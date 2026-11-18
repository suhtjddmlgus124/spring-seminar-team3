package com.wafflestudio.spring2026.auth

import com.wafflestudio.spring2026.support.Week4ApiIntegrationTest
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OperationsEnrollmentPeriodApiTest : Week4ApiIntegrationTest() {
    @Test
    fun `신청 기간 종료 후 신청과 취소는 거절하고 담당 운영진과 와장의 운영 정보 수정은 허용한다`() {
        val admin = adminToken()
        val rookie = approvedRookie()
        val applicant = approvedRookie()

        // 공개 API로 신청할 준비 시간을 확보한 뒤 실제 종료 시각을 기다린다.
        // 특정 Clock 빈이나 DB 테이블 구조를 요구하지 않으며 대기는 약 30초다.
        val closesAt = now().plusSeconds(30)
        val seminarId = createSeminar(
            capacity = 10,
            totalGraceDays = 3,
            applyStartAt = now().minusDays(1),
            applyEndAt = closesAt,
        )
        val enrollmentId = enrollSuccessfully(seminarId, rookie)
        val staff = approvedStaff(seminarId)
        val sessionId = createSession(seminarId)
        val before = operatingState(dashboard(seminarId, admin))

        awaitPeriodEnd(closesAt.toInstant())

        getAs(admin, "/seminars/$seminarId").andExpect {
            status { isOk() }
            jsonPath("$.enrolledCount") { value(1) }
            jsonPath("$.status") { value("CLOSED") }
        }
        enroll(seminarId, applicant.token).andExpect { status { isConflict() } }
        cancelEnrollment(seminarId, rookie.token).andExpect { status { isConflict() } }
        assertEquals(before, operatingState(dashboard(seminarId, admin)))

        for ((index, token) in listOf(staff.token, admin).withIndex()) {
            val graceDaysRemaining = index + 1
            val dropped = index == 0
            val attendance = if (index == 0) "PRESENT" else "ABSENT"
            val assignmentStatus = if (index == 0) "PASSED" else "NOT_SUBMITTED"

            updateEnrollment(
                seminarId, enrollmentId, token,
                mapOf("graceDaysRemaining" to graceDaysRemaining, "dropped" to dropped),
            ).andExpect {
                status { isOk() }
                jsonPath("$.id") { value(enrollmentId) }
                jsonPath("$.graceDaysRemaining") { value(graceDaysRemaining) }
                jsonPath("$.dropped") { value(dropped) }
            }
            updateRecords(
                sessionId, token,
                recordPatch(enrollmentId, "attendance" to attendance, "assignmentStatus" to assignmentStatus),
            ).andExpect {
                status { isNoContent() }
                content { string("") }
            }

            val saved = dashboard(seminarId, rookie.token)
            val row = dashboardRow(saved, enrollmentId)
            assertEquals(1, saved.path("totalElements").asInt())
            assertEquals(graceDaysRemaining, row.path("graceDaysRemaining").asInt())
            assertEquals(dropped, row.path("dropped").asBoolean())
            assertRecord(saved, enrollmentId, sessionId, attendance, assignmentStatus)
            val mine = myEnrollments(rookie).single()
            assertEquals(graceDaysRemaining, mine.path("graceDaysRemaining").asInt())
            assertEquals(dropped, mine.path("dropped").asBoolean())
            assertEquals(1, mine.path("seminar").path("enrolledCount").asInt())
            assertEquals("CLOSED", mine.path("seminar").path("status").asString())
        }
    }

    private fun awaitPeriodEnd(closesAt: Instant) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45)
        val waiter = CountDownLatch(1)
        while (Instant.now().isBefore(closesAt)) {
            val remaining = Duration.between(Instant.now(), closesAt).toNanos()
            val budget = deadline - System.nanoTime()
            assertTrue(budget > 0, "신청 종료 시각까지 대기하는 제한 시간을 초과했습니다.")
            waiter.await(minOf(maxOf(1L, remaining), budget), TimeUnit.NANOSECONDS)
        }
    }
}
