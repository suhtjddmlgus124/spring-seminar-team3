package com.wafflestudio.spring2026.waggle.dto.enrollment

import java.time.OffsetDateTime
import java.time.ZoneOffset
import com.wafflestudio.spring2026.waggle.dto.seminar.SeminarStatus
import com.wafflestudio.spring2026.waggle.model.Enrollment
import com.wafflestudio.spring2026.waggle.model.Seminar

/** 내 수강 목록의 한 항목. id 는 수강 신청(enrollment)의 ID 다. */
data class MyEnrollmentResponse(
    val id: Long,
    val seminar: EnrolledSeminarResponse,
    val graceDaysRemaining: Int,
    val dropped: Boolean,
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun from(enrollment: Enrollment, seminar: EnrolledSeminarResponse): MyEnrollmentResponse =
            MyEnrollmentResponse(
                id = enrollment.id!!,
                seminar = seminar,
                graceDaysRemaining = enrollment.graceDaysRemaining,
                // 탈락한 수강생이다. DB 컬럼 이름은 isFailed 다.
                dropped = enrollment.isFailed,
                createdAt = enrollment.createdAt.atOffset(ZoneOffset.ofHours(9)),
            )
    }
}

/** 각 항목은 GET /seminars/{seminarId} 의 같은 이름 항목과 같은 의미다. */
data class EnrolledSeminarResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val status: SeminarStatus,
    val capacity: Int,
    val enrolledCount: Long,
    val applyStartAt: OffsetDateTime,
    val applyEndAt: OffsetDateTime,
) {
    companion object {
        fun from(seminar: Seminar, enrolledCount: Long, status: SeminarStatus): EnrolledSeminarResponse =
            EnrolledSeminarResponse(
                id = seminar.id!!,
                title = seminar.title,
                description = seminar.description,
                status = status,
                capacity = seminar.capacity,
                enrolledCount = enrolledCount,
                applyStartAt = seminar.applyStartAt.atOffset(ZoneOffset.ofHours(9)),
                applyEndAt = seminar.applyEndAt.atOffset(ZoneOffset.ofHours(9)),
            )
    }
}
