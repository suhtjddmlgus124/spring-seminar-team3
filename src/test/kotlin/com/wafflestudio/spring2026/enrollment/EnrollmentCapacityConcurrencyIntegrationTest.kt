package com.wafflestudio.spring2026.enrollment

import com.wafflestudio.spring2026.support.ApiIntegrationTest
import com.wafflestudio.spring2026.support.runConcurrently
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnrollmentCapacityConcurrencyIntegrationTest : ApiIntegrationTest() {
    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private data class Operation(val actor: Actor, val cancel: Boolean)

    @Test
    fun `정원 30명에 승인 루키 100명이 동시 신청하면 30건만 저장된다`() {
        val admin = adminToken()
        val seminarId = createSeminar(capacity = 30)
        val actors = approvedActors(100, admin)
        assertStoredEnrollments(seminarId, 30, emptyMap())

        val results = runConcurrently(actors, parallelism = 100) { actor ->
            actor to enroll(seminarId, actor.token).andReturn().response
        }

        assertStatuses(results.map { it.second }, mapOf(201 to 30, 409 to 70))
        assertCapacityRejections(results.map { it.second })
        val expected = results.filter { it.second.status == 201 }.associate { (actor, response) ->
            actor.id to enrollmentId(response)
        }
        assertEquals(30, expected.size)
        assertStoredEnrollments(seminarId, 30, expected)
    }

    @Test
    fun `정원 5명의 신청 취소 혼합은 인원을 보존하고 이후 빈자리를 채울 수 있다`() {
        val capacity = 5
        val admin = adminToken()
        val seminarId = createSeminar(capacity = capacity)
        val actors = approvedActors(25, admin)
        val existing = actors.take(capacity)
        val newcomers = actors.drop(capacity)
        val initial = existing.associate { actor -> actor.id to enrollSuccessfully(seminarId, actor) }
        assertStoredEnrollments(seminarId, capacity, initial)

        val operations = newcomers.flatMapIndexed { index, actor ->
            buildList {
                if (index < existing.size) add(Operation(existing[index], cancel = true))
                add(Operation(actor, cancel = false))
            }
        }
        val results = runConcurrently(operations, parallelism = operations.size) { operation ->
            val response = if (operation.cancel) {
                cancelEnrollment(seminarId, operation.actor.token)
            } else {
                enroll(seminarId, operation.actor.token)
            }.andReturn().response
            operation to response
        }

        val cancellations = results.filter { it.first.cancel }.map { it.second }
        val enrollments = results.filterNot { it.first.cancel }
        assertStatuses(cancellations, mapOf(204 to capacity))
        cancellations.forEach { assertEquals("", it.contentAsString) }
        assertTrue(
            enrollments.all { it.second.status == 201 || it.second.status == 409 },
            "신규 신청 응답은 201 또는 409여야 합니다: ${enrollments.map { it.second.status }}",
        )
        assertCapacityRejections(enrollments.map { it.second })
        val successfulEnrollments = enrollments.filter { it.second.status == 201 }
        val expected = successfulEnrollments.associate { (operation, response) ->
            operation.actor.id to enrollmentId(response)
        }.toMutableMap()
        assertEquals(successfulEnrollments.size, expected.size)
        val expectedCount = initial.size + successfulEnrollments.size - cancellations.count { it.status == 204 }
        assertTrue(expectedCount in 0..capacity)
        assertEquals(expectedCount, storedEnrollments(seminarId).size, "초기 인원 + 성공 신청 - 성공 취소가 실제 인원과 같아야 합니다.")
        assertStoredEnrollments(seminarId, capacity, expected)

        for (actor in existing.take(capacity - expectedCount)) {
            expected[actor.id] = enrollSuccessfully(seminarId, actor)
        }
        assertEquals(capacity, expected.size, "남은 자리를 모두 채울 수 있어야 합니다.")
        val noSeat = actors.first { it.id !in expected }
        val rejected = enroll(seminarId, noSeat.token).andReturn().response
        assertEquals(409, rejected.status)
        assertCapacityRejections(listOf(rejected))
        assertStoredEnrollments(seminarId, capacity, expected)
    }

    private fun approvedActors(count: Int, admin: String): List<Actor> =
        List(count) {
            val email = uniqueEmail()
            val id = signupRookie(email)
            approve(id, token = admin).andExpect { status { isOk() } }
            Actor(id, login(email))
        }

    private fun enrollSuccessfully(seminarId: Long, actor: Actor): Long =
        enrollmentId(enroll(seminarId, actor.token).andExpect { status { isCreated() } }.andReturn().response)

    private fun enrollmentId(response: MockHttpServletResponse): Long {
        val id = objectMapper.readTree(response.contentAsString).path("id")
        assertTrue(id.isIntegralNumber, "성공 응답에는 수강 신청 ID가 있어야 합니다.")
        return id.asLong().also { assertTrue(it > 0) }
    }

    private fun assertStatuses(responses: List<MockHttpServletResponse>, expected: Map<Int, Int>) {
        assertEquals(
            expected,
            responses.groupingBy { it.status }.eachCount(),
            "동시 요청의 응답 건수가 다릅니다. 오류 예시: " +
                responses.filter { it.status >= 500 }.take(3).joinToString { it.contentAsString.take(300) },
        )
    }

    private fun assertCapacityRejections(responses: List<MockHttpServletResponse>) {
        responses.filter { it.status == 409 }.forEach { response ->
            assertEquals(
                "SEMINAR_IS_NOT_OPEN",
                objectMapper.readTree(response.contentAsString).path("code").asString(),
                "서로 다른 승인 루키의 신청은 정원 마감으로만 거절되어야 합니다.",
            )
        }
    }

    private fun storedEnrollments(seminarId: Long): List<Pair<Long, Long>> =
        jdbcTemplate.query(
            "SELECT rookieId, id FROM enrollments WHERE seminarId = ?",
            { row, _ -> row.getLong("rookieId") to row.getLong("id") },
            seminarId,
        )

    private fun assertStoredEnrollments(seminarId: Long, capacity: Int, expected: Map<Long, Long>) {
        val rows = storedEnrollments(seminarId)
        assertEquals(expected.size, rows.size, "실제 저장 건수가 성공한 신청 건수와 같아야 합니다.")
        assertTrue(rows.size <= capacity, "실제 수강 신청 수가 정원을 넘으면 안 됩니다.")
        assertEquals(expected.size, rows.map { it.second }.toSet().size)
        assertEquals(expected, rows.toMap(), "성공 응답의 사용자와 수강 신청 ID가 실제 저장 결과와 같아야 합니다.")
    }
}
