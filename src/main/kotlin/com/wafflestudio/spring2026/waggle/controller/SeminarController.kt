package com.wafflestudio.spring2026.waggle.controller

import java.time.Instant
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.http.ResponseEntity
import com.wafflestudio.spring2026.waggle.service.SeminarService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import jakarta.validation.Valid
import java.net.URI
import com.wafflestudio.spring2026.waggle.dto.seminar.SeminarResponse
import com.wafflestudio.spring2026.waggle.dto.seminar.SeminarCreateRequest
import com.wafflestudio.spring2026.waggle.dto.seminar.SeminarCreateResponse
import com.wafflestudio.spring2026.waggle.dto.seminar.SeminarUpdateRequest
import com.wafflestudio.spring2026.waggle.dto.seminar.SeminarStatus
import com.wafflestudio.spring2026.waggle.dto.PageQuery
import com.wafflestudio.spring2026.waggle.dto.PageResponse
import com.wafflestudio.spring2026.waggle.model.Seminar
import com.wafflestudio.spring2026.auth.AccessPolicy
import com.wafflestudio.spring2026.auth.AuthPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.RequestParam

@RequestMapping("/seminars")
@RestController
class SeminarController(
    private val seminarService: SeminarService,
    private val accessPolicy: AccessPolicy,
) {

    /** 로그인 없이 볼 수 있다. 운영진으로 가입하려는 사람이 담당할 세미나를 골라야 하기 때문이다. */
    @GetMapping
    fun getSeminars(
        @RequestParam("keyword", required = false) keyword: String?,
        @RequestParam("status", required = false) status: SeminarStatus?,
        @RequestParam("page", required = false) page: Int?,
        @RequestParam("size", required = false) size: Int?,
    ): ResponseEntity<PageResponse<SeminarResponse>> {
        val now = Instant.now()
        val seminars = seminarService.searchSeminars(keyword, status, PageQuery.of(page, size), now)

        return ResponseEntity.ok(seminars.map { toResponse(it, now) })
    }

    @GetMapping("/{id}")
    fun getSeminar(
        @PathVariable("id") id: Long,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<SeminarResponse> {
        accessPolicy.requireApproved(principal)
        val seminar = seminarService.getSeminar(id)
        val enrolledCount = seminarService.getEnrolledCount(seminar)
        val sessionCount = seminarService.getSessionCount(seminar)
        val status = seminarService.getStatus(seminar, enrolledCount, Instant.now())

        val response = SeminarResponse.from(seminar, enrolledCount, status, sessionCount)        
        return ResponseEntity.ok(response)
    }
    
    @PostMapping
    fun createSeminar(
        @Valid @RequestBody request: SeminarCreateRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<SeminarCreateResponse> {
        accessPolicy.requireAdmin(principal)
        val seminar = seminarService.createSeminar(
            title = request.title,
            description = request.description,
            capacity = request.capacity,
            applyStartAt = request.applyStartAt.toInstant(),
            applyEndAt = request.applyEndAt.toInstant(),
            totalGraceDays = request.totalGraceDays,
            now = Instant.now(),
        )
        
        val response = SeminarCreateResponse.from(seminar)
        return ResponseEntity
            .created(URI.create("/seminars/${seminar.id}"))
            .body(response)
    }
    
    @PatchMapping("/{id}")
    fun updateSeminar(
        @PathVariable("id") id: Long,
        @Valid @RequestBody request: SeminarUpdateRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<SeminarResponse> {
        val seminar = seminarService.getSeminar(id)
        accessPolicy.requireSeminarManager(principal, id)
        
        val title = 
            if(request.title.isPresent) request.title.get() 
            else seminar.title
        val description = 
            if(request.description.isPresent) request.description.orElse(null) 
            else seminar.description
        val updatedSeminar = seminarService.updateSeminar(seminar, title, description)
        
        val enrolledCount = seminarService.getEnrolledCount(seminar)
        val sessionCount = seminarService.getSessionCount(seminar)
        val status = seminarService.getStatus(seminar, enrolledCount, Instant.now())
        
        val response = SeminarResponse.from(updatedSeminar, enrolledCount, status, sessionCount)
        return ResponseEntity.ok(response)
    }

    private fun toResponse(seminar: Seminar, now: Instant): SeminarResponse {
        val enrolledCount = seminarService.getEnrolledCount(seminar)
        val sessionCount = seminarService.getSessionCount(seminar)
        val status = seminarService.getStatus(seminar, enrolledCount, now)
        return SeminarResponse.from(seminar, enrolledCount, status, sessionCount)
    }
}
