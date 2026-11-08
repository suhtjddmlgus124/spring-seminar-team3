package com.wafflestudio.spring2026.auth.controller

import com.wafflestudio.spring2026.auth.AuthPrincipal
import com.wafflestudio.spring2026.auth.dto.LoginRequest
import com.wafflestudio.spring2026.auth.dto.LoginResponse
import com.wafflestudio.spring2026.auth.dto.SignupRequest
import com.wafflestudio.spring2026.auth.dto.UserResponse
import com.wafflestudio.spring2026.auth.service.AuthService
import com.wafflestudio.spring2026.auth.service.UserService
import jakarta.validation.Valid
import java.net.URI
import java.time.Instant
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@RequestMapping("/auth")
@RestController
class AuthController(
    private val userService: UserService,
    private val authService: AuthService,
) {
    
    @PostMapping("/signup")
    fun signup(@Valid @RequestBody request: SignupRequest): ResponseEntity<UserResponse> {
        val user = userService.createUser(
            email = request.email,
            password = request.password,
            name = request.name,
            githubUsername = request.githubUsername,
            role = request.role,
            seminarId = request.seminarId,
            now = Instant.now(),
        )
        
        val response = UserResponse.from(user)
        return ResponseEntity
            .created(URI.create("/users/${user.id}"))
            .body(response)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        val accessToken = authService.login(
            email = request.email!!,
            password = request.password!!,
            now = Instant.now(),
        )
        return ResponseEntity.ok(LoginResponse(accessToken))
    }

    /** 요청에 담긴 그 토큰만 무효화한다. 토큰이 유효하지 않으면 여기까지 오지 못하고 401 이다. */
    @PostMapping("/logout")
    fun logout(@AuthenticationPrincipal principal: AuthPrincipal): ResponseEntity<Unit> {
        authService.logout(principal)
        return ResponseEntity.noContent().build()
    }

}
