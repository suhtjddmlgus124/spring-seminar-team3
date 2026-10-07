package com.wafflestudio.spring2026.seminar

import com.wafflestudio.spring2026.support.ApiIntegrationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeminarManagementApiTest : ApiIntegrationTest() {
    @Test
    fun `세미나를 개설하면 신청 중 상태로 조회할 수 있다`() {
        val seminarId = createSeminar(title = "Spring seminar", capacity = 30, totalGraceDays = 4)

        getAs(adminToken(), "/seminars/$seminarId").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(seminarId) }
            jsonPath("$.title") { value("Spring seminar") }
            jsonPath("$.capacity") { value(30) }
            jsonPath("$.enrolledCount") { value(0) }
            jsonPath("$.totalGraceDays") { value(4) }
            jsonPath("$.status") { value("OPEN") }
            jsonPath("$.sessionCount") { value(0) }
        }
    }

    @Test
    fun `전달된 세미나 필드를 수정하고 설명을 지울 수 있다`() {
        val seminarId = createSeminar(title = "Original title", description = "Original description")
        val admin = adminToken()

        patchAs(admin, "/seminars/$seminarId", """{"title":"Updated title","ignored":true}""").andExpect {
            status { isOk() }
            jsonPath("$.title") { value("Updated title") }
            jsonPath("$.description") { value("Original description") }
        }

        val response = patchAs(admin, "/seminars/$seminarId", """{"description":null}""").andExpect {
            status { isOk() }
            jsonPath("$.title") { value("Updated title") }
        }.andReturn().response.contentAsString

        assertTrue(objectMapper.readTree(response).path("description").isNull)
    }

    @Test
    fun `유효하지 않은 세미나 쓰기 요청은 400을 반환한다`() {
        val current = now()
        val admin = adminToken()
        postAs(
            admin,
            "/seminars",
            mapOf(
                "title" to "",
                "capacity" to 0,
                "applyStartAt" to current.plusDays(1).toString(),
                "applyEndAt" to current.toString(),
                "totalGraceDays" to -1,
            ),
        ).andExpect {
            status { isBadRequest() }
        }

        val seminarId = createSeminar()
        patchAs(admin, "/seminars/$seminarId", """{"title":"   "}""").andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `없는 세미나는 404를 반환한다`() {
        val admin = adminToken()
        getAs(admin, "/seminars/999999").andExpect {
            status { isNotFound() }
        }

        patchAs(admin, "/seminars/999999", """{"title":"Updated title"}""").andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `신청 기간 전이면 BEFORE, 신청 기간이 끝났으면 CLOSED 다`() {
        val admin = adminToken()
        val beforeId = createSeminar(applyStartAt = now().plusDays(1), applyEndAt = now().plusDays(2))
        val endedId = createSeminar(applyStartAt = now().minusDays(2), applyEndAt = now().minusDays(1))

        getAs(admin, "/seminars/$beforeId").andExpect { jsonPath("$.status") { value("BEFORE") } }
        getAs(admin, "/seminars/$endedId").andExpect { jsonPath("$.status") { value("CLOSED") } }
    }

    @Test
    fun `세미나 목록은 신청 상태로 거를 수 있고, 정해지지 않은 상태는 400을 반환한다`() {
        val keyword = unique("Filter")
        val openId = createSeminar(title = "$keyword open")
        val beforeId = createSeminar(title = "$keyword before", applyStartAt = now().plusDays(1), applyEndAt = now().plusDays(2))
        val closedId = createSeminar(title = "$keyword closed", applyStartAt = now().minusDays(2), applyEndAt = now().minusDays(1))

        for ((status, id) in listOf("OPEN" to openId, "BEFORE" to beforeId, "CLOSED" to closedId)) {
            getAs(null, "/seminars?keyword=$keyword&status=$status").andExpect {
                status { isOk() }
                jsonPath("$.totalElements") { value(1) }
                jsonPath("$.content[0].id") { value(id) }
                jsonPath("$.content[0].status") { value(status) }
            }
        }

        getAs(null, "/seminars?status=UNKNOWN").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `세미나 목록은 page 와 size 에 맞춰 나눠 보여 준다`() {
        val keyword = unique("Paged")
        val seminarIds = List(5) { createSeminar(title = "$keyword $it") }

        for ((page, expected) in seminarIds.chunked(2).withIndex()) {
            val result = getAs(null, "/seminars?keyword=$keyword&page=$page&size=2").andExpect {
                status { isOk() }
                jsonPath("$.page") { value(page) }
                jsonPath("$.size") { value(2) }
                jsonPath("$.totalElements") { value(5) }
                jsonPath("$.totalPages") { value(3) }
            }
            assertEquals(expected, contentIds(result), "page=$page 의 항목이 다릅니다.")
        }
    }
}
