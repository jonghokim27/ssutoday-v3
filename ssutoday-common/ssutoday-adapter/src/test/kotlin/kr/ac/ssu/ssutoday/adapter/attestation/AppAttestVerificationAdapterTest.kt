package kr.ac.ssu.ssutoday.adapter.attestation

import kr.ac.ssu.ssutoday.core.attestation.AttestationClientData
import kr.ac.ssu.ssutoday.core.attestation.AttestationVerdict
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.DERTaggedObject
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import tools.jackson.dataformat.cbor.CBORMapper
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AppAttestVerificationAdapterTest {
    private val mapper = CBORMapper.builder().build()
    private val now = Instant.parse("2026-09-13T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val rootKey = keyPair()
    private val root = certificate("CN=Test Root", "CN=Test Root", rootKey, rootKey, true)
    private val intermediateKey = keyPair()
    private val intermediate = certificate("CN=Test Intermediate", "CN=Test Root", intermediateKey, rootKey, true)
    private val key = keyPair()
    private val point = point(key)
    private val keyId = Base64.getEncoder().encodeToString(AttestationClientData.hash(point))
    private val clientHash = AttestationClientData.hash("server constructed client data".toByteArray())
    private val adapter = AppAttestVerificationAdapter(APP_ID, true, root, clock)

    @Test
    fun `P256 인증서 체인 nonce 키와 COSE를 검증하고 인증서의 공개키를 반환한다`() {
        val result = adapter.verifyRegistration(registration(), keyId, clientHash)
        assertEquals(AttestationVerdict.VERIFIED, result.verdict)
        assertContentEquals(key.public.encoded, result.publicKey)
        val withExtensions = registration(authData = registrationData() + extensions())
        assertEquals(AttestationVerdict.VERIFIED, adapter.verifyRegistration(withExtensions, keyId, clientHash).verdict)
    }

    @Test
    fun `다른 루트의 인증서와 만료된 인증서는 거부한다`() {
        val untrusted = AppAttestVerificationAdapter(APP_ID, true, AppAttestVerificationAdapter.appleRoot(), clock)
        assertEquals(AttestationVerdict.CERTIFICATE_UNTRUSTED, untrusted.verifyRegistration(registration(), keyId, clientHash).verdict)
        val expired = AppAttestVerificationAdapter(APP_ID, true, root, Clock.offset(clock, java.time.Duration.ofDays(3)))
        assertEquals(AttestationVerdict.CERTIFICATE_UNTRUSTED, expired.verifyRegistration(registration(), keyId, clientHash).verdict)
    }

    @Test
    fun `challenge 변경과 공개키 식별자 바꿔치기는 거부한다`() {
        assertEquals(AttestationVerdict.NONCE_MISMATCH, adapter.verifyRegistration(registration(), keyId, ByteArray(32)).verdict)
        assertEquals(
            AttestationVerdict.KEY_ID_MISMATCH,
            adapter.verifyRegistration(registration(), Base64.getEncoder().encodeToString(ByteArray(32)), clientHash).verdict,
        )
        val credentialChanged = registrationData().also { it[55] = (it[55].toInt() xor 1).toByte() }
        assertEquals(
            AttestationVerdict.KEY_ID_MISMATCH,
            adapter.verifyRegistration(registration(credentialChanged), keyId, clientHash).verdict,
        )
        val coseChanged = registrationData().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertEquals(AttestationVerdict.INVALID_INPUT, adapter.verifyRegistration(registration(coseChanged), keyId, clientHash).verdict)
    }

    @Test
    fun `App ID 환경 등록 counter와 서명된 확장 정책을 검증한다`() {
        val wrongApp = AppAttestVerificationAdapter("OTHER00000.com.ssutoday", true, root, clock)
        assertEquals(AttestationVerdict.APP_ID_MISMATCH, wrongApp.verifyRegistration(registration(), keyId, clientHash).verdict)
        val development = AppAttestVerificationAdapter(APP_ID, false, root, clock)
        assertEquals(AttestationVerdict.ENVIRONMENT_MISMATCH, development.verifyRegistration(registration(), keyId, clientHash).verdict)
        val devData = registrationData().also { "appattestdevelop".toByteArray().copyInto(it, 37) }
        assertEquals(AttestationVerdict.VERIFIED, development.verifyRegistration(registration(devData), keyId, clientHash).verdict)
        val counter = registrationData().also { it[36] = 1 }
        assertEquals(AttestationVerdict.COUNTER_REJECTED, adapter.verifyRegistration(registration(counter), keyId, clientHash).verdict)
        assertEquals(
            AttestationVerdict.APP_UNRECOGNIZED,
            adapter.verifyRegistration(registration(registrationData() + extensions(3)), keyId, clientHash).verdict,
        )
        assertEquals(
            AttestationVerdict.INVALID_INPUT,
            adapter
                .verifyRegistration(
                    registration(
                        registrationData() + extensions(version = "invalid"),
                    ),
                    keyId,
                    clientHash,
                ).verdict,
        )
    }

    @Test
    fun `assertion은 SHA256 ECDSA를 한 번 적용하고 unsigned counter를 반환한다`() {
        val data = header(0xffffffffL)
        val result = adapter.verifyAssertion(assertion(data), key.public.encoded, clientHash)
        assertEquals(AttestationVerdict.VERIFIED, result.verdict)
        assertEquals(0xffffffffL, result.counter)
        assertEquals(
            AttestationVerdict.SIGNATURE_INVALID,
            adapter.verifyAssertion(assertion(data), key.public.encoded, ByteArray(32)).verdict,
        )
        assertEquals(
            AttestationVerdict.SIGNATURE_INVALID,
            adapter.verifyAssertion(assertion(data, signingKey = keyPair()), key.public.encoded, clientHash).verdict,
        )
        val doubleHash =
            Signature
                .getInstance("SHA256withECDSA")
                .apply {
                    initSign(key.private)
                    update(AttestationClientData.hash(data + clientHash))
                }.sign()
        val encoded = encode(mapOf("authenticatorData" to data, "signature" to doubleHash))
        assertEquals(AttestationVerdict.SIGNATURE_INVALID, adapter.verifyAssertion(encoded, key.public.encoded, clientHash).verdict)
    }

    @Test
    fun `assertion의 App ID counter flags와 확장도 서명 검증과 함께 강제한다`() {
        assertEquals(
            AttestationVerdict.COUNTER_REJECTED,
            adapter.verifyAssertion(assertion(header(0)), key.public.encoded, clientHash).verdict,
        )
        val wrongApp = header(1).also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertEquals(
            AttestationVerdict.APP_ID_MISMATCH,
            adapter.verifyAssertion(assertion(wrongApp), key.public.encoded, clientHash).verdict,
        )
        assertEquals(
            AttestationVerdict.INVALID_INPUT,
            adapter.verifyAssertion(assertion(header(1, 0x40)), key.public.encoded, clientHash).verdict,
        )
        assertEquals(
            AttestationVerdict.VERIFIED,
            adapter.verifyAssertion(assertion(header(1, 0x80) + extensions()), key.public.encoded, clientHash).verdict,
        )
        assertEquals(
            AttestationVerdict.APP_UNRECOGNIZED,
            adapter.verifyAssertion(assertion(header(1, 0x80) + extensions(0)), key.public.encoded, clientHash).verdict,
        )
    }

    @Test
    fun `잘못된 CBOR 중복 키 trailing data와 과도한 입력은 거부한다`() {
        listOf("not base64", "a".repeat(65537), encode(listOf(1, 2)), registration() + "\n").forEach {
            assertEquals(AttestationVerdict.INVALID_INPUT, adapter.verifyRegistration(it, keyId, clientHash).verdict)
        }
        val valid = Base64.getDecoder().decode(assertion(header(1)))
        assertEquals(
            AttestationVerdict.INVALID_INPUT,
            adapter.verifyAssertion(Base64.getEncoder().encodeToString(valid + byteArrayOf(0)), key.public.encoded, clientHash).verdict,
        )
        val duplicate = byteArrayOf(0xa2.toByte(), 0x61, 0x61, 1, 0x61, 0x61, 2)
        assertEquals(
            AttestationVerdict.INVALID_INPUT,
            adapter.verifyAssertion(Base64.getEncoder().encodeToString(duplicate), key.public.encoded, clientHash).verdict,
        )
    }

    @Test
    fun `Apple 2026 공식 예제의 인증서는 유효하지만 raw challenge nonce는 명세와 달라 거부한다`() {
        val sample = javaClass.getResource("/attestation/apple-guide-attestation.b64")!!.readText().trim()
        val atIssuance = Clock.fixed(Instant.parse("2026-04-21T19:00:00Z"), ZoneOffset.UTC)
        val apple = AppAttestVerificationAdapter("1234567890.com.example.myapp", true, clock = atIssuance)
        val result =
            apple.verifyRegistration(
                sample,
                "zgSY9YSD+7TaDXssY6WlOPVS1K3Lmk+pFhlcSWE+ZV0=",
                AttestationClientData.hash("example_server_challenge".toByteArray()),
            )
        assertEquals(AttestationVerdict.NONCE_MISMATCH, result.verdict)
        val decoded = mapper.readTree(Base64.getDecoder().decode(sample))
        val authData = decoded["authData"].binaryValue()
        assertEquals(
            "h7fQbZOkKU5G8BHma2zEAPC6sgcpl2xhlYC0KuYL/24=",
            Base64.getEncoder().encodeToString(
                AttestationClientData.hash(
                    authData + "example_server_challenge".toByteArray(),
                ),
            ),
        )
        assertNotNull(AppAttestVerificationAdapter.appleRoot())
    }

    private fun registration(authData: ByteArray = registrationData()): String {
        val leaf =
            certificate(
                "CN=Credential",
                "CN=Test Intermediate",
                key,
                intermediateKey,
                false,
                AttestationClientData.hash(
                    authData + clientHash,
                ),
            )
        return encode(
            mapOf(
                "fmt" to "apple-appattest",
                "attStmt" to mapOf("x5c" to listOf(leaf.encoded, intermediate.encoded), "receipt" to byteArrayOf(1)),
                "authData" to authData,
            ),
        )
    }

    private fun registrationData(): ByteArray =
        header(0, 0x40) + "appattest".toByteArray() + ByteArray(7) + byteArrayOf(0, 32) +
            Base64.getDecoder().decode(keyId) +
            mapper.writeValueAsBytes(mapOf(1 to 2, 3 to -7, -1 to 1, -2 to point.copyOfRange(1, 33), -3 to point.copyOfRange(33, 65)))

    private fun header(
        counter: Long,
        flags: Int = 0,
    ): ByteArray =
        AttestationClientData.hash(APP_ID.toByteArray()) + byteArrayOf(flags.toByte()) +
            ByteBuffer.allocate(4).putInt(counter.toInt()).array()

    private fun extensions(
        category: Int = 2,
        version: String = "1",
    ): ByteArray =
        mapper.writeValueAsBytes(
            mapOf(
                "apple_validation_category_01" to byteArrayOf(category.toByte(), 0, 0, 0),
                "apple_bundle_version_01" to version,
            ),
        )

    private fun assertion(
        data: ByteArray,
        signingKey: KeyPair = key,
    ): String {
        val signature =
            Signature
                .getInstance("SHA256withECDSA")
                .apply {
                    initSign(signingKey.private)
                    update(data)
                    update(clientHash)
                }.sign()
        return encode(mapOf("authenticatorData" to data, "signature" to signature))
    }

    private fun encode(value: Any): String = Base64.getEncoder().encodeToString(mapper.writeValueAsBytes(value))

    private fun keyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private fun point(pair: KeyPair): ByteArray {
        val public = pair.public as ECPublicKey

        fun coordinate(value: BigInteger) =
            value
                .toByteArray()
                .takeLast(32)
                .toByteArray()
                .let { ByteArray(32 - it.size) + it }
        return byteArrayOf(4) + coordinate(public.w.affineX) + coordinate(public.w.affineY)
    }

    private fun certificate(
        subject: String,
        issuer: String,
        subjectKey: KeyPair,
        issuerKey: KeyPair,
        ca: Boolean,
        nonce: ByteArray? = null,
    ): X509Certificate {
        val builder =
            JcaX509v3CertificateBuilder(
                X500Name(issuer),
                BigInteger.valueOf(System.nanoTime()).abs(),
                Date.from(now.minusSeconds(86400)),
                Date.from(now.plusSeconds(86400)),
                X500Name(subject),
                subjectKey.public,
            )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(ca))
        builder.addExtension(Extension.keyUsage, true, KeyUsage(if (ca) KeyUsage.keyCertSign else KeyUsage.digitalSignature))
        if (nonce !=
            null
        ) {
            builder.addExtension(
                ASN1ObjectIdentifier("1.2.840.113635.100.8.2"),
                false,
                DERSequence(DERTaggedObject(true, 1, DEROctetString(nonce))),
            )
        }
        val signer = JcaContentSignerBuilder("SHA256withECDSA").setProvider(BouncyCastleProvider()).build(issuerKey.private)
        return JcaX509CertificateConverter().setProvider(BouncyCastleProvider()).getCertificate(builder.build(signer))
    }

    private companion object {
        const val APP_ID = "F7TW6722Y9.com.ssutoday"
    }
}
