package com.wafflestudio.spring2026.session

import com.wafflestudio.spring2026.support.ApiIntegrationTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.Test
import kotlin.test.assertFalse

class SessionScheduleApiTest : ApiIntegrationTest() {
    @Test
    fun `회차를 시작 시각순으로 정렬하고 순번을 다시 계산한다`() {
        val seminarId = createSeminar()
        val current = now()
        val laterSessionId = createSession(
            seminarId = seminarId,
            startsAt = current.plusDays(2),
            title = "Later session",
            lectureContent = "Later lecture",
        )
        val earlierSessionId = createSession(
            seminarId = seminarId,
            startsAt = current.plusDays(1),
            title = "Earlier session",
            lectureContent = "Earlier lecture",
        )

        val listBody = mockMvc.get("/seminars/$seminarId/sessions").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].id") { value(earlierSessionId) }
            jsonPath("$[0].round") { value(1) }
            jsonPath("$[1].id") { value(laterSessionId) }
            jsonPath("$[1].round") { value(2) }
        }.andReturn().response.contentAsString

        val listedSessions = objectMapper.readTree(listBody)
        assertFalse(listedSessions[0].has("lectureContent"))
        assertFalse(listedSessions[0].has("assignmentContent"))

        mockMvc.get("/sessions/$laterSessionId").andExpect {
            status { isOk() }
            jsonPath("$.seminarId") { value(seminarId) }
            jsonPath("$.round") { value(2) }
            jsonPath("$.title") { value("Later session") }
            jsonPath("$.lectureContent") { value("Later lecture") }
        }
    }

    @Test
    fun `유효하지 않은 회차 요청은 400을 반환한다`() {
        val seminarId = createSeminar()

        mockMvc.post("/seminars/$seminarId/sessions") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"startsAt":"${now().plusDays(1)}"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `없는 회차 리소스는 404를 반환한다`() {
        mockMvc.post("/seminars/999999/sessions") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "title" to "Session",
                    "startsAt" to now().plusDays(1).toString(),
                    "location" to "Room 208",
                    "assignmentTitle" to "Assignment",
                ),
            )
        }.andExpect {
            status { isNotFound() }
        }

        mockMvc.get("/seminars/999999/sessions").andExpect {
            status { isNotFound() }
        }

        mockMvc.get("/sessions/999999").andExpect {
            status { isNotFound() }
        }
    }
}
