package com.wafflestudio.spring2026.waggle.service

import java.time.Instant
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import com.wafflestudio.spring2026.waggle.model.Enrollment
import com.wafflestudio.spring2026.waggle.model.Seminar
import com.wafflestudio.spring2026.waggle.model.User
import com.wafflestudio.spring2026.waggle.model.UserRole
import com.wafflestudio.spring2026.waggle.model.UserStatus
import com.wafflestudio.spring2026.waggle.dto.seminar.SeminarStatus
import com.wafflestudio.spring2026.waggle.repository.EnrollmentRepository

class EnrollmentNotFoundException(enrollmentId: Long): RuntimeException(
    "ID가 ${enrollmentId}인 수강 신청을 찾을 수 없습니다."
)

class UserIsNotRookieException: RuntimeException(
    "루키만 수강 신청할 수 있습니다."
)

class RookieIsNotApprovedException: RuntimeException(
    "가입이 승인된 루키만 수강 신청할 수 있습니다."
)

class SeminarIsNotOpenException: RuntimeException(
    "신청할 수 있는 상태의 세미나가 아닙니다."
)

class AlreadyEnrolledException: RuntimeException(
    "이미 신청한 세미나입니다."
)

class EnrollmentPeriodClosedException: RuntimeException(
    "신청 기간에만 취소할 수 있습니다."
)

@Service
class EnrollmentService(
    private val enrollmentRepository: EnrollmentRepository,
    private val seminarService: SeminarService,
) {
    /**
     * 수강 신청. 선착순이라 별도 승인 없이 즉시 확정된다.
     *
     * 검사 순서가 곧 응답 코드의 순서다.
     *  1. 역할이 ROOKIE 가 아니면      400
     *  2. 가입 상태가 APPROVED 가 아니면 403
     *  3. 이미 신청했으면               409
     *  4. 세미나가 OPEN 이 아니면       409
     * 세미나와 사용자의 존재 여부(404)는 호출 전에 확인한다.
     */
    fun createEnrollment(seminar: Seminar, rookie: User, now: Instant): Enrollment {
        if (rookie.role != UserRole.ROOKIE) throw UserIsNotRookieException()
        if (rookie.status != UserStatus.APPROVED) throw RookieIsNotApprovedException()

        val seminarId = seminar.id!!
        val rookieId = rookie.id!!

        if (enrollmentRepository.existsBySeminarIdAndRookieId(seminarId, rookieId)) {
            throw AlreadyEnrolledException()
        }

        // 신청 기간 안이고 정원이 남아 있어야 한다. 두 조건을 합친 것이 OPEN 이다.
        val enrolledCount = enrollmentRepository.countBySeminarId(seminarId)
        if (seminarService.getStatus(seminar, enrolledCount) != SeminarStatus.OPEN) {
            throw SeminarIsNotOpenException()
        }

        val enrollment = Enrollment(
            // 잔여 Grace Day 는 세미나의 총량에서 시작한다.
            // 세미나의 총량이 나중에 바뀌어도 이미 신청한 사람의 잔여분이 흔들리지 않도록 값을 복사한다.
            graceDaysRemaining = seminar.totalGraceDays,
            createdAt = now,
            rookieId = rookieId,
            seminarId = seminarId,
        )
        return enrollmentRepository.save(enrollment)
    }

    /**
     * 경로의 세미나에 속한 수강 신청만 찾는다.
     * 다른 세미나의 enrollmentId 를 넣어 지우는 것을 막기 위해 seminarId 까지 확인한다.
     */
    fun getEnrollment(seminarId: Long, enrollmentId: Long): Enrollment {
        val enrollment = enrollmentRepository.findByIdOrNull(enrollmentId)
        if (enrollment == null || enrollment.seminarId != seminarId) {
            throw EnrollmentNotFoundException(enrollmentId)
        }
        return enrollment
    }

    /**
     * 수강 취소. 레코드를 실제로 지우므로 enrolledCount 가 줄고 빈자리가 열린다.
     *
     * 정원이 차서 CLOSED 인 세미나라도 신청 기간 안이면 취소할 수 있다.
     * 그래서 세미나 상태가 아니라 기간만 본다: applyStartAt <= now < applyEndAt
     */
    fun deleteEnrollment(seminar: Seminar, enrollment: Enrollment, now: Instant) {
        if (now.isBefore(seminar.applyStartAt) || !now.isBefore(seminar.applyEndAt)) {
            throw EnrollmentPeriodClosedException()
        }
        enrollmentRepository.delete(enrollment)
    }
}
