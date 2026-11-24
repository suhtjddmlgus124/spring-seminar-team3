package com.wafflestudio.spring2026.waggle.dto.enrollment

import java.time.OffsetDateTime
import java.time.ZoneOffset
import com.wafflestudio.spring2026.waggle.model.Enrollment

data class EnrollmentUpdateResponse(
    val id: Long,
    val graceDaysRemaining: Int,
    val dropped: Boolean,
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun from(enrollment: Enrollment): EnrollmentUpdateResponse =
            EnrollmentUpdateResponse(
                id = enrollment.id!!,
                graceDaysRemaining = enrollment.graceDaysRemaining,
                dropped = enrollment.isFailed,
                createdAt = enrollment.createdAt.atOffset(ZoneOffset.ofHours(9)),
            )
    }
}
