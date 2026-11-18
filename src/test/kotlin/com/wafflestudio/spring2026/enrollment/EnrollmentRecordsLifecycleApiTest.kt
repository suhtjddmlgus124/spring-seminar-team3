package com.wafflestudio.spring2026.enrollment

import com.wafflestudio.spring2026.support.Week4ApiIntegrationTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EnrollmentRecordsLifecycleApiTest : Week4ApiIntegrationTest() {
    @Test
    fun `취소하면 본인 기록을 정리하고 재신청은 초기 상태로 시작한다`() {
        val seminarId = createSeminar(capacity = 2, totalGraceDays = 4)
        val firstSessionId = createSession(seminarId, startsAt = now().plusDays(1))
        val secondSessionId = createSession(seminarId, startsAt = now().plusDays(2))
        val first = enrolledRookie(seminarId)
        val other = enrolledRookie(seminarId)
        val admin = adminToken()
        updateEnrollment(seminarId, first.enrollmentId, admin, mapOf("graceDaysRemaining" to 0, "dropped" to true))
            .andExpect { status { isOk() } }
        for (sessionId in listOf(firstSessionId, secondSessionId)) {
            updateRecords(
                sessionId, admin,
                recordPatch(first.enrollmentId, "attendance" to "ABSENT", "assignmentStatus" to "NOT_SUBMITTED"),
                recordPatch(other.enrollmentId, "attendance" to "PRESENT", "assignmentStatus" to "PASSED"),
            ).andExpect { status { isNoContent() } }
        }
        val otherBefore = operatingState(dashboard(seminarId, admin)).getValue(other.enrollmentId)

        cancelEnrollment(seminarId, first.actor.token).andExpect {
            status { isNoContent() }
            content { string("") }
        }

        val afterCancel = dashboard(seminarId, admin)
        assertEquals(listOf(other.enrollmentId), afterCancel.path("content").toList().map { it.path("id").asLong() })
        assertEquals(otherBefore, operatingState(afterCancel).getValue(other.enrollmentId))
        assertEquals(listOf(firstSessionId, secondSessionId), afterCancel.path("sessions").toList().map { it.path("id").asLong() })
        assertEquals(0, myEnrollments(first.actor).size)
        getAs(first.actor.token, "/seminars/$seminarId/dashboard").andExpect { status { isForbidden() } }
        getAs(admin, "/seminars/$seminarId").andExpect {
            status { isOk() }
            jsonPath("$.enrolledCount") { value(1) }
            jsonPath("$.status") { value("OPEN") }
        }
        cancelEnrollment(seminarId, first.actor.token).andExpect { status { isNotFound() } }
        updateEnrollment(seminarId, first.enrollmentId, admin, mapOf("dropped" to false))
            .andExpect { status { isNotFound() } }
        updateRecords(firstSessionId, admin, recordPatch(first.enrollmentId, "attendance" to "PRESENT"))
            .andExpect { status { isNotFound() } }

        val newEnrollmentId = enrollSuccessfully(seminarId, first.actor)
        val afterReenroll = dashboard(seminarId, first.actor.token)
        val row = dashboardRow(afterReenroll, newEnrollmentId)
        assertEquals(4, row.path("graceDaysRemaining").asInt())
        assertEquals(false, row.path("dropped").asBoolean())
        for (sessionId in listOf(firstSessionId, secondSessionId)) {
            assertRecord(afterReenroll, newEnrollmentId, sessionId, null, null)
        }
        assertEquals(otherBefore, operatingState(afterReenroll).getValue(other.enrollmentId))
        assertEquals(2, afterReenroll.path("totalElements").asInt())
        assertEquals(newEnrollmentId, myEnrollments(first.actor).single().path("id").asLong())
        enroll(seminarId, approvedRookie().token).andExpect { status { isConflict() } }
    }
}
