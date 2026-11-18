package com.wafflestudio.spring2026.enrollment

import com.wafflestudio.spring2026.support.Week4ApiIntegrationTest
import com.wafflestudio.spring2026.support.runConcurrently
import org.springframework.mock.web.MockHttpServletResponse
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnrollmentConcurrencyApiTest : Week4ApiIntegrationTest() {
    private data class Operation(val actor: Actor, val cancel: Boolean)

    @Test
    fun `서로 다른 동시 신청은 남은 정원만큼 정확히 성공하고 모든 조회가 일치한다`() {
        val admin = adminToken()
        val rookies = approvedActors(100, admin)

        for ((capacity, requests) in listOf(1 to 8, 5 to 20, 30 to 100, 105 to 5)) {
            val seminarId = createSeminar(capacity = capacity)
            val participants = rookies.take(requests)
            val results = runConcurrently(participants) { actor ->
                actor to enroll(seminarId, actor.token).andReturn().response
            }
            val successCount = minOf(capacity, requests)
            assertStatuses(
                results.map { it.second },
                mapOf(201 to successCount, 409 to requests - successCount).filterValues { it > 0 },
            )
            val expected = results.filter { it.second.status == 201 }.associate { (actor, response) ->
                actor.id to createdEnrollmentId(response)
            }
            assertState(seminarId, capacity, participants, expected, admin)
        }
    }

    @Test
    fun `마지막 빈자리 경쟁은 기존 수강생을 유지하고 남은 자리만 채운다`() {
        val admin = adminToken()
        val seminarId = createSeminar(capacity = 3)
        val actors = approvedActors(12, admin)
        val expected = actors.take(2).associate { it.id to enrollSuccessfully(seminarId, it) }.toMutableMap()

        val results = runConcurrently(actors.drop(2)) { actor ->
            actor to enroll(seminarId, actor.token).andReturn().response
        }

        assertStatuses(results.map { it.second }, mapOf(201 to 1, 409 to 9))
        val (winner, response) = results.single { it.second.status == 201 }
        expected[winner.id] = createdEnrollmentId(response)
        assertState(seminarId, 3, actors, expected, admin)
    }

    @Test
    fun `동일 사용자의 동시 신청은 한 번만 성공하고 실패가 빈자리를 소비하지 않는다`() {
        val admin = adminToken()
        val seminarId = createSeminar(capacity = 3)
        val actors = approvedActors(4, admin)
        val first = actors.first()

        val responses = runConcurrently(List(20) { first }) { actor ->
            enroll(seminarId, actor.token).andReturn().response
        }

        assertStatuses(responses, mapOf(201 to 1, 409 to 19))
        val expected = mutableMapOf(first.id to createdEnrollmentId(responses.single { it.status == 201 }))
        assertState(seminarId, 3, actors, expected, admin)
        for (actor in actors.drop(1).take(2)) {
            expected[actor.id] = enrollSuccessfully(seminarId, actor)
        }
        enroll(seminarId, actors.last().token).andExpect { status { isConflict() } }
        assertState(seminarId, 3, actors, expected, admin)
    }

    @Test
    fun `동시 중복 취소는 인원을 한 번만 줄이고 다른 수강생을 유지한다`() {
        val admin = adminToken()
        val seminarId = createSeminar(capacity = 2)
        val actors = approvedActors(3, admin)
        val first = actors[0]
        val other = actors[1]
        enrollSuccessfully(seminarId, first)
        val otherEnrollmentId = enrollSuccessfully(seminarId, other)

        val responses = runConcurrently(List(10) { first }) { actor ->
            cancelEnrollment(seminarId, actor.token).andReturn().response
        }

        assertStatuses(responses, mapOf(204 to 1, 404 to 9))
        assertEquals("", responses.single { it.status == 204 }.contentAsString)
        val expected = mutableMapOf(other.id to otherEnrollmentId)
        assertState(seminarId, 2, actors, expected, admin)
        expected[first.id] = enrollSuccessfully(seminarId, first)
        enroll(seminarId, actors[2].token).andExpect { status { isConflict() } }
        assertState(seminarId, 2, actors, expected, admin)
    }

    @Test
    fun `신청과 취소가 섞여도 인원 보존식과 정원을 지키고 이후 빈자리를 채울 수 있다`() {
        val admin = adminToken()
        val seminarId = createSeminar(capacity = 5)
        val actors = approvedActors(25, admin)
        val existing = actors.take(5)
        val newcomers = actors.drop(5)
        existing.forEach { enrollSuccessfully(seminarId, it) }
        val operations = newcomers.flatMapIndexed { index, actor ->
            buildList {
                if (index < existing.size) add(Operation(existing[index], cancel = true))
                add(Operation(actor, cancel = false))
            }
        }

        val results = runConcurrently(operations) { operation ->
            val response = if (operation.cancel) {
                cancelEnrollment(seminarId, operation.actor.token)
            } else {
                enroll(seminarId, operation.actor.token)
            }.andReturn().response
            operation to response
        }

        val cancellations = results.filter { it.first.cancel }.map { it.second }
        val enrollments = results.filterNot { it.first.cancel }
        assertStatuses(cancellations, mapOf(204 to 5))
        cancellations.forEach { assertEquals("", it.contentAsString) }
        assertTrue(
            enrollments.all { it.second.status == 201 || it.second.status == 409 },
            "신청 응답은 201 또는 409여야 합니다: ${enrollments.map { it.second.status }}",
        )
        val expected = enrollments.filter { it.second.status == 201 }.associate { (operation, response) ->
            operation.actor.id to createdEnrollmentId(response)
        }.toMutableMap()
        val finalCount = 5 + expected.size - cancellations.count { it.status == 204 }
        assertTrue(finalCount in 0..5)
        assertEquals(finalCount, expected.size)
        assertState(seminarId, 5, actors, expected, admin)

        for (actor in existing.take(5 - finalCount)) {
            expected[actor.id] = enrollSuccessfully(seminarId, actor)
        }
        val noSeat = actors.first { it.id !in expected }
        enroll(seminarId, noSeat.token).andExpect { status { isConflict() } }
        assertState(seminarId, 5, actors, expected, admin)
    }

    private fun approvedActors(count: Int, admin: String): List<Actor> =
        List(count) {
            val email = uniqueEmail()
            val id = signupRookie(email)
            approve(id, token = admin).andExpect { status { isOk() } }
            Actor(id, login(email))
        }

    private fun createdEnrollmentId(response: MockHttpServletResponse): Long {
        val body = objectMapper.readTree(response.contentAsString)
        assertTrue(body.path("id").isIntegralNumber, "201 응답에는 수강 신청 ID가 있어야 합니다.")
        return body.path("id").asLong().also { assertTrue(it > 0) }
    }

    private fun assertStatuses(responses: List<MockHttpServletResponse>, expected: Map<Int, Int>) {
        assertEquals(
            expected,
            responses.groupingBy { it.status }.eachCount(),
            "동시 요청의 응답 건수가 다릅니다. 오류 예시: " +
                responses.filter { it.status >= 500 }.take(3).joinToString { it.contentAsString.take(300) },
        )
    }

    private fun assertState(
        seminarId: Long,
        capacity: Int,
        participants: List<Actor>,
        expected: Map<Long, Long>,
        admin: String,
    ) {
        val body = dashboard(seminarId, admin, "?size=100")
        val rows = body.path("content").toList()
        assertEquals(expected.size, body.path("totalElements").asInt())
        assertEquals(expected.size, rows.size)
        assertEquals(expected.size, rows.map { it.path("id").asLong() }.toSet().size)
        assertEquals(expected, rows.associate { it.path("user").path("id").asLong() to it.path("id").asLong() })

        val seminar = responseJson(getAs(admin, "/seminars/$seminarId").andExpect { status { isOk() } })
        val expectedStatus = if (expected.size == capacity) "CLOSED" else "OPEN"
        assertEquals(capacity, seminar.path("capacity").asInt())
        assertEquals(expected.size, seminar.path("enrolledCount").asInt())
        assertEquals(expectedStatus, seminar.path("status").asString())
        val keyword = URLEncoder.encode(seminar.path("title").asString(), StandardCharsets.UTF_8)
        val listed = responseJson(
            getAs(null, "/seminars?keyword=$keyword&size=100").andExpect { status { isOk() } },
        ).path("content").toList().single { it.path("id").asLong() == seminarId }
        assertEquals(expected.size, listed.path("enrolledCount").asInt())
        assertEquals(expectedStatus, listed.path("status").asString())

        for (actor in participants) {
            val mine = myEnrollments(actor).filter { it.path("seminar").path("id").asLong() == seminarId }
            val expectedId = expected[actor.id]
            assertEquals(if (expectedId == null) 0 else 1, mine.size, "사용자 ${actor.id}의 수강 목록이 다릅니다.")
            if (expectedId != null) {
                assertEquals(expectedId, mine.single().path("id").asLong())
                assertEquals(expected.size, mine.single().path("seminar").path("enrolledCount").asInt())
                assertEquals(expectedStatus, mine.single().path("seminar").path("status").asString())
            }
        }
    }
}
