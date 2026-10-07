package com.wafflestudio.spring2026.waggle.dto.user

import com.wafflestudio.spring2026.waggle.model.UserRole
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import jakarta.validation.constraints.AssertTrue

data class SignupRequest(
    @field:NotBlank(message = "비어있을 수 없습니다.")
    @field:Email(message = "이메일 형식이어야 합니다.")
    @field:Size(max = 255, message = "255자를 넘을 수 없습니다.")
    val email: String,
    
    @field:NotBlank(message = "비어있을 수 없습니다.")
    val password: String,
    
    @field:NotBlank(message = "비어있을 수 없습니다.")
    @field:Size(max = 255, message = "255자를 넘을 수 없습니다.")
    val name: String,
    
    @field:NotBlank(message = "비어있을 수 없습니다.")
    @field:Size(max = 255, message = "255자를 넘을 수 없습니다.")
    val githubUsername: String,
    
    val role: UserRole,
    
    val seminarId: Long? = null,
) {
    @get:AssertTrue(message = "새로운 사용자는 ADMIN일 수 없습니다.")
    val isRole: Boolean
        get() = (role != UserRole.ADMIN)
    
    @get:AssertTrue(message = "ROOKIE는 가질 수 없고, STAFF는 반드시 가져야 합니다.")
    val isSeminarId: Boolean
        get() = when(role) {
            UserRole.STAFF -> seminarId != null
            UserRole.ROOKIE -> seminarId == null
            else -> true
        }
}
