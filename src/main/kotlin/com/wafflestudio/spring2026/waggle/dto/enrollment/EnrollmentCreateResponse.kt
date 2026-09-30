package com.wafflestudio.spring2026.waggle.dto.enrollment

import java.time.OffsetDateTime
import java.time.ZoneOffset
import com.wafflestudio.spring2026.waggle.model.Enrollment

data class EnrollmentCreateResponse(
    val id: Long,
    val graceDaysRemaining: Int,
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun from(enrollment: Enrollment): EnrollmentCreateResponse =
            EnrollmentCreateResponse(
                id = enrollment.id!!,
                graceDaysRemaining = enrollment.graceDaysRemaining,
                createdAt = enrollment.createdAt.atOffset(ZoneOffset.ofHours(9)),
            )
    }
}
