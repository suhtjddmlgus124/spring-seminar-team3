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
import org.springframework.web.bind.annotation.RequestParam
import com.wafflestudio.spring2026.auth.AccessPolicy
import com.wafflestudio.spring2026.waggle.dto.PageQuery
import com.wafflestudio.spring2026.waggle.dto.PageResponse
import com.wafflestudio.spring2026.waggle.model.UserRole
import com.wafflestudio.spring2026.waggle.model.UserStatus

@RequestMapping("/users")
@RestController
class UserController(
    private val userService: UserService,
    private val accessPolicy: AccessPolicy,
) {

    /** 가입 심사 화면의 신청 목록. 와장과 운영진만 쓴다. */
    @GetMapping
    fun getApplicants(
        @RequestParam("status", required = false) status: UserStatus?,
        @RequestParam("role", required = false) role: UserRole?,
        @RequestParam("page", required = false) page: Int?,
        @RequestParam("size", required = false) size: Int?,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<PageResponse<UserResponse>> {
        accessPolicy.requireUserListViewer(principal)
        val users = userService.getApplicants(principal.user, status, role, PageQuery.of(page, size))
        return ResponseEntity.ok(users.map { UserResponse.from(it) })
    }
    
    /** 본인 정보는 가입 승인 전에도 볼 수 있다. 경로 상수가 먼저 와야 /users/{id} 에 먹히지 않는다. */
    @GetMapping("/me")
    fun getMe(@AuthenticationPrincipal principal: AuthPrincipal): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(UserResponse.from(principal.user))
    }

    /** 승인 전 사용자는 자기 자신이라도 여기로는 조회할 수 없고 /users/me 를 쓴다. */
    @GetMapping("/{id}")
    fun getUser(
        @PathVariable("id") id: Long,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<UserResponse> {
        accessPolicy.requireApproved(principal)
        val user = userService.getUser(id)
        val response = UserResponse.from(user)
        return ResponseEntity.ok(response)
    }
    
    @PatchMapping("/{id}/approval")
    fun approvalUser(
        @PathVariable("id") id: Long, 
        @Valid @RequestBody request: UserApprovalRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<UserResponse> {
        val user = userService.getUser(id)
        // 심사 대상의 역할을 알아야 판단할 수 있어서 대상을 먼저 찾는다.
        accessPolicy.requireApprover(principal, user)
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