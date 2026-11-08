package com.wafflestudio.spring2026.auth.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import 

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