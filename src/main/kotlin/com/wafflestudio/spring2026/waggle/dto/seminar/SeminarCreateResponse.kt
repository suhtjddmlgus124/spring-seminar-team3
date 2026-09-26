package com.wafflestudio.spring2026.waggle.dto.seminar

import java.time.LocalDateTime
import com.wafflestudio.spring2026.waggle.model.Seminar

data class SeminarCreateResponse(
    val id: Long,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(seminar: Seminar, now: LocalDateTime): SeminarCreateResponse
            = SeminarCreateResponse(seminar.id!!, now)
    }
}
