package com.wafflestudio.spring2026.waggle.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("seminars")
data class Seminar(
    @Id
    val id: Long? = null,

    @Column("title")
    val title: String,

    @Column("description")
    val description: String?,

    @Column("capacity")
    val capacity: Int,

    @Column("applyStartAt")
    val applyStartAt: LocalDateTime,

    @Column("applyEndAt")
    val applyEndAt: LocalDateTime,

    @Column("totalGraceDays")
    val totalGraceDays: Int,
)