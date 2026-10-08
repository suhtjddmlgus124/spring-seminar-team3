package com.wafflestudio.spring2026.waggle.controller

import java.net.URI
import java.time.Instant
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import jakarta.validation.Valid
import com.wafflestudio.spring2026.waggle.service.UserService
import com.wafflestudio.spring2026.waggle.dto.user.UserResponse
import com.wafflestudio.spring2026.waggle.dto.user.SignupRequest
import com.wafflestudio.spring2026.waggle.dto.user.UserApprovalRequest
import com.wafflestudio.spring2026.auth.AuthPrincipal
import com.wafflestudio.spring2026.auth.AuthService
import com.wafflestudio.spring2026.auth.dto.LoginRequest
import com.wafflestudio.spring2026.auth.dto.LoginResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal

@RequestMapping("/users")
@RestController
class UserController(
    private val userService: UserService
) {
    
    /** 본인 정보는 가입 승인 전에도 볼 수 있다. 경로 상수가 먼저 와야 /users/{id} 에 먹히지 않는다. */
    @GetMapping("/me")
    fun getMe(@AuthenticationPrincipal principal: AuthPrincipal): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(UserResponse.from(principal.user))
    }

    @GetMapping("/{id}")
    fun getUser(@PathVariable("id") id: Long): ResponseEntity<UserResponse> {
        val user = userService.getUser(id)
        val response = UserResponse.from(user)
        return ResponseEntity.ok(response)
    }
    
    @PatchMapping("/{id}/approval")
    fun approvalUser(
        @PathVariable("id") id: Long, 
        @Valid @RequestBody request: UserApprovalRequest
    ): ResponseEntity<UserResponse> {
        val user = userService.getUser(id)
        val updatedUser = userService.approvalUser(user, request.status)
        
        val response = UserResponse.from(updatedUser)
        return ResponseEntity.ok(response)
    }

}

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