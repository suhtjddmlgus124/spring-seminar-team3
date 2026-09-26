package com.wafflestudio.spring2026.waggle.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

enum class ParticipationAttendanceStatus { PRESENT, ABSENT }

enum class ParticipationAssignmentStatus { PASSED, SUBMITTED_BUT_FAILED, NOT_SUBMITTED }

@Table("participations")
data class Participation(
    @Id
    val id: Long? = null,

    @Column("attendanceStatus")
    val attendanceStatus: ParticipationAttendanceStatus?,

    @Column("assignmentStatus")
    val assignmentStatus: ParticipationAssignmentStatus?,

    @Column("rookieId")
    val rookieId: Long,

    @Column("sessionId")
    val sessionId: Long,
)