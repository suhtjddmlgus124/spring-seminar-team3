package com.wafflestudio.spring2026.auth.dto

import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:NotBlank(message = "이메일은 비어 있을 수 없습니다.")
    val email: String?,

    @field:NotBlank(message = "비밀번호는 비어 있을 수 없습니다.")
    val password: String?,
)