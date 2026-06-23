package kr.ac.ssu.ssutoday.adapter.security

import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import kr.ac.ssu.ssutoday.core.exception.BusinessException
import kr.ac.ssu.ssutoday.core.status.StatusCode
import kr.ac.ssu.ssutoday.core.exception.TokenExpiredException
import kr.ac.ssu.ssutoday.core.dto.TokenPayload
import kr.ac.ssu.ssutoday.core.port.TokenPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.security.MessageDigest
import java.util.Date

@Component
class JwtTokenAdapter(
    @Value("\${spring.jwt.secret}") private val secret: String,
) : TokenPort {
    override fun createAccessToken(payload: TokenPayload): String =
        Jwts.builder()
            .claim("studentId", payload.studentId)
            .claim("name", payload.name)
            .claim("major", payload.major)
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + 2 * 60 * 60 * 1000))
            .signWith(SignatureAlgorithm.HS256, secret)
            .compact()

    override fun validateAccessToken(token: String): TokenPayload {
        try {
            val claims = Jwts.parser().setSigningKey(secret).parseClaimsJws(token).body
            return TokenPayload(
                (claims["studentId"] as Number).toInt(),
                claims["name"] as String,
                claims["major"] as String,
            )
        } catch (exception: ExpiredJwtException) {
            throw TokenExpiredException(exception)
        } catch (exception: Exception) {
            throw BusinessException(StatusCode.SSU4001, cause = exception)
        }
    }

    override fun randomToken(length: Int): String {
        require(length in 1..SHA_512_HEX_LENGTH)
        val entropy = ByteArray(64).also(secureRandom::nextBytes)
        val timestamp = System.currentTimeMillis().toString().toByteArray()
        val digest = MessageDigest.getInstance("SHA-512").run {
            update(entropy)
            digest(timestamp)
        }
        return digest.joinToString("") { "%02x".format(it) }.take(length)
    }

    private companion object {
        const val SHA_512_HEX_LENGTH = 128
        val secureRandom = SecureRandom()
    }
}
