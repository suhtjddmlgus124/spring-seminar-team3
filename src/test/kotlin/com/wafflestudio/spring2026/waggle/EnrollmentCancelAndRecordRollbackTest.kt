package com.wafflestudio.spring2026.waggle

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import tools.jackson.databind.ObjectMapper
import com.wafflestudio.spring2026.waggle.dto.session.FieldPatch
import com.wafflestudio.spring2026.waggle.dto.session.RecordUpdateCommand
import com.wafflestudio.spring2026.waggle.model.Enrollment
import com.wafflestudio.spring2026.waggle.model.Participation
import com.wafflestudio.spring2026.waggle.model.ParticipationAssignmentStatus
import com.wafflestudio.spring2026.waggle.model.ParticipationAttendanceStatus
import com.wafflestudio.spring2026.waggle.repository.EnrollmentRepository
import com.wafflestudio.spring2026.waggle.repository.ParticipationRepository
import com.wafflestudio.spring2026.waggle.repository.SeminarRepository
import com.wafflestudio.spring2026.waggle.repository.SessionRepository
import com.wafflestudio.spring2026.waggle.service.BatchUpdateRecordsParser
import com.wafflestudio.spring2026.waggle.service.EnrollmentNotFoundException
import com.wafflestudio.spring2026.waggle.service.EnrollmentService
import com.wafflestudio.spring2026.waggle.service.MyEnrollmentNotFoundException
import com.wafflestudio.spring2026.waggle.service.ParticipationService

