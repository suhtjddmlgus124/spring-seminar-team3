package com.wafflestudio.spring2026.enrollment

import com.wafflestudio.spring2026.support.Week4ApiIntegrationTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EnrollmentManagementApiTest : Week4ApiIntegrationTest() {
    @Test
    fun `전달한 수강 정보만 수정하고 응답과 기존 조회에 반영한다`() {
        val seminarId = createSeminar(capacity = 1, totalGraceDays = 4)
        val sessionId = createSession(seminarId)
        val enrolled = enrolledRookie(seminarId)
        val admin = adminToken()
        val original = dashboardRow(dashboard(seminarId, admin), enrolled.enrollmentId)
        updateRecords(sessionId, admin, recordPatch(enrolled.enrollmentId, "attendance" to "PRESENT", "assignmentStatus" to "PASSED"))
            .andExpect { status { isNoContent() } }

        val graceResponse = responseJson(
            updateEnrollment(seminarId, enrolled.enrollmentId, admin, mapOf("graceDaysRemaining" to 0, "ignored" to true))
                .andExpect {
                    status { isOk() }
                    jsonPath("$.id") { value(enrolled.enrollmentId) }
                    jsonPath("$.graceDaysRemaining") { value(0) }
                    jsonPath("$.dropped") { value(false) }
                },
        )
        assertSameInstant(original.path("createdAt"), graceResponse.path("createdAt"))

        val dropResponse = responseJson(
            updateEnrollment(seminarId, enrolled.enrollmentId, admin, mapOf("dropped" to true)).andExpect {
                status { isOk() }
                jsonPath("$.id") { value(enrolled.enrollmentId) }
                jsonPath("$.graceDaysRemaining") { value(0) }
                jsonPath("$.dropped") { value(true) }
            },
        )
        assertSameInstant(original.path("createdAt"), dropResponse.path("createdAt"))

        val body = dashboard(seminarId, admin)
        val row = dashboardRow(body, enrolled.enrollmentId)
        assertEquals(0, row.path("graceDaysRemaining").asInt())
        assertEquals(true, row.path("dropped").asBoolean())
        assertSameInstant(original.path("createdAt"), row.path("createdAt"))
        assertRecord(body, enrolled.enrollmentId, sessionId, "PRESENT", "PASSED")
        val mine = myEnrollments(enrolled.actor).single()
        assertEquals(enrolled.enrollmentId, mine.path("id").asLong())
        assertEquals(0, mine.path("graceDaysRemaining").asInt())
        assertEquals(true, mine.path("dropped").asBoolean())
        assertSameInstant(original.path("createdAt"), mine.path("createdAt"))

        getAs(enrolled.actor.token, "/seminars/$seminarId").andExpect {
            status { isOk() }
            jsonPath("$.enrolledCount") { value(1) }
            jsonPath("$.status") { value("CLOSED") }
        }
        enroll(seminarId, approvedRookie().token).andExpect { status { isConflict() } }
        dashboard(seminarId, enrolled.actor.token)
        updateRecords(sessionId, admin, recordPatch(enrolled.enrollmentId, "attendance" to "ABSENT"))
            .andExpect { status { isNoContent() } }
        assertRecord(dashboard(seminarId, admin), enrolled.enrollmentId, sessionId, "ABSENT", "PASSED")

        repeat(2) {
            updateEnrollment(seminarId, enrolled.enrollmentId, admin, mapOf("graceDaysRemaining" to 4, "dropped" to false))
                .andExpect {
                    status { isOk() }
                    jsonPath("$.graceDaysRemaining") { value(4) }
                    jsonPath("$.dropped") { value(false) }
                }
        }
        val restored = dashboardRow(dashboard(seminarId, admin), enrolled.enrollmentId)
        assertEquals(4, restored.path("graceDaysRemaining").asInt())
        assertEquals(false, restored.path("dropped").asBoolean())
    }

    @Test
    fun `유효하지 않은 수강 정보 수정은 어느 필드도 반영하지 않는다`() {
        val seminarId = createSeminar(totalGraceDays = 3)
        val sessionId = createSession(seminarId)
        val enrolled = enrolledRookie(seminarId)
        val admin = adminToken()
        updateRecords(sessionId, admin, recordPatch(enrolled.enrollmentId, "attendance" to "ABSENT"))
            .andExpect { status { isNoContent() } }
        val before = operatingState(dashboard(seminarId, admin))
        val invalidBodies: List<Any> = listOf(
            emptyMap<String, Any?>(),
            mapOf("ignored" to true),
            mapOf("graceDaysRemaining" to -1, "dropped" to true),
            mapOf("graceDaysRemaining" to 4, "dropped" to true),
            mapOf("graceDaysRemaining" to 1.5),
            mapOf("graceDaysRemaining" to "invalid"),
            mapOf("graceDaysRemaining" to null, "dropped" to true),
            mapOf("graceDaysRemaining" to 0, "dropped" to null),
            mapOf("graceDaysRemaining" to 0, "dropped" to "invalid"),
            mapOf("dropped" to 1),
        )

        for (body in invalidBodies) {
            updateEnrollment(seminarId, enrolled.enrollmentId, admin, body)
                .andExpect { status { isBadRequest() } }
            assertEquals(before, operatingState(dashboard(seminarId, admin)), "실패한 요청이 부분 반영되었습니다: $body")
            val mine = myEnrollments(enrolled.actor).single()
            assertEquals(3, mine.path("graceDaysRemaining").asInt())
            assertEquals(false, mine.path("dropped").asBoolean())
        }
    }

    @Test
    fun `Grace Day 상한은 세미나마다 다르고 총량이 0인 세미나도 처리한다`() {
        val admin = adminToken()
        for (totalGraceDays in listOf(0, 5)) {
            val seminarId = createSeminar(totalGraceDays = totalGraceDays)
            val enrolled = enrolledRookie(seminarId)
            for (value in listOf(0, totalGraceDays).distinct()) {
                updateEnrollment(seminarId, enrolled.enrollmentId, admin, mapOf("graceDaysRemaining" to value))
                    .andExpect {
                        status { isOk() }
                        jsonPath("$.graceDaysRemaining") { value(value) }
                    }
            }
            updateEnrollment(seminarId, enrolled.enrollmentId, admin, mapOf("graceDaysRemaining" to totalGraceDays + 1))
                .andExpect { status { isBadRequest() } }
            assertEquals(totalGraceDays, dashboardRow(dashboard(seminarId, admin), enrolled.enrollmentId).path("graceDaysRemaining").asInt())
        }
    }

    @Test
    fun `다른 세미나의 수강 신청과 없는 리소스는 404이며 다른 수강 정보는 유지한다`() {
        val firstId = createSeminar(totalGraceDays = 3)
        val secondId = createSeminar(totalGraceDays = 5)
        val first = enrolledRookie(firstId)
        val sameUserSecondEnrollmentId = enrollSuccessfully(secondId, first.actor)
        val other = enrolledRookie(firstId)
        val admin = adminToken()

        updateEnrollment(firstId, first.enrollmentId, admin, mapOf("graceDaysRemaining" to 1, "dropped" to true))
            .andExpect { status { isOk() } }
        assertEquals(5, dashboardRow(dashboard(secondId, admin), sameUserSecondEnrollmentId).path("graceDaysRemaining").asInt())
        assertEquals(false, dashboardRow(dashboard(secondId, admin), sameUserSecondEnrollmentId).path("dropped").asBoolean())
        assertEquals(3, dashboardRow(dashboard(firstId, admin), other.enrollmentId).path("graceDaysRemaining").asInt())
        val beforeFirst = operatingState(dashboard(firstId, admin))
        val beforeSecond = operatingState(dashboard(secondId, admin))

        for ((seminarId, enrollmentId) in listOf(firstId to sameUserSecondEnrollmentId, firstId to MISSING_ID, MISSING_ID to first.enrollmentId)) {
            updateEnrollment(seminarId, enrollmentId, admin, mapOf("graceDaysRemaining" to 0, "dropped" to true))
                .andExpect { status { isNotFound() } }
            assertEquals(beforeFirst, operatingState(dashboard(firstId, admin)))
            assertEquals(beforeSecond, operatingState(dashboard(secondId, admin)))
        }
    }
}
