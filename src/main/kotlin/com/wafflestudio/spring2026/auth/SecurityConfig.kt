package com.wafflestudio.spring2026.auth

import com.wafflestudio.spring2026.ApiErrorResponse
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import tools.jackson.databind.ObjectMapper

@Configuration
class SecurityConfig(
    private val objectMapper: ObjectMapper,
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtAuthenticationFilter: JwtAuthenticationFilter,
    ): SecurityFilterChain {
        http
            // 토큰으로만 인증하므로 세션도 CSRF 토큰도 쓰지 않는다.
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.POST, "/auth/signup", "/auth/login").permitAll()
                // 세미나 목록은 명세상 로그인 없이 볼 수 있다.
                it.requestMatchers(HttpMethod.GET, "/seminars").permitAll()
                it.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/error").permitAll()
                it.anyRequest().authenticated()
            }
            .exceptionHandling {
                // 인증 실패는 401, 인증은 됐는데 권한이 없으면 403.
                it.authenticationEntryPoint { _, response, _ ->
                    write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "인증에 실패했습니다.")
                }
                it.accessDeniedHandler { _, response, _ ->
                    write(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "이 기능을 사용할 권한이 없습니다.")
                }
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    private fun write(response: HttpServletResponse, status: Int, code: String, message: String) {
        response.status = status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(objectMapper.writeValueAsString(ApiErrorResponse(code = code, message = message)))
    }
}
