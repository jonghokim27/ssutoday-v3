package kr.ac.ssu.ssutoday.adapter.attestation

import io.github.oshai.kotlinlogging.KotlinLogging
import kr.ac.ssu.ssutoday.core.attestation.AppAttestAssertionVerification
import kr.ac.ssu.ssutoday.core.attestation.AppAttestRegistrationVerification
import kr.ac.ssu.ssutoday.core.attestation.AttestationClientData
import kr.ac.ssu.ssutoday.core.attestation.AttestationVerdict
import kr.ac.ssu.ssutoday.core.port.AppAttestVerificationPort
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.ASN1TaggedObject
import tools.jackson.core.StreamReadConstraints
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.dataformat.cbor.CBORFactory
import tools.jackson.dataformat.cbor.CBORMapper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.time.Clock
import java.util.Base64
import java.util.Date
import java.util.HexFormat

/** Apple 고정 Root CA만 신뢰한다. 네트워크나 클라이언트가 지정한 신뢰 앵커를 사용하지 않는다. */
class AppAttestVerificationAdapter(
    appId: String,
    private val production: Boolean,
    private val root: X509Certificate = appleRoot(),
    private val clock: Clock = Clock.systemUTC(),
) : AppAttestVerificationPort {
    private val appIdHash = AttestationClientData.hash(appId.toByteArray(Charsets.UTF_8))
    private val log = KotlinLogging.logger {}

    private val mapper =
        CBORMapper
            .builder(
                CBORFactory
                    .builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .streamReadConstraints(
                        StreamReadConstraints
                            .builder()
                            .maxNestingDepth(8)
                            .maxStringLength(65536)
                            .build(),
                    ).build(),
            ).disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build()

    override fun verifyRegistration(
        attestation: String,
        keyId: String,
        clientDataHash: ByteArray,
    ): AppAttestRegistrationVerification =
        try {
            checkInput(clientDataHash.size == 32 && AttestationClientData.isValidKeyId(keyId))
            val decoded = decodeObject(attestation, 65536)
            checkInput(decoded["fmt"]?.stringValue() == "apple-appattest")
            val statement = decoded["attStmt"] ?: reject(AttestationVerdict.INVALID_INPUT)
            val chain = statement["x5c"] ?: reject(AttestationVerdict.INVALID_INPUT)
            checkInput(chain.isArray && chain.size() in 2..3)
            val certificates = (0 until chain.size()).map { parseCertificate(binary(chain[it])) }
            validateChain(certificates)
            val leaf = certificates.first()
            val authData = binary(decoded["authData"])
            val nonce = AttestationClientData.hash(authData + clientDataHash)
            if (!MessageDigest.isEqual(certificateNonce(leaf), nonce)) reject(AttestationVerdict.NONCE_MISMATCH)
            val publicKey = leaf.publicKey as? ECPublicKey ?: reject(AttestationVerdict.KEY_ID_MISMATCH)
            val point = publicPoint(publicKey)
            val keyHash = Base64.getDecoder().decode(keyId)
            if (!MessageDigest.isEqual(AttestationClientData.hash(point), keyHash)) reject(AttestationVerdict.KEY_ID_MISMATCH)
            verifyRegistrationData(authData, keyHash, point)
            AppAttestRegistrationVerification(AttestationVerdict.VERIFIED, publicKey.encoded)
        } catch (failure: VerificationFailure) {
            AppAttestRegistrationVerification(failure.verdict)
        } catch (_: Exception) {
            AppAttestRegistrationVerification(AttestationVerdict.INVALID_INPUT)
        }

    override fun verifyAssertion(
        assertion: String,
        publicKey: ByteArray,
        clientDataHash: ByteArray,
    ): AppAttestAssertionVerification =
        try {
            checkInput(clientDataHash.size == 32)
            val decoded = decodeObject(assertion, 32768)
            val authData = binary(decoded["authenticatorData"])
            val signature = binary(decoded["signature"])
            checkInput(signature.size in 8..80)
            val counter = verifyAssertionData(authData)
            val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKey)) as ECPublicKey
            publicPoint(key) // P-256만 허용한다.
            // Apple은 nonce = SHA256(authenticatorData || clientDataHash)를 만든 뒤 그 nonce를 메시지로 서명한다.
            // SHA256withECDSA가 nonce를 한 번 더 해싱하므로 nonce 자체를 넣어야 한다.
            val nonce = MessageDigest.getInstance("SHA-256").digest(authData + clientDataHash)
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(key)
            verifier.update(nonce)
            if (!verifier.verify(signature)) {
                // 진단용. 서명 입력 네 가지를 그대로 남겨 오프라인에서 어느 쪽이 어긋나는지 대조한다.
                // 공개키는 공개 자료이고 assertion은 소모된 challenge에 묶여 있다. 원인 확인 후 제거한다.
                val hex = HexFormat.of()
                log.warn {
                    "assertion signature mismatch:" +
                        " authData=" + hex.formatHex(authData) +
                        " clientDataHash=" + hex.formatHex(clientDataHash) +
                        " signature=" + Base64.getEncoder().encodeToString(signature) +
                        " publicKey=" + hex.formatHex(publicKey)
                }
                reject(AttestationVerdict.SIGNATURE_INVALID)
            }
            AppAttestAssertionVerification(AttestationVerdict.VERIFIED, counter)
        } catch (failure: VerificationFailure) {
            // 진단용. checkInput은 어느 줄에서 걸렸는지가 유일한 단서라 스택트레이스를 남긴다.
            log.warn(failure) { "App Attest assertion rejected: ${failure.verdict}" }
            AppAttestAssertionVerification(failure.verdict)
        } catch (error: Exception) {
            log.warn(error) { "App Attest assertion failed to decode" }
            AppAttestAssertionVerification(AttestationVerdict.INVALID_INPUT)
        }

    private fun validateChain(certificates: List<X509Certificate>) {
        try {
            val now = Date.from(clock.instant())
            root.checkValidity(now)
            certificates.forEach { it.checkValidity(now) }
            check(certificates.first().basicConstraints == -1)
            check(certificates.first().keyUsage?.get(0) != false)
            val path = CertificateFactory.getInstance("X.509").generateCertPath(certificates)
            val params =
                PKIXParameters(setOf(TrustAnchor(root, null))).apply {
                    isRevocationEnabled = false
                    date = now
                }
            CertPathValidator.getInstance("PKIX").validate(path, params)
        } catch (_: Exception) {
            reject(AttestationVerdict.CERTIFICATE_UNTRUSTED)
        }
    }

    private fun certificateNonce(leaf: X509Certificate): ByteArray {
        val outer = ASN1OctetString.getInstance(leaf.getExtensionValue("1.2.840.113635.100.8.2"))
        val sequence = ASN1Sequence.getInstance(ASN1Primitive.fromByteArray(outer.octets))
        checkInput(sequence.size() == 1)
        val tagged = ASN1TaggedObject.getInstance(sequence.getObjectAt(0))
        checkInput(tagged.tagNo == 1 && tagged.isExplicit)
        return ASN1OctetString.getInstance(tagged.explicitBaseObject).octets.also { checkInput(it.size == 32) }
    }

    private fun verifyRegistrationData(
        data: ByteArray,
        keyHash: ByteArray,
        point: ByteArray,
    ) {
        val counter = verifyHeader(data, true)
        if (counter != 0L) reject(AttestationVerdict.COUNTER_REJECTED)
        checkInput(data.size >= 55)
        val expectedAaguid = if (production) "appattest".toByteArray() + ByteArray(7) else "appattestdevelop".toByteArray()
        if (!data.copyOfRange(37, 53).contentEquals(expectedAaguid)) reject(AttestationVerdict.ENVIRONMENT_MISMATCH)
        val length = ByteBuffer.wrap(data, 53, 2).short.toInt() and 0xffff
        if (length != 32 || data.size < 87 || !MessageDigest.isEqual(data.copyOfRange(55, 87), keyHash)) {
            reject(AttestationVerdict.KEY_ID_MISMATCH)
        }
        // COSE와 인증서가 같은 키를 표현하는지도 확인하고 남은 bytes는 확장 map으로만 허용한다.
        mapper.tokenStreamFactory().createParser(data.copyOfRange(87, data.size)).use { parser ->
            val cose = mapper.readTree(parser)
            checkInput(cose.isObject && cose["1"]?.intValue() == 2 && cose["3"]?.intValue() == -7 && cose["-1"]?.intValue() == 1)
            checkInput(binary(cose["-2"]).contentEquals(point.copyOfRange(1, 33)))
            checkInput(binary(cose["-3"]).contentEquals(point.copyOfRange(33, 65)))
            if (parser.nextToken() != null) {
                verifyExtensions(mapper.readTree(parser))
                checkInput(parser.nextToken() == null)
            } else {
                checkInput(data[32].toInt() and 0x80 == 0)
            }
        }
    }

    private fun verifyAssertionData(data: ByteArray): Long {
        // 진단용. 실기기가 실제로 보내는 flags를 확인한 뒤 제거한다.
        log.warn { "assertion authData: size=${data.size} flags=0x${"%02x".format(data[32].toInt() and 0xff)}" }
        val counter = verifyHeader(data, false)
        if (counter == 0L) reject(AttestationVerdict.COUNTER_REJECTED)
        if (data.size > 37) {
            verifyExtensions(readObject(data.copyOfRange(37, data.size)))
        } else {
            checkInput(data[32].toInt() and 0x80 == 0)
        }
        return counter
    }

    private fun verifyHeader(
        data: ByteArray,
        registration: Boolean,
    ): Long {
        checkInput(data.size in 37..4096)
        if (!MessageDigest.isEqual(data.copyOfRange(0, 32), appIdHash)) reject(AttestationVerdict.APP_ID_MISMATCH)
        val flags = data[32].toInt() and 0xff
        // Apple은 App Attest authenticatorData의 flags를 문서로 보장하지 않는다. iPadOS 26은 assertion에도
        // AT 비트를 세팅한다. AT는 attested credential data를 파싱하는 registration에서만 의미가 있으므로
        // 거기서만 강제한다. assertion의 뒤따르는 바이트는 아래 크기 기준으로 검사한다.
        if (registration) checkInput(flags and 0x40 != 0)
        return ByteBuffer.wrap(data, 33, 4).int.toLong() and 0xffffffffL
    }

    private fun verifyExtensions(extensions: JsonNode) {
        checkInput(extensions.isObject)
        val category = binary(extensions["apple_validation_category_01"])
        checkInput(category.size == 4)
        val value = ByteBuffer.wrap(category).order(ByteOrder.LITTLE_ENDIAN).int
        if (value !in if (production) setOf(2, 4) else setOf(3)) reject(AttestationVerdict.APP_UNRECOGNIZED)
        val version = extensions["apple_bundle_version_01"]
        checkInput(version?.isString == true && Regex("[0-9]{1,10}(?:\\.[0-9]{1,10}){0,2}").matches(version.stringValue()))
    }

    private fun publicPoint(key: ECPublicKey): ByteArray {
        val expected =
            AlgorithmParameters
                .getInstance("EC")
                .apply {
                    init(ECGenParameterSpec("secp256r1"))
                }.getParameterSpec(ECParameterSpec::class.java)
        checkInput(
            key.params.curve == expected.curve &&
                key.params.generator == expected.generator &&
                key.params.order == expected.order &&
                key.params.cofactor == expected.cofactor,
        )

        fun coordinate(value: java.math.BigInteger): ByteArray {
            val bytes = value.toByteArray()
            checkInput(value.signum() >= 0 && bytes.size <= 33)
            return ByteArray(32).also { output ->
                val significant = if (bytes.size == 33) bytes.copyOfRange(1, 33) else bytes
                significant.copyInto(output, 32 - significant.size)
            }
        }
        return byteArrayOf(4) + coordinate(key.w.affineX) + coordinate(key.w.affineY)
    }

    private fun decodeObject(
        value: String,
        limit: Int,
    ): JsonNode {
        checkInput(value.isNotEmpty() && value.length <= limit)
        val bytes = Base64.getDecoder().decode(value)
        checkInput(Base64.getEncoder().encodeToString(bytes) == value)
        return readObject(bytes)
    }

    private fun readObject(bytes: ByteArray): JsonNode =
        mapper.tokenStreamFactory().createParser(bytes).use { parser ->
            val node = mapper.readTree(parser)
            checkInput(node.isObject && parser.nextToken() == null)
            node
        }

    private fun binary(node: JsonNode?): ByteArray {
        checkInput(node?.isBinary == true)
        return requireNotNull(node).binaryValue()
    }

    private fun parseCertificate(bytes: ByteArray): X509Certificate {
        checkInput(bytes.size in 100..8192)
        val input = bytes.inputStream()
        val certificate = CertificateFactory.getInstance("X.509").generateCertificate(input) as X509Certificate
        checkInput(input.available() == 0)
        return certificate
    }

    private fun checkInput(valid: Boolean) {
        if (!valid) reject(AttestationVerdict.INVALID_INPUT)
    }

    private fun reject(verdict: AttestationVerdict): Nothing = throw VerificationFailure(verdict)

    private class VerificationFailure(
        val verdict: AttestationVerdict,
    ) : RuntimeException()

    companion object {
        fun appleRoot(): X509Certificate =
            AppAttestVerificationAdapter::class.java.getResourceAsStream("/attestation/Apple_App_Attestation_Root_CA.pem").use {
                CertificateFactory.getInstance("X.509").generateCertificate(requireNotNull(it)) as X509Certificate
            }
    }
}
