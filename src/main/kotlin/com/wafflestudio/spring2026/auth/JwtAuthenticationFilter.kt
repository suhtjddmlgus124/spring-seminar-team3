package com.wafflestudio.spring2026.auth

import com.wafflestudio.spring2026.waggle.repository.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 요청마다 Authorization 헤더의 Access Token 을 확인해 요청자를 세운다.
 *
 * 토큰이 없거나 잘못됐으면 여기서 예외를 던지지 않고 그냥 인증하지 않은 채 흘려보낸다.
 * 보호된 경로였다면 뒤의 인가 단계가 막고 JwtAuthenticationEntryPoint 가 401 을 만든다.
 * 공개 경로였다면 잘못된 토큰이 있어도 그대로 통과한다.
 */
@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val tokenBlacklistService: TokenBlacklistService,
    private val userRepository: UserRepository,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        resolvePrincipal(request)?.let { principal ->
            val authentication = UsernamePasswordAuthenticationToken(principal, null, emptyList())
            SecurityContextHolder.getContext().authentication = authentication
        }
        filterChain.doFilter(request, response)
    }

    private fun resolvePrincipal(request: HttpServletRequest): AuthPrincipal? {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        if (!header.startsWith(BEARER_PREFIX)) return null

        val token = header.removePrefix(BEARER_PREFIX).trim()
        if (token.isEmpty()) return null

        // 서명과 만료를 먼저 본다. 둘 중 하나라도 어긋나면 DB 를 건드리지 않는다.
        val claims = jwtService.verify(token) ?: return null

        val jti = claims.id ?: return null
        if (tokenBlacklistService.isBlacklisted(jti)) return null

        val userId = claims.subject?.toLongOrNull() ?: return null
        val user = userRepository.findByIdOrNull(userId) ?: return null
        val expiresAt = claims.expiration?.toInstant() ?: return null

        return AuthPrincipal(user = user, jti = jti, expiresAt = expiresAt)
    }

    companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}
