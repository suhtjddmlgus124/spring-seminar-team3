package com.wafflestudio.spring2026.seminar

import com.wafflestudio.spring2026.support.ApiIntegrationTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import kotlin.test.Test
import kotlin.test.assertTrue

class SeminarManagementApiTest : ApiIntegrationTest() {
    @Test
    fun `세미나를 개설하면 신청 중 상태로 조회할 수 있다`() {
        val seminarId = createSeminar(title = "Spring seminar", capacity = 30, totalGraceDays = 4)

        mockMvc.get("/seminars/$seminarId").andExpect {
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

        mockMvc.patch("/seminars/$seminarId") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Updated title","ignored":true}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.title") { value("Updated title") }
            jsonPath("$.description") { value("Original description") }
        }

        val response = mockMvc.patch("/seminars/$seminarId") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"description":null}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.title") { value("Updated title") }
        }.andReturn().response.contentAsString

        assertTrue(objectMapper.readTree(response).path("description").isNull)
    }

    @Test
    fun `유효하지 않은 세미나 쓰기 요청은 400을 반환한다`() {
        val current = now()
        mockMvc.post("/seminars") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "title" to "",
                    "capacity" to 0,
                    "applyStartAt" to current.plusDays(1).toString(),
                    "applyEndAt" to current.toString(),
                    "totalGraceDays" to -1,
                ),
            )
        }.andExpect {
            status { isBadRequest() }
        }

        val seminarId = createSeminar()
        mockMvc.patch("/seminars/$seminarId") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"   "}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `없는 세미나는 404를 반환한다`() {
        mockMvc.get("/seminars/999999").andExpect {
            status { isNotFound() }
        }

        mockMvc.patch("/seminars/999999") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Updated title"}"""
        }.andExpect {
            status { isNotFound() }
        }
    }
}
