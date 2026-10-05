package com.wafflestudio.spring2026.waggle.service

import java.time.Instant
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import com.wafflestudio.spring2026.waggle.model.Seminar
import com.wafflestudio.spring2026.waggle.model.Session
import com.wafflestudio.spring2026.waggle.repository.SessionRepository

class SessionNotFoundException(sessionId: Long): RuntimeException(
    "ID가 ${sessionId}인 회차를 찾을 수 없습니다."
)

@Service
class SessionService(
    private val sessionRepository: SessionRepository,
) {
    

    fun getSession(id: Long): Session
        = sessionRepository.findByIdOrNull(id) ?: throw SessionNotFoundException(id)

    fun getSessions(seminar: Seminar): List<Session>
        = sessionRepository.findBySeminarIdOrderByStartsAtAscIdAsc(seminar.id!!)

    fun getSessionRound(session: Session): Int {
        val sessionId = session.id!!
        val sessions = sessionRepository.findBySeminarIdOrderByStartsAtAscIdAsc(session.seminarId)
        val index = sessions.indexOfFirst { it.id == sessionId }

        if (index == -1) throw SessionNotFoundException(sessionId)
        return index + 1
    }
}