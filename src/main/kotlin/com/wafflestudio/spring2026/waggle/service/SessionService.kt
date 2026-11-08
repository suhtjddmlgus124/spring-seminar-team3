package com.wafflestudio.spring2026.waggle.service

import java.time.Instant
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import com.wafflestudio.spring2026.waggle.model.Seminar
import com.wafflestudio.spring2026.waggle.model.Session
import com.wafflestudio.spring2026.waggle.repository.SessionRepository
import com.wafflestudio.spring2026.waggle.dto.session.SessionCreateRequest
import com.wafflestudio.spring2026.waggle.dto.session.SessionCreateResponse
import com.wafflestudio.spring2026.waggle.dto.session.SessionUpdateRequest
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

    /**
     * 보낸 항목만 고친다. startsAt 을 바꾸면 회차 번호가 달라질 수 있는데,
     * 번호는 저장하지 않고 조회할 때마다 시작 시각 순서로 계산하므로 따로 고칠 것이 없다.
     */
    fun updateSession(session: Session, request: SessionUpdateRequest): Session {
        val updated = session.copy(
            title = if (request.title.isPresent) request.title.get() else session.title,
            startsAt = if (request.startsAt.isPresent) request.startsAt.get().toInstant() else session.startsAt,
            location = if (request.location.isPresent) request.location.get() else session.location,
            assignmentTitle = if (request.assignmentTitle.isPresent) request.assignmentTitle.get() else session.assignmentTitle,
            lectureContent = if (request.lectureContent.isPresent) request.lectureContent.orElse(null) else session.lectureContent,
            assignmentContent = if (request.assignmentContent.isPresent) request.assignmentContent.orElse(null) else session.assignmentContent,
        )
        return sessionRepository.save(updated)
    }
}