@SpringBootTest
class EnrollmentCancelAndRecordRollbackTest @Autowired constructor(
    private val enrollmentService: EnrollmentService,
    private val participationService: ParticipationService,
    private val batchUpdateRecordsParser: BatchUpdateRecordsParser,
    private val enrollmentRepository: EnrollmentRepository,
    private val participationRepository: ParticipationRepository,
    private val seminarRepository: SeminarRepository,
    private val sessionRepository: SessionRepository,
) {
    private val objectMapper = ObjectMapper()

    @Test
    fun `동시에 같은 수강 신청을 두 번 취소하면 1건만 성공하고 1건은 예외가 발생하며 출석 기록도 삭제된다`() {
        val seminar = seminarRepository.findAll().firstOrNull()
            ?: return
        val session = sessionRepository.findAll().firstOrNull { it.seminarId == seminar.id }
            ?: return

        val now = seminar.applyStartAt.plus(1, ChronoUnit.SECONDS)
        val testRookieId = 999901L

        val enrollment = enrollmentRepository.save(
            Enrollment(
                graceDaysRemaining = seminar.totalGraceDays,
                createdAt = now,
                rookieId = testRookieId,
                seminarId = seminar.id!!,
            )
        )
        val enrollmentId = enrollment.id!!

        try {
            participationRepository.save(
                Participation(
                    attendanceStatus = ParticipationAttendanceStatus.PRESENT,
                    assignmentStatus = ParticipationAssignmentStatus.PASSED,
                    enrollmentId = enrollmentId,
                    sessionId = session.id!!,
                )
            )

            val executor = Executors.newFixedThreadPool(2)
            val readyLatch = CountDownLatch(2)
            val startLatch = CountDownLatch(1)

            val futures = (1..2).map {
                executor.submit<Result<Unit>> {
                    readyLatch.countDown()
                    startLatch.await()
                    runCatching {
                        enrollmentService.cancelMyEnrollment(seminar.id!!, testRookieId, now)
                    }
                }
            }

            readyLatch.await()
            startLatch.countDown()

            val results = futures.map { it.get() }
            executor.shutdown()

            val successCount = results.count { it.isSuccess }
            val notFoundCount = results.count { it.exceptionOrNull() is MyEnrollmentNotFoundException }

            assertEquals(1, successCount)
            assertEquals(1, notFoundCount)
            assertNull(enrollmentRepository.findByIdOrNull(enrollmentId))
            assertNull(participationRepository.findByEnrollmentIdAndSessionId(enrollmentId, session.id!!))
        } finally {
            participationRepository.deleteAllByEnrollmentId(enrollmentId)
            if (enrollmentRepository.existsById(enrollmentId)) {
                enrollmentRepository.deleteById(enrollmentId)
            }
        }
    }

    @Test
    fun `출석 및 과제 일괄 수정 중 1건이라도 실패하면 이전 수정 내역까지 전부 롤백된다`() {
        val seminar = seminarRepository.findAll().firstOrNull()
            ?: return
        val session = sessionRepository.findAll().firstOrNull { it.seminarId == seminar.id }
            ?: return

        val enrollment = enrollmentRepository.save(
            Enrollment(
                graceDaysRemaining = seminar.totalGraceDays,
                createdAt = Instant.now(),
                rookieId = 999902L,
                seminarId = seminar.id!!,
            )
        )
        val enrollmentId = enrollment.id!!

        try {
            val commands = listOf(
                RecordUpdateCommand(
                    enrollmentId = enrollmentId,
                    attendanceStatus = FieldPatch.Set(ParticipationAttendanceStatus.PRESENT),
                    assignmentStatus = FieldPatch.Set(ParticipationAssignmentStatus.PASSED),
                ),
                RecordUpdateCommand(
                    enrollmentId = 999999999L,
                    attendanceStatus = FieldPatch.Set(ParticipationAttendanceStatus.ABSENT),
                    assignmentStatus = FieldPatch.Omitted,
                ),
            )

            assertThrows(EnrollmentNotFoundException::class.java) {
                participationService.batchUpdateRecords(session, commands)
            }

            val saved = participationRepository.findByEnrollmentIdAndSessionId(enrollmentId, session.id!!)
            assertNull(saved)
        } finally {
            participationRepository.deleteAllByEnrollmentId(enrollmentId)
            enrollmentRepository.deleteById(enrollmentId)
        }
    }

    @Test
    fun `출석과 과제 수정 시 생략은 기존 값을 유지하고 null 전달 시 초기화되며 둘 다 null이면 기록이 삭제된다`() {
        val seminar = seminarRepository.findAll().firstOrNull()
            ?: return
        val session = sessionRepository.findAll().firstOrNull { it.seminarId == seminar.id }
            ?: return

        val enrollment = enrollmentRepository.save(
            Enrollment(
                graceDaysRemaining = seminar.totalGraceDays,
                createdAt = Instant.now(),
                rookieId = 999903L,
                seminarId = seminar.id!!,
            )
        )
        val enrollmentId = enrollment.id!!

        try {
            // 1. 출석·과제 최초 등록
            participationService.batchUpdateRecords(
                session,
                listOf(
                    RecordUpdateCommand(
                        enrollmentId = enrollmentId,
                        attendanceStatus = FieldPatch.Set(ParticipationAttendanceStatus.PRESENT),
                        assignmentStatus = FieldPatch.Set(ParticipationAssignmentStatus.PASSED),
                    )
                ),
            )

            // 2. 출석은 null(초기화), 과제는 생략(유지)
            participationService.batchUpdateRecords(
                session,
                listOf(
                    RecordUpdateCommand(
                        enrollmentId = enrollmentId,
                        attendanceStatus = FieldPatch.Clear,
                        assignmentStatus = FieldPatch.Omitted,
                    )
                ),
            )

            val afterPartialClear = participationRepository.findByEnrollmentIdAndSessionId(enrollmentId, session.id!!)
            assertNotNull(afterPartialClear)
            assertNull(afterPartialClear!!.attendanceStatus)
            assertEquals(ParticipationAssignmentStatus.PASSED, afterPartialClear.assignmentStatus)

            // 3. 남은 과제까지 null(초기화) -> 둘 다 null이므로 행 삭제
            participationService.batchUpdateRecords(
                session,
                listOf(
                    RecordUpdateCommand(
                        enrollmentId = enrollmentId,
                        attendanceStatus = FieldPatch.Omitted,
                        assignmentStatus = FieldPatch.Clear,
                    )
                ),
            )

            val afterFullClear = participationRepository.findByEnrollmentIdAndSessionId(enrollmentId, session.id!!)
            assertNull(afterFullClear)
        } finally {
            participationRepository.deleteAllByEnrollmentId(enrollmentId)
            enrollmentRepository.deleteById(enrollmentId)
        }
    }

    @Test
    fun `파서는 필드 생략과 null 전달을 정확히 구분한다`() {
        val json = objectMapper.readTree(
            """
            {
              "records": [
                {
                  "enrollmentId": 1,
                  "attendanceStatus": "PRESENT"
                },
                {
                  "enrollmentId": 2,
                  "attendanceStatus": null,
                  "assignmentStatus": "NOT_SUBMITTED"
                }
              ]
            }
            """.trimIndent()
        )

        val commands = batchUpdateRecordsParser.parse(json)

        assertEquals(2, commands.size)
        assertEquals(FieldPatch.Set(ParticipationAttendanceStatus.PRESENT), commands[0].attendanceStatus)
        assertEquals(FieldPatch.Omitted, commands[0].assignmentStatus)

        assertEquals(FieldPatch.Clear, commands[1].attendanceStatus)
        assertEquals(FieldPatch.Set(ParticipationAssignmentStatus.NOT_SUBMITTED), commands[1].assignmentStatus)
    }
}