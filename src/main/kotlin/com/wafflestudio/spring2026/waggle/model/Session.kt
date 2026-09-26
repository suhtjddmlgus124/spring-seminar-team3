package com.wafflestudio.spring2026.waggle.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("sessions")
data class Session(
    @Id
    val id: Long? = null,

    @Column("title")
    val title: String,

    @Column("startsAt")
    val startsAt: LocalDateTime,
    
    @Column("location")
    val location: String,

    @Column("assignmentTitle")
    val assignmentTitle: String,

    @Column("lectureContent")
    val lectureContent: String?,

    @Column("assignmentContent")
    val assignmentContent: String?,

    @Column("seminarId")
    val seminarId: Long,
)