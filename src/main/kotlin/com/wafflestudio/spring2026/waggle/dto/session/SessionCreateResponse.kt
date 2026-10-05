package com.wafflestudio.spring2026.waggle.dto.session

import com.wafflestudio.spring2026.waggle.model.Session

data class SessionCreateResponse(
    val id: Long,
    val seminarId: Long,
    val round: Long,
) {
    companion object {
        fun from(session: Session, round: Long): SessionCreateResponse
            = SessionCreateResponse(
                id = session.id!!,
                seminarId = session.seminarId,
                round = round,
            )
    }
}