package com.wafflestudio.spring2026.waggle.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("enrollments")
data class Enrollment(
    @Id
    val id: Long? = null,

    @Column("graceDaysRemaining")
    val graceDaysRemaining: Int,

    @Column("isFailed")
    val isFailed: Boolean = false,

    @Column("createdAt")
    val createdAt: LocalDateTime,

    @Column("rookieId")
    val rookieId: Long,

    @Column("seminarId")
    val seminarId: Long,
)
