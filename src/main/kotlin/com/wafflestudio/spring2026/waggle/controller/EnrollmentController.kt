package com.wafflestudio.spring2026.waggle.controller

import java.net.URI
import java.time.LocalDateTime
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import jakarta.validation.Valid
import com.wafflestudio.spring2026.waggle.dto.enrollment.EnrollmentCreateRequest
import com.wafflestudio.spring2026.waggle.dto.enrollment.EnrollmentCreateResponse
import com.wafflestudio.spring2026.waggle.service.EnrollmentService
import com.wafflestudio.spring2026.waggle.service.SeminarService
import com.wafflestudio.spring2026.waggle.service.UserService

@RequestMapping("/seminars/{seminarId}/enrollments")
@RestController
class EnrollmentController(
    private val enrollmentService: EnrollmentService,
    private val seminarService: SeminarService,
    private val userService: UserService,
) {

    @PostMapping
    fun createEnrollment(
        @PathVariable("seminarId") seminarId: Long,
        @Valid @RequestBody request: EnrollmentCreateRequest,
    ): ResponseEntity<EnrollmentCreateResponse> {
        // 존재하지 않는 세미나 또는 사용자는 각 서비스가 404 를 던진다.
        val seminar = seminarService.getSeminar(seminarId)
        val rookie = userService.getUser(request.rookieId)

        val enrollment = enrollmentService.createEnrollment(seminar, rookie, now())

        val response = EnrollmentCreateResponse.from(enrollment)
        return ResponseEntity
            .created(URI.create("/seminars/$seminarId/enrollments/${enrollment.id}"))
            .body(response)
    }

    @DeleteMapping("/{enrollmentId}")
    fun deleteEnrollment(
        @PathVariable("seminarId") seminarId: Long,
        @PathVariable("enrollmentId") enrollmentId: Long,
    ): ResponseEntity<Void> {
        val seminar = seminarService.getSeminar(seminarId)
        val enrollment = enrollmentService.getEnrollment(seminarId, enrollmentId)

        enrollmentService.deleteEnrollment(seminar, enrollment, now())

        return ResponseEntity.noContent().build()
    }

    // 저장된 시각과 같은 기준으로 비교하기 위해 시스템 기본 타임존을 타지 않는다.
    // 팀에서 저장 규약(UTC)을 확정하면 공통 유틸로 옮긴다.
    private fun now(): LocalDateTime = LocalDateTime.now(java.time.ZoneOffset.UTC)
}
