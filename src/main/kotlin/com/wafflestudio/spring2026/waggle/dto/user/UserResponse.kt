package com.wafflestudio.spring2026.waggle.dto.user

import java.time.OffsetDateTime
import java.time.ZoneOffset
import com.wafflestudio.spring2026.waggle.model.User
import com.wafflestudio.spring2026.waggle.model.UserRole
import com.wafflestudio.spring2026.waggle.model.UserStatus

data class UserResponse(
    val id: Long,
    val email: String,
    val name: String,
    val githubUsername: String,
    val role: UserRole,
    val status: UserStatus,
    val seminarId: Long?,
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun from(user: User): UserResponse
            = UserResponse(
                id = user.id!!,
                email = user.email,
                name = user.name,
                githubUsername = user.githubUsername,
                role = user.role,
                status = user.status,
                seminarId = user.seminarId,
                createdAt = user.createdAt.atOffset(ZoneOffset.ofHours(9)),
            )
    }
}
