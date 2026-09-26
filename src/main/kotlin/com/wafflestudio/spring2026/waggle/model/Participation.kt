package com.wafflestudio.spring2026.waggle.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

enum class AttendanceStatus { PRESENT, ABSENT }

enum class AssignmentStatus { PASSED, SUBMITTED_BUT_FAILED, NOT_SUBMITTED }

@Table("participations")
data class Participation(
    @Id
    val id: Long? = null,

    @Column("attendanceStatus")
    val attendanceStatus: AttendanceStatus?,

    @Column("assignmentStatus")
    val assignmentStatus: AssignmentStatus?,

    @Column("rookieId")
    val rookieId: Long,

    @Column("sessionId")
    val sessionId: Long,
)