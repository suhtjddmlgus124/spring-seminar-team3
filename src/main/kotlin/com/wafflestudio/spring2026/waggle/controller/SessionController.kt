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
import tools.jackson.databind.JsonNode
import com.wafflestudio.spring2026.waggle.service.SeminarService
import com.wafflestudio.spring2026.waggle.service.SessionService
import com.wafflestudio.spring2026.waggle.service.ParticipationService
import com.wafflestudio.spring2026.waggle.service.BatchUpdateRecordsParser
import com.wafflestudio.spring2026.waggle.dto.session.BatchUpdateRecordsResponse
import com.wafflestudio.spring2026.waggle.dto.session.SessionCreateRequest
import com.wafflestudio.spring2026.waggle.dto.session.SessionCreateResponse
import com.wafflestudio.spring2026.waggle.dto.session.SessionListResponse
import com.wafflestudio.spring2026.waggle.dto.session.SessionResponse
import com.wafflestudio.spring2026.waggle.dto.session.SessionUpdateRequest
import com.wafflestudio.spring2026.auth.AccessPolicy
import com.wafflestudio.spring2026.auth.AuthPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PatchMapping

@RequestMapping("/seminars/{seminarId}/sessions")
@RestController
class SeminarSessionController(
    private val sessionService: SessionService,
    private val seminarService: SeminarService,
    private val accessPolicy: AccessPolicy,
) {
    @GetMapping
    fun getSessions(
        @PathVariable("seminarId") seminarId: Long,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<List<SessionListResponse>> {
        val seminar = seminarService.getSeminar(seminarId)
        accessPolicy.requireSessionViewer(principal, seminarId)
        val sessions = sessionService.getSessions(seminar)

        val response = sessions.mapIndexed { index, session ->
            SessionListResponse.from(session, index + 1)
        }
        return ResponseEntity.ok(response)
    }

    @PostMapping
    fun createSession(
        @PathVariable("seminarId") seminarId: Long,
        @Valid @RequestBody request: SessionCreateRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<SessionCreateResponse> {
        accessPolicy.requireSeminarManager(principal, seminarId)
        val response = sessionService.createSession(seminarId, request)
        return ResponseEntity
            .created(URI.create("/sessions/${response.id}"))
            .body(response)
    }
}

@RequestMapping("/sessions")
@RestController
class SessionController(
    private val sessionService: SessionService,
    private val accessPolicy: AccessPolicy,
    private val participationService: ParticipationService,
    private val batchUpdateRecordsParser: BatchUpdateRecordsParser,
) {
    @GetMapping("/{id}")
    fun getSession(
        @PathVariable("id") id: Long,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<SessionResponse> {
        val session = sessionService.getSession(id)
        accessPolicy.requireSessionViewer(principal, session.seminarId)
        val round = sessionService.getSessionRound(session)

        val response = SessionResponse.from(session, round)
        return ResponseEntity.ok(response)
    }

    @PatchMapping("/{id}")
    fun updateSession(
        @PathVariable("id") id: Long,
        @Valid @RequestBody request: SessionUpdateRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<SessionResponse> {
        val session = sessionService.getSession(id)
        accessPolicy.requireSeminarManager(principal, session.seminarId)

        val updated = sessionService.updateSession(session, request)
        val round = sessionService.getSessionRound(updated)

        return ResponseEntity.ok(SessionResponse.from(updated, round))
    }

    @PatchMapping("/{sessionId}/records")
    fun updateSessionRecords(
        @PathVariable("sessionId") sessionId: Long,
        @RequestBody body: JsonNode,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<BatchUpdateRecordsResponse> {
        val session = sessionService.getSession(sessionId)
        accessPolicy.requireSeminarManager(principal, session.seminarId)

        val commands = batchUpdateRecordsParser.parse(body)
        val response = participationService.batchUpdateRecords(session, commands)
        return ResponseEntity.ok(response)
    }
}