package com.wafflestudio.spring2026.seminar

import com.wafflestudio.spring2026.support.Week4ApiIntegrationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeminarDashboardApiTest : Week4ApiIntegrationTest() {
    @Test
    fun `빈 대시보드는 기본 페이지 정보와 빈 배열을 반환한다`() {
        val seminarId = createSeminar()
        val body = dashboard(seminarId, adminToken())

        assertEquals(seminarId, body.path("seminarId").asLong())
        assertEquals(0, body.path("page").asInt())
        assertEquals(20, body.path("size").asInt())
        assertEquals(0, body.path("totalElements").asInt())
        assertEquals(0, body.path("totalPages").asInt())
        assertEquals(0, body.path("sessions").size())
        assertEquals(0, body.path("content").size())
    }

    @Test
    fun `회차가 없어도 수강생을 반환하고 수강생이 없어도 전체 회차를 반환한다`() {
        val admin = adminToken()
        val noSessionsId = createSeminar()
        val enrolled = enrolledRookie(noSessionsId)
        val noSessions = dashboard(noSessionsId, admin)
        assertEquals(0, noSessions.path("sessions").size())
        assertEquals(0, dashboardRow(noSessions, enrolled.enrollmentId).path("records").size())

        val noStudentsId = createSeminar()
        val sessionId = createSession(noStudentsId)
        val noStudents = dashboard(noStudentsId, admin, "?page=3&size=1")
        assertEquals(listOf(sessionId), noStudents.path("sessions").toList().map { it.path("id").asLong() })
        assertEquals(0, noStudents.path("content").size())
        assertEquals(0, noStudents.path("totalElements").asInt())
        assertEquals(0, noStudents.path("totalPages").asInt())
    }

    @Test
    fun `수강 신청 ID와 사용자 정보를 구분하고 모든 회차의 미기록을 null로 반환한다`() {
        val seminarId = createSeminar(totalGraceDays = 4)
        val firstSessionId = createSession(seminarId, startsAt = now().plusDays(1))
        val secondSessionId = createSession(seminarId, startsAt = now().plusDays(2))
        val enrolled = enrolledRookie(seminarId)
        val admin = adminToken()
        val expectedUser = responseJson(
            getAs(admin, "/users/${enrolled.actor.id}").andExpect { status { isOk() } },
        )
        val expectedEnrollment = myEnrollments(enrolled.actor).single()

        val body = dashboard(seminarId, admin)
        val row = dashboardRow(body, enrolled.enrollmentId)
        assertEquals(enrolled.actor.id, row.path("user").path("id").asLong())
        for (field in listOf("email", "name", "githubUsername")) {
            assertEquals(expectedUser.path(field).asString(), row.path("user").path(field).asString())
        }
        assertEquals(4, row.path("graceDaysRemaining").asInt())
        assertTrue(row.path("dropped").isBoolean)
        assertEquals(false, row.path("dropped").asBoolean())
        assertSameInstant(expectedEnrollment.path("createdAt"), row.path("createdAt"))
        assertEquals(listOf(firstSessionId, secondSessionId), row.path("records").toList().map { it.path("sessionId").asLong() })
        assertRecord(body, enrolled.enrollmentId, firstSessionId, null, null)
        assertRecord(body, enrolled.enrollmentId, secondSessionId, null, null)
    }

    @Test
    fun `회차는 시작 시각과 ID 순으로 정렬하고 순번과 내용을 기존 회차 조회와 맞춘다`() {
        val seminarId = createSeminar()
        val start = now().plusDays(1)
        val laterId = createSession(seminarId, startsAt = start.plusDays(1), title = "Later")
        val earlierId = createSession(seminarId, startsAt = start, title = "Earlier")
        val tiedId = createSession(seminarId, startsAt = start, title = "Tied")
        val otherSeminarId = createSeminar()
        createSession(otherSeminarId, startsAt = start.minusDays(1))
        val enrolled = enrolledRookie(seminarId)
        val admin = adminToken()

        val body = dashboard(seminarId, admin)
        val expectedIds = listOf(earlierId, tiedId).sorted() + laterId
        assertEquals(expectedIds, body.path("sessions").toList().map { it.path("id").asLong() })
        assertEquals(expectedIds, dashboardRow(body, enrolled.enrollmentId).path("records").toList().map { it.path("sessionId").asLong() })
        body.path("sessions").toList().forEachIndexed { index, session ->
            val expected = responseJson(
                getAs(admin, "/sessions/${session.path("id").asLong()}").andExpect { status { isOk() } },
            )
            assertEquals(index + 1, session.path("round").asInt())
            assertEquals(expected.path("round").asInt(), session.path("round").asInt())
            assertEquals(expected.path("title").asString(), session.path("title").asString())
            assertEquals(expected.path("assignmentTitle").asString(), session.path("assignmentTitle").asString())
            assertSameInstant(expected.path("startsAt"), session.path("startsAt"))
        }
    }

    @Test
    fun `수강생 행만 페이지로 나누고 탈락한 수강생과 전체 회차를 포함한다`() {
        val seminarId = createSeminar()
        val sessionId = createSession(seminarId)
        val students = List(5) { enrolledRookie(seminarId) }
        val admin = adminToken()
        val dropped = students[2]
        updateEnrollment(seminarId, dropped.enrollmentId, admin, mapOf("dropped" to true))
            .andExpect { status { isOk() } }
        updateRecords(sessionId, admin, recordPatch(dropped.enrollmentId, "attendance" to "PRESENT"))
            .andExpect { status { isNoContent() } }

        val expectedIds = students.map { it.enrollmentId }.sorted()
        val seenIds = mutableListOf<Long>()
        for (page in 0..3) {
            val body = dashboard(seminarId, admin, "?page=$page&size=2")
            val actualIds = body.path("content").toList().map { it.path("id").asLong() }
            assertEquals(expectedIds.drop(page * 2).take(2), actualIds)
            assertEquals(page, body.path("page").asInt())
            assertEquals(2, body.path("size").asInt())
            assertEquals(5, body.path("totalElements").asInt())
            assertEquals(3, body.path("totalPages").asInt())
            assertEquals(listOf(sessionId), body.path("sessions").toList().map { it.path("id").asLong() })
            for (row in body.path("content")) {
                assertEquals(listOf(sessionId), row.path("records").toList().map { it.path("sessionId").asLong() })
            }
            if (dropped.enrollmentId in actualIds) {
                assertEquals(true, dashboardRow(body, dropped.enrollmentId).path("dropped").asBoolean())
                assertRecord(body, dropped.enrollmentId, sessionId, "PRESENT", null)
            }
            seenIds += actualIds
        }
        assertEquals(expectedIds, seenIds)
        assertEquals(expectedIds, dashboard(seminarId, admin, "?size=100").path("content").toList().map { it.path("id").asLong() })
    }

    @Test
    fun `회차 일시 변경으로 순번이 달라져도 기록은 회차 ID를 따라간다`() {
        val seminarId = createSeminar()
        val start = now().plusDays(1)
        val firstId = createSession(seminarId, startsAt = start)
        val secondId = createSession(seminarId, startsAt = start.plusDays(1))
        val enrolled = enrolledRookie(seminarId)
        val admin = adminToken()
        updateRecords(firstId, admin, recordPatch(enrolled.enrollmentId, "attendance" to "PRESENT", "assignmentStatus" to "PASSED"))
            .andExpect { status { isNoContent() } }
        updateRecords(secondId, admin, recordPatch(enrolled.enrollmentId, "attendance" to "ABSENT", "assignmentStatus" to "NOT_SUBMITTED"))
            .andExpect { status { isNoContent() } }

        patchAs(admin, "/sessions/$firstId", mapOf("startsAt" to start.plusDays(2).toString()))
            .andExpect { status { isOk() } }

        val body = dashboard(seminarId, admin)
        assertEquals(listOf(secondId, firstId), body.path("sessions").toList().map { it.path("id").asLong() })
        assertEquals(listOf(1, 2), body.path("sessions").toList().map { it.path("round").asInt() })
        assertEquals(listOf(secondId, firstId), dashboardRow(body, enrolled.enrollmentId).path("records").toList().map { it.path("sessionId").asLong() })
        assertRecord(body, enrolled.enrollmentId, firstId, "PRESENT", "PASSED")
        assertRecord(body, enrolled.enrollmentId, secondId, "ABSENT", "NOT_SUBMITTED")
    }

    @Test
    fun `잘못된 페이지 요청은 400이고 없는 세미나는 404다`() {
        val seminarId = createSeminar()
        val admin = adminToken()
        for (query in listOf("page=-1", "page=abc", "page=1.5", "size=0", "size=-1", "size=101", "size=abc", "size=1.5")) {
            getAs(admin, "/seminars/$seminarId/dashboard?$query").andExpect { status { isBadRequest() } }
        }
        getAs(admin, "/seminars/$MISSING_ID/dashboard").andExpect { status { isNotFound() } }
    }
}
