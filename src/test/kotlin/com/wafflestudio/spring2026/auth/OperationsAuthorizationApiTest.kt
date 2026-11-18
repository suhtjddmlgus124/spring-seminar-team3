package com.wafflestudio.spring2026.auth

import com.wafflestudio.spring2026.support.Week4ApiIntegrationTest
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

class OperationsAuthorizationApiTest : Week4ApiIntegrationTest() {
    @Test
    fun `와장과 담당 운영진은 모든 운영 API를 사용하고 수강생은 다른 수강생의 현황도 조회한다`() {
        val seminarId = createSeminar()
        val sessionId = createSession(seminarId)
        val first = enrolledRookie(seminarId)
        val other = enrolledRookie(seminarId)
        val staff = approvedStaff(seminarId)
        val admin = adminToken()

        for ((index, token) in listOf(admin, staff.token).withIndex()) {
            val graceDaysRemaining = index + 1
            val dropped = index == 0
            val attendance = if (index == 0) "PRESENT" else "ABSENT"
            val assignmentStatus = if (index == 0) "PASSED" else "NOT_SUBMITTED"
            dashboard(seminarId, token)
            updateEnrollment(seminarId, first.enrollmentId, token, mapOf("graceDaysRemaining" to graceDaysRemaining, "dropped" to dropped))
                .andExpect { status { isOk() } }
            updateRecords(sessionId, token, recordPatch(first.enrollmentId, "attendance" to attendance, "assignmentStatus" to assignmentStatus))
                .andExpect { status { isNoContent() } }

            val saved = dashboard(seminarId, admin)
            val row = dashboardRow(saved, first.enrollmentId)
            assertEquals(graceDaysRemaining, row.path("graceDaysRemaining").asInt())
            assertEquals(dropped, row.path("dropped").asBoolean())
            assertRecord(saved, first.enrollmentId, sessionId, attendance, assignmentStatus)
            val mine = myEnrollments(first.actor).single()
            assertEquals(graceDaysRemaining, mine.path("graceDaysRemaining").asInt())
            assertEquals(dropped, mine.path("dropped").asBoolean())
        }

        val body = dashboard(seminarId, first.actor.token)
        assertEquals(setOf(first.enrollmentId, other.enrollmentId), body.path("content").toList().map { it.path("id").asLong() }.toSet())
        assertEquals(other.actor.id, dashboardRow(body, other.enrollmentId).path("user").path("id").asLong())
        assertEquals(false, dashboardRow(body, first.enrollmentId).path("dropped").asBoolean())
        assertRecord(body, first.enrollmentId, sessionId, "ABSENT", "NOT_SUBMITTED")

        val before = operatingState(body)
        for (student in listOf(first, other)) {
            for (target in listOf(first, other)) {
                updateEnrollment(seminarId, target.enrollmentId, student.actor.token, mapOf("graceDaysRemaining" to 0))
                    .andExpect { status { isForbidden() } }
                updateRecords(sessionId, student.actor.token, recordPatch(target.enrollmentId, "attendance" to "PRESENT"))
                    .andExpect { status { isForbidden() } }
            }
        }
        assertEquals(before, operatingState(dashboard(seminarId, admin)))
    }

    @Test
    fun `다른 운영진과 미수강 루키 및 미승인 사용자는 모든 운영 API에서 403을 받는다`() {
        val seminarId = createSeminar()
        val sessionId = createSession(seminarId)
        val enrolled = enrolledRookie(seminarId)
        val admin = adminToken()
        val otherStaff = approvedStaff(createSeminar())
        val rejectedStaffEmail = uniqueEmail()
        val rejectedStaffId = signupStaff(seminarId, rejectedStaffEmail)
        approve(rejectedStaffId, "REJECTED", admin).andExpect { status { isOk() } }
        val rejectedStaffToken = login(rejectedStaffEmail)
        val forbiddenTokens = listOf(
            otherStaff.token,
            approvedRookie().token,
            pendingStaff(seminarId).token,
            rejectedStaffToken,
            pendingRookie().token,
            rejectedRookie().token,
        )
        val before = operatingState(dashboard(seminarId, admin))

        for (token in forbiddenTokens) {
            getAs(token, "/seminars/$seminarId/dashboard").andExpect { status { isForbidden() } }
            updateEnrollment(seminarId, enrolled.enrollmentId, token, mapOf("graceDaysRemaining" to 0, "dropped" to true))
                .andExpect { status { isForbidden() } }
            updateRecords(sessionId, token, recordPatch(enrolled.enrollmentId, "attendance" to "PRESENT"))
                .andExpect { status { isForbidden() } }
            assertEquals(before, operatingState(dashboard(seminarId, admin)))
        }
    }

