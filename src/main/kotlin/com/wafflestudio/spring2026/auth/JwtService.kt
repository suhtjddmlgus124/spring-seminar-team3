package com.wafflestudio.spring2026.auth

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Access Token 의 발급과 검증만 담당한다.
 *
 * 과제 명세가 요구하는 클레임은 셋이다.
 *   sub : 사용자 ID 문자열
 *   iat : 발급 시각(초)
 *   exp : iat + 3600
 *
 * 여기에 jti(UUID) 를 더 넣는다. 로그아웃은 토큰 원문이 아니라 이 jti 를 저장해
 * 무효화하므로, 토큰이 길어져도 블랙리스트 테이블은 36자로 고정된다.
 */
@Service
class JwtService(
    @Value("\${jwt.secret}") secret: String,
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8))

    fun issue(userId: Long, now: Instant): String {
        val expiresAt = now.plusSeconds(TOKEN_TTL_SECONDS)
        return Jwts.builder()
            .subject(userId.toString())
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(key, Jwts.SIG.HS256)
            .compact()
    }

    /**
     * 서명과 만료를 검증한다.
     * 서명 위조, 형식 오류, 만료는 모두 같은 실패로 다룬다. 호출하는 쪽에서 401 로 바꾼다.
     */
    fun verify(token: String): Claims? =
        try {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (e: JwtException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }

    companion object {
        const val TOKEN_TTL_SECONDS = 3600L
    }
}
