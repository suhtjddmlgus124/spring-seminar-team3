package com.wafflestudio.spring2026.waggle.dto.seminar

import java.time.OffsetDateTime
import java.time.ZoneOffset
import com.wafflestudio.spring2026.waggle.model.Seminar

data class SeminarCreateResponse(
    val id: Long,
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun from(seminar: Seminar): SeminarCreateResponse
            = SeminarCreateResponse(seminar.id!!, seminar.createdAt.atOffset(ZoneOffset.ofHours(9)))
    }
}
