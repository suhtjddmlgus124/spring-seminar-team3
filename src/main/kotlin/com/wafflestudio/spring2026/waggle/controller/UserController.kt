package com.wafflestudio.spring2026.waggle.controller

import java.net.URI
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

@RequestMapping("/users")
@RestController
class UserController(
    private val userService: UserService
) {
    
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
    private val userService: UserService
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
        )
        
        val response = UserResponse.from(user)
        return ResponseEntity
            .created(URI.create("/users/${user.id}"))
            .body(response)
    }

}