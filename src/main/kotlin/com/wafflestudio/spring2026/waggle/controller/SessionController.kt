package com.wafflestudio.spring2026.waggle.controller

import java.net.URI
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import jakarta.validation.Valid
import com.wafflestudio.spring2026.waggle.service.SeminarService
import com.wafflestudio.spring2026.waggle.service.SessionService
import com.wafflestudio.spring2026.waggle.dto.session.SessionCreateRequest
import com.wafflestudio.spring2026.waggle.dto.session.SessionCreateResponse
import com.wafflestudio.spring2026.waggle.dto.session.SessionListResponse
import com.wafflestudio.spring2026.waggle.dto.session.SessionResponse

@RequestMapping("/seminars/{seminarId}/sessions")
@RestController
class SeminarSessionController(
    private val sessionService: SessionService,
    private val seminarService: SeminarService,
) {

    @GetMapping
    fun getSessions(
        @PathVariable("seminarId") seminarId: Long,
    ): ResponseEntity<List<SessionListResponse>> {
        val seminar = seminarService.getSeminar(seminarId)
        val sessions = sessionService.getSessions(seminar)

        val response = sessions.mapIndexed { index, session ->
            SessionListResponse.from(session, index + 1)
        }
        return ResponseEntity.ok(response)
    }
}

@RequestMapping("/sessions")
@RestController
class SessionController(
    private val sessionService: SessionService,
) {
    @GetMapping("/{id}")
    fun getSession(@PathVariable("id") id: Long): ResponseEntity<SessionResponse> {
        val session = sessionService.getSession(id)
        val round = sessionService.getSessionRound(session)

        val response = SessionResponse.from(session, round)
        return ResponseEntity.ok(response)
    }
}