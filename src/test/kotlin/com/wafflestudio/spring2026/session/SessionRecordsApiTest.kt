package com.wafflestudio.spring2026.session

import com.wafflestudio.spring2026.support.Week4ApiIntegrationTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionRecordsApiTest : Week4ApiIntegrationTest() {
    @Test
    fun `여러 수강생의 기록을 저장하고 다른 수강생과 회차 및 운영 정보를 유지한다`() {
        val seminarId = createSeminar(totalGraceDays = 4)
        val sessionId = createSession(seminarId, startsAt = now().plusDays(1))
        val otherSessionId = createSession(seminarId, startsAt = now().plusDays(2))
        val first = enrolledRookie(seminarId)
        val second = enrolledRookie(seminarId)
        val untouched = enrolledRookie(seminarId)
        val admin = adminToken()
        updateEnrollment(seminarId, first.enrollmentId, admin, mapOf("graceDaysRemaining" to 2, "dropped" to true))
            .andExpect { status { isOk() } }
        updateRecords(otherSessionId, admin, recordPatch(first.enrollmentId, "attendance" to "ABSENT", "assignmentStatus" to "NOT_SUBMITTED"))
            .andExpect { status { isNoContent() } }

        patchOperation(
            admin, "/sessions/$sessionId/records",
            mapOf(
                "ignored" to true,
                "records" to listOf(
                    recordPatch(first.enrollmentId, "attendance" to "PRESENT", "assignmentStatus" to "PASSED", "ignored" to true),
                    recordPatch(second.enrollmentId, "attendance" to "ABSENT", "assignmentStatus" to "SUBMITTED_BUT_FAILED"),
                ),
            ),
        ).andExpect {
            status { isNoContent() }
            content { string("") }
        }

        val body = dashboard(seminarId, admin)
        assertRecord(body, first.enrollmentId, sessionId, "PRESENT", "PASSED")
        assertRecord(body, second.enrollmentId, sessionId, "ABSENT", "SUBMITTED_BUT_FAILED")
        assertRecord(body, untouched.enrollmentId, sessionId, null, null)
        assertRecord(body, first.enrollmentId, otherSessionId, "ABSENT", "NOT_SUBMITTED")
        assertRecord(body, second.enrollmentId, otherSessionId, null, null)
        assertRecord(body, untouched.enrollmentId, otherSessionId, null, null)
        val firstRow = dashboardRow(body, first.enrollmentId)
        assertEquals(2, firstRow.path("graceDaysRemaining").asInt())
        assertEquals(true, firstRow.path("dropped").asBoolean())
        for (student in listOf(second, untouched)) {
            val row = dashboardRow(body, student.enrollmentId)
            assertEquals(4, row.path("graceDaysRemaining").asInt())
            assertEquals(false, row.path("dropped").asBoolean())
        }
    }

    @Test
    fun `생략과 null을 구분하고 같은 값의 재저장과 이미 빈 기록의 삭제를 허용한다`() {
        val seminarId = createSeminar()
        val sessionId = createSession(seminarId)
        val enrolled = enrolledRookie(seminarId)
        val admin = adminToken()

        fun change(vararg fields: Pair<String, Any?>) {
            updateRecords(sessionId, admin, recordPatch(enrolled.enrollmentId, *fields)).andExpect {
                status { isNoContent() }
                content { string("") }
            }
        }
        fun expect(attendance: String?, assignmentStatus: String?) {
            val body = dashboard(seminarId, admin)
            assertRecord(body, enrolled.enrollmentId, sessionId, attendance, assignmentStatus)
            assertEquals(1, dashboardRow(body, enrolled.enrollmentId).path("records").size())
            assertEquals(3, dashboardRow(body, enrolled.enrollmentId).path("graceDaysRemaining").asInt())
        }

        change("attendance" to "PRESENT")
        expect("PRESENT", null)
        change("assignmentStatus" to "NOT_SUBMITTED")
        expect("PRESENT", "NOT_SUBMITTED")
        change("attendance" to null)
        expect(null, "NOT_SUBMITTED")
        change("assignmentStatus" to null)
        expect(null, null)
        change("attendance" to null, "assignmentStatus" to null)
        expect(null, null)
        repeat(2) {
            change("attendance" to "ABSENT", "assignmentStatus" to "SUBMITTED_BUT_FAILED")
            expect("ABSENT", "SUBMITTED_BUT_FAILED")
        }
        change("assignmentStatus" to "PASSED")
        expect("ABSENT", "PASSED")
        change("attendance" to "PRESENT")
        expect("PRESENT", "PASSED")
        change("attendance" to null, "assignmentStatus" to null)
        expect(null, null)
    }

    @Test
    fun `일괄 수정의 형식과 상태값 오류는 400이며 모든 기존 기록을 유지한다`() {
        val seminarId = createSeminar()
        val sessionId = createSession(seminarId)
        val first = enrolledRookie(seminarId)
        val second = enrolledRookie(seminarId)
        val admin = adminToken()
        updateRecords(sessionId, admin, recordPatch(first.enrollmentId, "attendance" to "PRESENT", "assignmentStatus" to "NOT_SUBMITTED"))
            .andExpect { status { isNoContent() } }
        val before = operatingState(dashboard(seminarId, admin))
        val invalidRecords: List<Any?> = listOf(
            null,
            "invalid",
            emptyMap<String, Any?>(),
            mapOf("attendance" to "PRESENT"),
            recordPatch(first.enrollmentId),
            recordPatch(first.enrollmentId, "ignored" to true),
            mapOf("enrollmentId" to null, "attendance" to "PRESENT"),
            mapOf("enrollmentId" to 0, "attendance" to "PRESENT"),
            mapOf("enrollmentId" to -1, "attendance" to "PRESENT"),
            mapOf("enrollmentId" to 1.5, "attendance" to "PRESENT"),
            mapOf("enrollmentId" to "invalid", "attendance" to "PRESENT"),
            recordPatch(first.enrollmentId, "attendance" to "LATE"),
            recordPatch(first.enrollmentId, "attendance" to "present"),
            recordPatch(first.enrollmentId, "attendance" to 1),
            recordPatch(first.enrollmentId, "assignmentStatus" to "FAILED"),
            recordPatch(first.enrollmentId, "assignmentStatus" to false),
        )
        val invalidBodies: List<Any> = listOf(
            emptyMap<String, Any?>(),
            mapOf("ignored" to true),
            mapOf("records" to null),
            mapOf("records" to emptyList<Any>()),
            mapOf("records" to "invalid"),
            mapOf("records" to emptyMap<String, Any?>()),
            mapOf("records" to listOf(
                recordPatch(first.enrollmentId, "attendance" to "ABSENT"),
                recordPatch(first.enrollmentId, "assignmentStatus" to "PASSED"),
            )),
            mapOf("records" to listOf(
                recordPatch(first.enrollmentId, "attendance" to null),
                recordPatch(second.enrollmentId, "assignmentStatus" to "INVALID"),
            )),
        ) + invalidRecords.map { mapOf("records" to listOf(it)) }

        for (body in invalidBodies) {
            patchOperation(admin, "/sessions/$sessionId/records", body).andExpect { status { isBadRequest() } }
            assertEquals(before, operatingState(dashboard(seminarId, admin)), "실패한 요청의 기록이 반영되었습니다: $body")
        }
    }

    @Test
    fun `없는 수강 신청이나 다른 세미나의 수강 신청이 섞이면 삭제와 신규 저장도 모두 반영하지 않는다`() {
        val seminarId = createSeminar()
        val sessionId = createSession(seminarId)
        val first = enrolledRookie(seminarId)
        val second = enrolledRookie(seminarId)
        val otherSeminarId = createSeminar()
        val otherSessionId = createSession(otherSeminarId)
        val foreignEnrollmentId = enrollSuccessfully(otherSeminarId, first.actor)
        val admin = adminToken()
        updateRecords(sessionId, admin, recordPatch(first.enrollmentId, "attendance" to "PRESENT", "assignmentStatus" to "PASSED"))
            .andExpect { status { isNoContent() } }
        updateRecords(otherSessionId, admin, recordPatch(foreignEnrollmentId, "attendance" to "ABSENT", "assignmentStatus" to "NOT_SUBMITTED"))
            .andExpect { status { isNoContent() } }
        val before = operatingState(dashboard(seminarId, admin))
        val foreignBefore = operatingState(dashboard(otherSeminarId, admin))
        val validChanges = listOf(
            recordPatch(first.enrollmentId, "attendance" to null, "assignmentStatus" to null),
            recordPatch(second.enrollmentId, "attendance" to "ABSENT", "assignmentStatus" to "SUBMITTED_BUT_FAILED"),
        )

        for (badId in listOf(MISSING_ID, foreignEnrollmentId)) {
            val invalid = recordPatch(badId, "attendance" to "PRESENT")
            for (records in listOf(validChanges + invalid, listOf(invalid) + validChanges)) {
                patchOperation(admin, "/sessions/$sessionId/records", mapOf("records" to records))
                    .andExpect { status { isNotFound() } }
                assertEquals(before, operatingState(dashboard(seminarId, admin)))
                assertEquals(foreignBefore, operatingState(dashboard(otherSeminarId, admin)))
            }
        }
        updateRecords(MISSING_ID, admin, recordPatch(first.enrollmentId, "attendance" to "PRESENT"))
            .andExpect { status { isNotFound() } }
        assertEquals(before, operatingState(dashboard(seminarId, admin)))

        updateRecords(sessionId, admin, *validChanges.toTypedArray()).andExpect { status { isNoContent() } }
        val recovered = dashboard(seminarId, admin)
        assertRecord(recovered, first.enrollmentId, sessionId, null, null)
        assertRecord(recovered, second.enrollmentId, sessionId, "ABSENT", "SUBMITTED_BUT_FAILED")
        assertEquals(foreignBefore, operatingState(dashboard(otherSeminarId, admin)))
    }
}
