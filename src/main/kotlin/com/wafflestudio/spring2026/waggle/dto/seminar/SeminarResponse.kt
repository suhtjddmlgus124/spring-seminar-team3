package com.wafflestudio.spring2026.waggle.dto.seminar

import java.time.LocalDateTime
import com.wafflestudio.spring2026.waggle.model.Seminar

enum class SeminarStatus { BEFORE, CLOSED, OPEN }

data class SeminarResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val capacity: Int,
    val enrolledCount: Long,
    val applyStartAt: LocalDateTime,
    val applyEndAt: LocalDateTime,
    val totalGraceDays: Int,
    val status: SeminarStatus,
    val sessionCount: Long,
) {
    companion object {
        fun from(
            seminar: Seminar, 
            enrolledCount: Long, 
            status: SeminarStatus, 
            sessionCount: Long
        ): SeminarResponse 
            = SeminarResponse(
                id = seminar.id!!,
                title = seminar.title,
                description = seminar.description,
                capacity = seminar.capacity,
                enrolledCount = enrolledCount,
                applyStartAt = seminar.applyStartAt,
                applyEndAt = seminar.applyEndAt,
                totalGraceDays = seminar.totalGraceDays,
                status = status,
                sessionCount = sessionCount,
            )
    }
}