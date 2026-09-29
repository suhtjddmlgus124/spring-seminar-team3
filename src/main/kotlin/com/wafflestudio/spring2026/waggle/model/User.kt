package com.wafflestudio.spring2026.waggle.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

enum class UserRole { ROOKIE, STAFF, ADMIN }

enum class UserStatus { PENDING, APPROVED, REJECTED }

@Table("users")
data class User(
    @Id
    val id: Long? = null,

    @Column("email")
    val email: String,

    @Column("password")
    val password: String,

    @Column("name")
    val name: String,

    @Column("githubUsername")
    val githubUsername: String,

    @Column("role")
    val role: UserRole,

    @Column("status")
    val status: UserStatus,

    @Column("createdAt")
    val createdAt: LocalDateTime,

    @Column("seminarId")
    val seminarId: Long?,
)