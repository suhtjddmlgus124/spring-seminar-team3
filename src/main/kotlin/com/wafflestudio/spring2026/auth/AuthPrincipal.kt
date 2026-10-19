package com.wafflestudio.spring2026.auth

import com.wafflestudio.spring2026.waggle.model.User
import java.time.Instant

/**
 * 인증을 통과한 요청자. 인가 규칙은 이 값만 보고 판단한다.
 *
 * jti 와 expiresAt 은 로그아웃이 토큰을 무효화할 때 쓴다.
 */
data class AuthPrincipal(
    val user: User,
    val jti: String,
    val expiresAt: Instant,
) {
    val userId: Long get() = user.id!!
}
