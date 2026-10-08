package com.wafflestudio.spring2026.auth

import com.wafflestudio.spring2026.waggle.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * 가입하지 않은 이메일과 비밀번호 불일치를 구분하지 않는다.
 * 어느 쪽인지 알려 주면 어떤 이메일이 가입돼 있는지 확인하는 수단이 된다.
 */
class InvalidCredentialsException : RuntimeException("이메일 또는 비밀번호가 올바르지 않습니다.")

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val tokenBlacklistService: TokenBlacklistService,
) {
    fun login(email: String, password: String, now: Instant): String {
        val user = userRepository.findByEmail(email) ?: throw InvalidCredentialsException()
        if (!passwordEncoder.matches(password, user.password)) throw InvalidCredentialsException()

        return jwtService.issue(userId = user.id!!, now = now)
    }

    /**
     * 요청에 쓴 그 토큰 하나만 무효화한다.
     * 같은 사용자가 다른 기기에서 받은 토큰은 그대로 살아 있어야 한다.
     */
    fun logout(principal: AuthPrincipal) {
        tokenBlacklistService.blacklist(jti = principal.jti, expiresAt = principal.expiresAt)
    }
}
