package com.wafflestudio.spring2026.waggle.service

import java.time.Instant
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import com.wafflestudio.spring2026.waggle.model.Seminar
import com.wafflestudio.spring2026.waggle.model.Session
import com.wafflestudio.spring2026.waggle.repository.SessionRepository
import com.wafflestudio.spring2026.waggle.dto.session.SessionCreateRequest
import com.wafflestudio.spring2026.waggle.dto.session.SessionCreateResponse
import com.wafflestudio.spring2026.waggle.repository.SeminarRepository

class SessionNotFoundException(sessionId: Long): RuntimeException(
    "ID가 ${sessionId}인 회차를 찾을 수 없습니다."
)

@Service
class SessionService(
    private val sessionRepository: SessionRepository,
    private val seminarRepository: SeminarRepository,
) {
    fun getSession(id: Long): Session
        = sessionRepository.findByIdOrNull(id) ?: throw SessionNotFoundException(id)

    fun getSessions(seminar: Seminar): List<Session>
        = sessionRepository.findBySeminarIdOrderByStartsAtAscIdAsc(seminar.id!!)

    // fun getSessionRound(session: Session): Int {
    //     val sessionId = session.id!!
    //     val sessions = sessionRepository.findBySeminarIdOrderByStartsAtAscIdAsc(session.seminarId)
    //     val index = sessions.indexOfFirst { it.id == sessionId }

    //     if (index == -1) throw SessionNotFoundException(sessionId)
    //     return index + 1
    // }
    fun getSessionRound(session: Session): Long {
        sessionRepository.findByIdOrNull(session.id!!)
        ?: throw SessionNotFoundException(session.id)
        val round = sessionRepository.findRoundBySessionId(session.id)
        return round
    }
        
    fun createSession(seminarId: Long, request: SessionCreateRequest): SessionCreateResponse {
        if (!seminarRepository.existsById(seminarId)) {
            throw SeminarNotFoundException(seminarId)
        }
        val session = sessionRepository.save(
            Session(
                title = request.title,
                startsAt = request.startsAt.toInstant(),
                location = request.location,
                assignmentTitle = request.assignmentTitle,
                lectureContent = request.lectureContent,
                assignmentContent = request.assignmentContent,
                seminarId = seminarId,
            )
        )
        val round = sessionRepository.findRoundBySessionId(session.id!!)

        return SessionCreateResponse(
            id = session.id,
            seminarId = session.seminarId,
            round = round,
        )
    }
}