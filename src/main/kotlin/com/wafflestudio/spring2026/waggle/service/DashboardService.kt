package com.wafflestudio.spring2026.waggle.service

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import com.wafflestudio.spring2026.PageQuery
import com.wafflestudio.spring2026.PageResponse
import com.wafflestudio.spring2026.auth.repository.UserRepository
import com.wafflestudio.spring2026.waggle.dto.dashboard.DashboardRecord
import com.wafflestudio.spring2026.waggle.dto.dashboard.DashboardResponse
import com.wafflestudio.spring2026.waggle.dto.dashboard.DashboardRow
import com.wafflestudio.spring2026.waggle.dto.dashboard.DashboardSession
import com.wafflestudio.spring2026.waggle.dto.dashboard.DashboardUser
import com.wafflestudio.spring2026.waggle.model.Seminar
import com.wafflestudio.spring2026.waggle.repository.EnrollmentRepository
import com.wafflestudio.spring2026.waggle.repository.ParticipationRepository
import com.wafflestudio.spring2026.waggle.repository.SessionRepository

@Service
class DashboardService(
    private val sessionRepository: SessionRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val participationRepository: ParticipationRepository,
    private val userRepository: UserRepository,
) {
    /**
     * 한 트랜잭션(읽기 전용) 안에서 회차·수강생·기록을 읽는다.
     * MySQL 기본 격리 수준(REPEATABLE READ)에서 같은 스냅샷을 보므로
     * 조회 도중 신청·취소가 끼어들어도 totalElements 와 content 가 서로 어긋나지 않는다.
     */
    @Transactional(readOnly = true)
    fun getDashboard(seminar: Seminar, query: PageQuery): DashboardResponse {
        val seminarId = seminar.id!!

        // 회차 순서: startsAt 오름차순, 같으면 ID 오름차순. round 는 이 순서의 1부터 시작하는 번호이며
        // 기존 회차 조회(findRoundBySessionId: 앞선 회차 수 + 1)와 같은 기준이다.
        val sessions = sessionRepository.findBySeminarIdOrderByStartsAtAscIdAsc(seminarId)
        val sessionColumns = sessions.mapIndexed { index, session ->
            DashboardSession(
                id = session.id!!,
                round = index + 1,
                title = session.title,
                startsAt = session.startsAt.toKst(),
                assignmentTitle = session.assignmentTitle,
            )
        }

        val enrollments = enrollmentRepository.findPageBySeminarId(seminarId, query.size, query.offset)
        val total = enrollmentRepository.countBySeminarId(seminarId)

        val users = userRepository.findAllById(enrollments.map { it.rookieId }).associateBy { it.id!! }
        // (수강 신청 ID, 회차 ID) -> 기록. 기록은 표시 번호(round)가 아니라 회차 ID 에 붙는다.
        val records = if (enrollments.isEmpty()) emptyMap() else
            participationRepository.findByEnrollmentIdIn(enrollments.map { it.id!! })
                .associateBy { it.enrollmentId to it.sessionId }

        val rows = enrollments.map { enrollment ->
            val user = users.getValue(enrollment.rookieId)
            DashboardRow(
                id = enrollment.id!!,
                user = DashboardUser(user.id!!, user.email, user.name, user.githubUsername),
                graceDaysRemaining = enrollment.graceDaysRemaining,
                dropped = enrollment.isFailed,
                createdAt = enrollment.createdAt.toKst(),
                // 모든 회차를 같은 순서로 정확히 한 번씩. 기록이 없으면 두 값 모두 null.
                records = sessionColumns.map { column ->
                    val record = records[enrollment.id to column.id]
                    DashboardRecord(column.id, record?.attendanceStatus, record?.assignmentStatus)
                },
            )
        }

        val page = PageResponse.of(rows, query, total)
        return DashboardResponse(
            seminarId = seminarId,
            sessions = sessionColumns,
            content = page.content,
            page = page.page,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
        )
    }

    private fun Instant.toKst(): OffsetDateTime = atOffset(ZoneOffset.ofHours(9))
}