    @Test
    fun `새 엔드포인트도 누락 형식 오류 변조 토큰을 401로 처리한다`() {
        val seminarId = createSeminar()
        val sessionId = createSession(seminarId)
        val enrolled = enrolledRookie(seminarId)
        val staff = approvedStaff(seminarId)
        val invalidSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32))
        val forged = staff.token.substringBeforeLast('.') + "." + invalidSignature
        val before = operatingState(dashboard(seminarId, enrolled.actor.token))

        for (token in listOf(null, "not-a-jwt", forged)) {
            assertOperationsUnauthorized(token, seminarId, enrolled.enrollmentId, sessionId)
        }

        mockMvc.get("/seminars/$seminarId/dashboard") {
            header(HttpHeaders.AUTHORIZATION, "Token ${enrolled.actor.token}")
        }.andExpect { status { isUnauthorized() } }
        mockMvc.patch("/seminars/$seminarId/enrollments/${enrolled.enrollmentId}") {
            header(HttpHeaders.AUTHORIZATION, "Token ${enrolled.actor.token}")
            json(mapOf("dropped" to true))
        }.andExpect { status { isUnauthorized() } }
        mockMvc.patch("/sessions/$sessionId/records") {
            header(HttpHeaders.AUTHORIZATION, "Token ${enrolled.actor.token}")
            json(mapOf("records" to listOf(recordPatch(enrolled.enrollmentId, "attendance" to "PRESENT"))))
        }.andExpect { status { isUnauthorized() } }
        assertEquals(before, operatingState(dashboard(seminarId, enrolled.actor.token)))
    }

    @Test
    fun `로그아웃하지 않은 사용자의 만료 토큰도 운영 API에서 401을 받는다`() {
        val seminarId = createSeminar()
        val sessionId = createSession(seminarId)
        val enrolled = enrolledRookie(seminarId)
        val staff = approvedStaff(seminarId)
        val current = Instant.now()
        val expired = resign(staff.token, current.minusSeconds(7200), current.minusSeconds(3600))
        val before = operatingState(dashboard(seminarId, staff.token))

        assertOperationsUnauthorized(expired, seminarId, enrolled.enrollmentId, sessionId)

        // 원본은 무효화하지 않았으므로 같은 사용자의 유효한 토큰은 계속 사용할 수 있다.
        assertEquals(before, operatingState(dashboard(seminarId, staff.token)))
    }

    @Test
    fun `만료 전 로그아웃한 토큰도 운영 API에서 401을 받는다`() {
        val seminarId = createSeminar()
        val sessionId = createSession(seminarId)
        val enrolled = enrolledRookie(seminarId)
        val staff = approvedStaff(seminarId)
        val admin = adminToken()
        val before = operatingState(dashboard(seminarId, staff.token))

        logout(staff.token).andExpect { status { isNoContent() } }
        assertOperationsUnauthorized(staff.token, seminarId, enrolled.enrollmentId, sessionId)

        assertEquals(before, operatingState(dashboard(seminarId, admin)))
    }

    @Test
    fun `인증 실패는 잘못된 본문과 없는 리소스보다 먼저 판단한다`() {
        getAs(null, "/seminars/$MISSING_ID/dashboard?page=-1").andExpect { status { isUnauthorized() } }
        patchAs(null, "/seminars/$MISSING_ID/enrollments/$MISSING_ID", "{")
            .andExpect { status { isUnauthorized() } }
        patchAs(null, "/sessions/$MISSING_ID/records", "{")
            .andExpect { status { isUnauthorized() } }
    }

    private fun assertOperationsUnauthorized(token: String?, seminarId: Long, enrollmentId: Long, sessionId: Long) {
        getAs(token, "/seminars/$seminarId/dashboard").andExpect { status { isUnauthorized() } }
        patchOperation(token, "/seminars/$seminarId/enrollments/$enrollmentId", mapOf("dropped" to true))
            .andExpect { status { isUnauthorized() } }
        patchOperation(token, "/sessions/$sessionId/records", mapOf("records" to listOf(recordPatch(enrollmentId, "attendance" to "PRESENT"))))
            .andExpect { status { isUnauthorized() } }
    }
}
