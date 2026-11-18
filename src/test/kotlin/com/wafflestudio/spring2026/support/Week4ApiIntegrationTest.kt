package com.wafflestudio.spring2026.support

import org.springframework.test.web.servlet.ResultActionsDsl
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

abstract class Week4ApiIntegrationTest : ApiIntegrationTest() {
    // 요청의 명시적인 null이 제출 애플리케이션의 응답 직렬화 설정에 의해 빠지지 않게 한다.
    private val requestMapper = JsonMapper.builder().build()

    protected companion object {
        const val MISSING_ID = 999_999_999L
    }

    protected data class EnrolledRookie(
        val actor: Actor,
        val enrollmentId: Long,
    )

    protected data class RecordState(
        val sessionId: Long,
        val attendance: String?,
        val assignmentStatus: String?,
    )

    protected data class OperatingState(
        val graceDaysRemaining: Int,
        val dropped: Boolean,
        val records: List<RecordState>,
    )

    protected fun enrolledRookie(seminarId: Long): EnrolledRookie {
        val actor = approvedRookie()
        return EnrolledRookie(actor, enrollSuccessfully(seminarId, actor))
    }

    protected fun enrollSuccessfully(seminarId: Long, actor: Actor): Long =
        responseId(enroll(seminarId, actor.token).andExpect { status { isCreated() } })

    protected fun dashboard(
        seminarId: Long,
        token: String,
        query: String = "",
    ): JsonNode =
        responseJson(
            getAs(token, "/seminars/$seminarId/dashboard$query").andExpect { status { isOk() } },
        ).also { body ->
            assertEquals(seminarId, requiredInteger(body, "seminarId"))
            val page = requiredInteger(body, "page")
            val size = requiredInteger(body, "size")
            val total = requiredInteger(body, "totalElements")
            val totalPages = requiredInteger(body, "totalPages")
            assertTrue(page >= 0 && size in 1L..100L && total >= 0)
            assertEquals((total + size - 1) / size, totalPages)
            assertTrue(body.path("content").isArray, "dashboard.content는 배열이어야 합니다.")
            assertTrue(body.path("sessions").isArray, "dashboard.sessions는 배열이어야 합니다.")
            assertEquals(minOf(size, maxOf(0L, total - page * size)), body.path("content").size().toLong())

            val sessionIds = body.path("sessions").toList().mapIndexed { index, session ->
                val id = requiredInteger(session, "id")
                assertEquals((index + 1).toLong(), requiredInteger(session, "round"))
                for (field in listOf("title", "startsAt", "assignmentTitle")) {
                    assertTrue(session.path(field).isString, "sessions[].$field 문자열이 필요합니다.")
                }
                OffsetDateTime.parse(session.path("startsAt").asString())
                id
            }
            assertEquals(sessionIds.size, sessionIds.toSet().size)
            val enrollmentIds = body.path("content").toList().map { row ->
                val id = requiredInteger(row, "id")
                assertTrue(id > 0)
                assertTrue(row.path("user").isObject)
                requiredInteger(row.path("user"), "id")
                for (field in listOf("email", "name", "githubUsername")) {
                    assertTrue(row.path("user").path(field).isString, "user.$field 문자열이 필요합니다.")
                }
                assertTrue(requiredInteger(row, "graceDaysRemaining") >= 0)
                assertTrue(row.path("dropped").isBoolean)
                assertTrue(row.path("createdAt").isString)
                OffsetDateTime.parse(row.path("createdAt").asString())
                assertTrue(row.path("records").isArray)
                val recordSessionIds = row.path("records").toList().map { record ->
                    nullableStatus(record, "attendance")
                    nullableStatus(record, "assignmentStatus")
                    requiredInteger(record, "sessionId")
                }
                assertEquals(sessionIds, recordSessionIds, "각 수강생의 기록은 전체 회차와 같은 순서여야 합니다.")
                id
            }
            assertEquals(enrollmentIds.sorted(), enrollmentIds)
            assertEquals(enrollmentIds.size, enrollmentIds.toSet().size)
        }

