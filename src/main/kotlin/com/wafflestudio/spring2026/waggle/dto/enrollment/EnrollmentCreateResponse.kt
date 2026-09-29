package com.wafflestudio.spring2026.waggle.dto.enrollment

import java.time.LocalDateTime
import com.wafflestudio.spring2026.waggle.model.Enrollment

data class EnrollmentCreateResponse(
    val id: Long,
    val graceDaysRemaining: Int,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(enrollment: Enrollment): EnrollmentCreateResponse =
            EnrollmentCreateResponse(
                id = enrollment.id!!,
                graceDaysRemaining = enrollment.graceDaysRemaining,
                createdAt = enrollment.createdAt,
            )
    }
}
