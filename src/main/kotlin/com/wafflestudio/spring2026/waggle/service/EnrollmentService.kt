package com.wafflestudio.spring2026.waggle.service

import java.time.Instant
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.annotation.Isolation
import com.wafflestudio.spring2026.waggle.model.Enrollment
import com.wafflestudio.spring2026.waggle.model.Seminar
import com.wafflestudio.spring2026.auth.model.User
import com.wafflestudio.spring2026.auth.model.UserRole
import com.wafflestudio.spring2026.auth.model.UserStatus
import com.wafflestudio.spring2026.waggle.dto.seminar.SeminarStatus
import com.wafflestudio.spring2026.waggle.repository.EnrollmentRepository
import com.wafflestudio.spring2026.waggle.repository.SeminarRepository
import com.wafflestudio.spring2026.waggle.dto.enrollment.EnrollmentUpdateRequest
import com.wafflestudio.spring2026.waggle.dto.enrollment.InvalidEnrollmentUpdateRequestException
import com.wafflestudio.spring2026.waggle.repository.ParticipationRepository
import com.wafflestudio.spring2026.PageQuery
import com.wafflestudio.spring2026.PageResponse

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

class MyEnrollmentNotFoundException(seminarId: Long): RuntimeException(
    "ID가 ${seminarId}인 세미나에 신청한 내역이 없습니다."
)

class EnrollmentPeriodClosedException: RuntimeException(
    "신청 기간에만 취소할 수 있습니다."
)

@Service
class EnrollmentService(
    private val enrollmentRepository: EnrollmentRepository,
    private val seminarRepository: SeminarRepository,
    private val seminarService: SeminarService,
    private val participationRepository: ParticipationRepository,
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
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun createEnrollment(seminar: Seminar, rookie: User, now: Instant): Enrollment {
        if (rookie.role != UserRole.ROOKIE) throw UserIsNotRookieException()
        if (rookie.status != UserStatus.APPROVED) throw RookieIsNotApprovedException()

        val seminarId = seminar.id!!
        val rookieId = rookie.id!!

        // 세미나 행을 커밋까지 잠가 같은 세미나의 중복 확인, 정원 확인, 저장을 순서대로 처리한다.
        val lockedSeminar = seminarRepository.findByIdForUpdate(seminarId)
            ?: throw SeminarNotFoundException(seminarId)

        if (enrollmentRepository.existsBySeminarIdAndRookieId(seminarId, rookieId)) {
            throw AlreadyEnrolledException()
        }

        // 신청 기간 안이고 정원이 남아 있어야 한다. 두 조건을 합친 것이 OPEN 이다.
        val enrolledCount = enrollmentRepository.countBySeminarId(seminarId)
        if (seminarService.getStatus(lockedSeminar, enrolledCount, Instant.now()) != SeminarStatus.OPEN) {
            throw SeminarIsNotOpenException()
        }

        val enrollment = Enrollment(
            // 잔여 Grace Day 는 세미나의 총량에서 시작한다.
            // 세미나의 총량이 나중에 바뀌어도 이미 신청한 사람의 잔여분이 흔들리지 않도록 값을 복사한다.
            graceDaysRemaining = lockedSeminar.totalGraceDays,
            createdAt = now,
            rookieId = rookieId,
            seminarId = seminarId,
        )
        return saveUnlessDuplicated(enrollment)
    }

    /**
     * 같은 루키가 동시에 여러 번 신청하면 위의 exists 검사를 모두 통과할 수 있다(조회와 저장 사이의 경쟁).
     * 그래서 최종 판단은 DB 의 UNIQUE(rookieId, seminarId) 가 한다.
     * 그 제약 위반만 409(이미 신청)로 바꾸고, 다른 DB 오류는 숨기지 않고 그대로 던진다.
     * 실패한 INSERT 는 행을 남기지 않으므로 빈자리를 소비하지 않는다.
     */
    private fun saveUnlessDuplicated(enrollment: Enrollment): Enrollment =
        try {
            enrollmentRepository.save(enrollment)
        } catch (e: DuplicateKeyException) {
            if (e.mostSpecificCause.message?.contains(ENROLLMENT_UNIQUE_KEY, ignoreCase = true) == true) {
                throw AlreadyEnrolledException()
            }
            throw e
        }

    companion object {
        /** V1__init.sql 의 UNIQUE INDEX 이름 */
        private const val ENROLLMENT_UNIQUE_KEY = "UQ_enrollments_rookieId_seminarId"
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
     * 요청자 본인의 신청을 찾는다. 3주차부터 수강 취소는 신청 ID 가 아니라 토큰으로 대상을 정한다.
     * 신청한 적이 없으면 404 다. 남의 신청을 지울 경로 자체가 없다.
     */
    fun getMyEnrollment(seminarId: Long, rookieId: Long): Enrollment =
        enrollmentRepository.findBySeminarIdAndRookieId(seminarId, rookieId)
            ?: throw MyEnrollmentNotFoundException(seminarId)

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

    /** 내 수강 목록. 최근 신청 순, 신청 일시가 같으면 ID 내림차순. */
    fun getMyEnrollments(rookieId: Long, query: PageQuery): PageResponse<Enrollment> {
        val enrollments = enrollmentRepository.findPageByRookieId(rookieId, query.size, query.offset)
        val total = enrollmentRepository.countByRookieId(rookieId)
        return PageResponse.of(enrollments, query, total)
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun updateEnrollment(
        seminarId: Long,
        enrollmentId: Long,
        request: EnrollmentUpdateRequest,
    ): Enrollment {
        val seminar = seminarRepository.findByIdForUpdate(seminarId)
            ?: throw SeminarNotFoundException(seminarId)

        val enrollment = getEnrollment(seminarId, enrollmentId)

        if (request.graceDaysRemaining == null && request.dropped == null) {
            throw InvalidEnrollmentUpdateRequestException(
                "수정할 항목을 하나 이상 보내야 합니다.",
            )
        }

        val graceDaysRemaining = request.graceDaysRemaining
            ?: enrollment.graceDaysRemaining

        if (graceDaysRemaining !in 0..seminar.totalGraceDays) {
            throw InvalidEnrollmentUpdateRequestException(
                "잔여 Grace Day는 0 이상 ${seminar.totalGraceDays} 이하여야 합니다.",
            )
        }

        return enrollmentRepository.save(
            enrollment.copy(
                graceDaysRemaining = graceDaysRemaining,
                isFailed = request.dropped ?: enrollment.isFailed,
            ),
        )
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun cancelMyEnrollment(
        seminarId: Long,
        rookieId: Long,
        now: Instant,
    ) {
        val seminar = seminarRepository.findByIdForUpdate(seminarId)
            ?: throw SeminarNotFoundException(seminarId)

        if (now.isBefore(seminar.applyStartAt) || !now.isBefore(seminar.applyEndAt)) {
            throw EnrollmentPeriodClosedException()
        }

        val enrollment = enrollmentRepository.findBySeminarIdAndRookieId(
            seminarId,
            rookieId,
        ) ?: throw MyEnrollmentNotFoundException(seminarId)

        participationRepository.deleteAllByEnrollmentId(enrollment.id!!)

        val deletedCount = enrollmentRepository.deleteByIdReturningCount(
            enrollment.id,
        )

        if (deletedCount != 1) {
            throw MyEnrollmentNotFoundException(seminarId)
        }
    }
}
