package com.wafflestudio.spring2026.waggle.controller

import java.net.URI
import java.time.Instant
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import com.wafflestudio.spring2026.auth.AccessPolicy
import com.wafflestudio.spring2026.auth.AuthPrincipal
import com.wafflestudio.spring2026.waggle.dto.PageQuery
import com.wafflestudio.spring2026.waggle.dto.PageResponse
import com.wafflestudio.spring2026.waggle.dto.enrollment.EnrolledSeminarResponse
import com.wafflestudio.spring2026.waggle.dto.enrollment.MyEnrollmentResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import com.wafflestudio.spring2026.waggle.dto.enrollment.EnrollmentCreateResponse
import com.wafflestudio.spring2026.waggle.service.EnrollmentService
import com.wafflestudio.spring2026.waggle.service.SeminarService

@RequestMapping("/seminars/{seminarId}/enrollments")
@RestController
class EnrollmentController(
    private val enrollmentService: EnrollmentService,
    private val seminarService: SeminarService,
    private val accessPolicy: AccessPolicy,
) {

    /**
     * 3주차부터 신청자는 본문이 아니라 토큰으로 정해진다.
     * 2주차에는 body 의 rookieId 를 믿었는데, 그러면 남의 ID 로 대신 신청할 수 있었다.
     */
    @PostMapping
    fun createEnrollment(
        @PathVariable("seminarId") seminarId: Long,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<EnrollmentCreateResponse> {
        accessPolicy.requireRookie(principal)
        // 존재하지 않는 세미나는 서비스가 404 를 던진다.
        val seminar = seminarService.getSeminar(seminarId)

        val enrollment = enrollmentService.createEnrollment(seminar, principal.user, now())

        val response = EnrollmentCreateResponse.from(enrollment)
        return ResponseEntity
            .created(URI.create("/seminars/$seminarId/enrollments/${enrollment.id}"))
            .body(response)
    }

    /** 지울 수 있는 것은 본인 신청뿐이라 경로가 /me 다. */
    @DeleteMapping("/me")
    fun deleteMyEnrollment(
        @PathVariable("seminarId") seminarId: Long,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        accessPolicy.requireRookie(principal)
        val seminar = seminarService.getSeminar(seminarId)
        val enrollment = enrollmentService.getMyEnrollment(seminarId, principal.userId)

        enrollmentService.deleteEnrollment(seminar, enrollment, now())

        return ResponseEntity.noContent().build()
    }

    // Instant 는 시점 자체라 타임존 해석이 끼어들지 않는다.
    private fun now(): Instant = Instant.now()
}

@RequestMapping("/users/me/enrollments")
@RestController
class MyEnrollmentController(
    private val enrollmentService: EnrollmentService,
    private val seminarService: SeminarService,
    private val accessPolicy: AccessPolicy,
) {

    @GetMapping
    fun getMyEnrollments(
        @RequestParam("page", required = false) page: Int?,
        @RequestParam("size", required = false) size: Int?,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<PageResponse<MyEnrollmentResponse>> {
        accessPolicy.requireRookie(principal)
        val now = Instant.now()
        val enrollments = enrollmentService.getMyEnrollments(principal.userId, PageQuery.of(page, size))

        val response = enrollments.map { enrollment ->
            val seminar = seminarService.getSeminar(enrollment.seminarId)
            val enrolledCount = seminarService.getEnrolledCount(seminar)
            val status = seminarService.getStatus(seminar, enrolledCount, now)
            MyEnrollmentResponse.from(enrollment, EnrolledSeminarResponse.from(seminar, enrolledCount, status))
        }
        return ResponseEntity.ok(response)
    }
}
