package com.wafflestudio.spring2026.waggle.dto.session

import java.time.OffsetDateTime
import java.time.ZoneOffset
import com.wafflestudio.spring2026.waggle.model.Session

data class SessionListResponse(
    val id: Long,
    val seminarId: Long,
    val round: Int,
    val title: String,
    val startsAt: OffsetDateTime,
    val location: String,
    val assignmentTitle: String,
) {
    companion object {
        fun from(session: Session, round: Int): SessionListResponse
            = SessionListResponse(
                id = session.id!!,
                seminarId = session.seminarId,
                round = round,
                title = session.title,
                startsAt = session.startsAt.atOffset(ZoneOffset.ofHours(9)),
                location = session.location,
                assignmentTitle = session.assignmentTitle,
            )
    }
}