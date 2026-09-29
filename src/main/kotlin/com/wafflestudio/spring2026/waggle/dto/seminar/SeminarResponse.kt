package com.wafflestudio.spring2026.waggle.dto.seminar

import java.time.OffsetDateTime
import java.time.ZoneOffset
import com.wafflestudio.spring2026.waggle.model.Seminar

enum class SeminarStatus { BEFORE, CLOSED, OPEN }

data class SeminarResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val capacity: Int,
    val enrolledCount: Long,
    val applyStartAt: OffsetDateTime,
    val applyEndAt: OffsetDateTime,
    val totalGraceDays: Int,
    val status: SeminarStatus,
    val sessionCount: Long,
    val createdAt: OffsetDateTime,
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
                applyStartAt = seminar.applyStartAt.atOffset(ZoneOffset.ofHours(9)),
                applyEndAt = seminar.applyEndAt.atOffset(ZoneOffset.ofHours(9)),
                totalGraceDays = seminar.totalGraceDays,
                status = status,
                sessionCount = sessionCount,
                createdAt = seminar.createdAt.atOffset(ZoneOffset.ofHours(9))
            )
    }
}