    private fun requiredInteger(node: JsonNode, field: String): Long {
        assertTrue(node.path(field).isIntegralNumber, "$field 정수 프로퍼티가 필요합니다.")
        return node.path(field).asLong()
    }

    protected fun dashboardRow(dashboard: JsonNode, enrollmentId: Long): JsonNode =
        assertNotNull(
            dashboard.path("content").toList().singleOrNull { it.path("id").asLong() == enrollmentId },
            "대시보드에 수강 신청 $enrollmentId 행이 정확히 하나 있어야 합니다.",
        )

    protected fun dashboardRecord(row: JsonNode, sessionId: Long): JsonNode {
        assertTrue(row.path("records").isArray, "records는 배열이어야 합니다.")
        return assertNotNull(
            row.path("records").toList().singleOrNull { it.path("sessionId").asLong() == sessionId },
            "수강 신청 ${row.path("id")}에 회차 $sessionId 기록이 정확히 하나 있어야 합니다.",
        )
    }

    protected fun nullableStatus(record: JsonNode, field: String): String? {
        assertTrue(record.has(field), "$field 프로퍼티는 미기록일 때도 생략하면 안 됩니다.")
        val value = record.path(field)
        assertTrue(value.isNull || value.isString, "$field 값은 문자열 또는 null이어야 합니다.")
        return if (value.isNull) null else value.asString()
    }

    protected fun assertRecord(
        dashboard: JsonNode,
        enrollmentId: Long,
        sessionId: Long,
        attendance: String?,
        assignmentStatus: String?,
    ) {
        val record = dashboardRecord(dashboardRow(dashboard, enrollmentId), sessionId)
        assertEquals(attendance, nullableStatus(record, "attendance"), "출석 상태가 다릅니다.")
        assertEquals(assignmentStatus, nullableStatus(record, "assignmentStatus"), "과제 상태가 다릅니다.")
    }

    protected fun operatingState(dashboard: JsonNode): Map<Long, OperatingState> =
        dashboard.path("content").toList().associate { row ->
            assertTrue(row.path("graceDaysRemaining").isIntegralNumber)
            assertTrue(row.path("dropped").isBoolean)
            assertTrue(row.path("records").isArray)
            row.path("id").asLong() to OperatingState(
                graceDaysRemaining = row.path("graceDaysRemaining").asInt(),
                dropped = row.path("dropped").asBoolean(),
                records = row.path("records").toList().map { record ->
                    RecordState(
                        sessionId = record.path("sessionId").asLong(),
                        attendance = nullableStatus(record, "attendance"),
                        assignmentStatus = nullableStatus(record, "assignmentStatus"),
                    )
                },
            )
        }

    protected fun recordPatch(enrollmentId: Long, vararg fields: Pair<String, Any?>): Map<String, Any?> =
        mapOf("enrollmentId" to enrollmentId) + fields.toMap()

    protected fun updateRecords(
        sessionId: Long,
        token: String,
        vararg records: Map<String, Any?>,
    ): ResultActionsDsl =
        patchOperation(token, "/sessions/$sessionId/records", mapOf("records" to records.toList()))

    protected fun updateEnrollment(
        seminarId: Long,
        enrollmentId: Long,
        token: String,
        body: Any,
    ): ResultActionsDsl =
        patchOperation(token, "/seminars/$seminarId/enrollments/$enrollmentId", body)

    protected fun patchOperation(token: String?, path: String, body: Any): ResultActionsDsl =
        patchAs(token, path, if (body is String) body else requestMapper.writeValueAsString(body))

    protected fun myEnrollments(actor: Actor): List<JsonNode> =
        responseJson(
            getAs(actor.token, "/users/me/enrollments?size=100").andExpect { status { isOk() } },
        ).path("content").toList()

    protected fun assertSameInstant(expected: JsonNode, actual: JsonNode) {
        assertTrue(expected.isString && actual.isString, "일시는 ISO-8601 문자열이어야 합니다.")
        assertEquals(
            OffsetDateTime.parse(expected.asString()).toInstant(),
            OffsetDateTime.parse(actual.asString()).toInstant(),
        )
    }
}
