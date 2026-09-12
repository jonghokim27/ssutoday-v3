package kr.ac.ssu.ssutoday.adapter.attestation

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.io.TempDir
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayIntegrityAccessTokenProviderTest {
    @TempDir
    lateinit var directory: Path

    private val mapper = JsonMapper.builder().build()

    @Test
    fun `키가 없는 환경에서는 OAuth를 호출하지 않는다`() {
        assertNull(PlayIntegrityAccessTokenProvider("").getAccessToken())
        assertNull(PlayIntegrityAccessTokenProvider(directory.resolve("missing.json").toString()).getAccessToken())
    }

    @Test
    fun `서비스 계정 이외의 credential은 허용하지 않는다`() {
        val path = directory.resolve("invalid.json")
        Files.writeString(path, """{"type":"authorized_user","refresh_token":"synthetic"}""")
        assertFailsWith<IOException> { PlayIntegrityAccessTokenProvider(path.toString()).getAccessToken() }
    }

    @Test
    fun `서비스 계정으로 playintegrity scope를 요청하고 OAuth 토큰을 재사용한다`() {
        // 실제 계정/키와 Google 서버를 사용하지 않는다. 테스트마다 키쌍과 로컬 OAuth 서버를 만든다.
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val privateKey =
            "-----BEGIN PRIVATE KEY-----\n" +
                Base64.getMimeEncoder(64, byteArrayOf(10)).encodeToString(keys.private.encoded) +
                "\n-----END PRIVATE KEY-----\n"
        val requests = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/token") { exchange ->
            requests += exchange.requestBody.use { it.readBytes().toString(Charsets.UTF_8) }
            val response = """{"access_token":"synthetic-access-token","token_type":"Bearer","expires_in":3600}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val tokenUri = "http://127.0.0.1:${server.address.port}/token"
            val path = directory.resolve("synthetic-service-account.json")
            Files.writeString(
                path,
                mapper.writeValueAsString(
                    mapOf(
                        "type" to "service_account",
                        "project_id" to "test-project",
                        "private_key_id" to "synthetic-key-id",
                        "private_key" to privateKey,
                        "client_email" to "test@test-project.iam.gserviceaccount.com",
                        "client_id" to "123",
                        "token_uri" to tokenUri,
                    ),
                ),
            )
            val provider = PlayIntegrityAccessTokenProvider(path.toString())
            repeat(3) { assertEquals("synthetic-access-token", provider.getAccessToken()) }
            assertEquals(1, requests.size)
            val fields =
                requests.single().split('&').associate {
                    val parts = it.split('=', limit = 2)
                    parts[0] to URLDecoder.decode(parts[1], Charsets.UTF_8)
                }
            assertEquals("urn:ietf:params:oauth:grant-type:jwt-bearer", fields["grant_type"])
            val jwt = requireNotNull(fields["assertion"]).split('.')
            val claims = mapper.readTree(Base64.getUrlDecoder().decode(jwt[1]))
            assertEquals("https://www.googleapis.com/auth/playintegrity", claims.path("scope").asString())
            assertEquals("https://oauth2.googleapis.com/token", claims.path("aud").asString())
            val signature = Signature.getInstance("SHA256withRSA")
            signature.initVerify(keys.public)
            signature.update("${jwt[0]}.${jwt[1]}".toByteArray(Charsets.US_ASCII))
            assertTrue(signature.verify(Base64.getUrlDecoder().decode(jwt[2])))
        } finally {
            server.stop(0)
        }
    }
}
