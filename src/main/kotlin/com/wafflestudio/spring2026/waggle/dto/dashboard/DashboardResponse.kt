package com.wafflestudio.spring2026.waggle.dto.dashboard

import java.time.OffsetDateTime
import com.fasterxml.jackson.annotation.JsonInclude
import com.wafflestudio.spring2026.waggle.model.ParticipationAssignmentStatus
import com.wafflestudio.spring2026.waggle.model.ParticipationAttendanceStatus

/** 행은 수강생, 열은 회차인 화면용 응답. 페이지는 content(수강생)에만 적용한다. */
data class DashboardResponse(
    val seminarId: Long,
    val sessions: List<DashboardSession>,
    val content: List<DashboardRow>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class DashboardSession(
    val id: Long,
    val round: Int,
    val title: String,
    val startsAt: OffsetDateTime,
    val assignmentTitle: String,
)

data class DashboardRow(
    /** 수강 신청 ID. 사용자 ID 가 아니다. */
    val id: Long,
    val user: DashboardUser,
    val graceDaysRemaining: Int,
    val dropped: Boolean,
    val createdAt: OffsetDateTime,
    val records: List<DashboardRecord>,
)

data class DashboardUser(
    val id: Long,
    val email: String,
    val name: String,
    val githubUsername: String,
)

/** 미기록은 결석·미제출이 아니다. null 로 내보내되 프로퍼티는 생략하지 않는다. */
@JsonInclude(JsonInclude.Include.ALWAYS)
data class DashboardRecord(
    val sessionId: Long,
    val attendance: ParticipationAttendanceStatus?,
    val assignmentStatus: ParticipationAssignmentStatus?,
)
