package com.wafflestudio.spring2026.session

import com.wafflestudio.spring2026.support.ApiIntegrationTest
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
        val admin = adminToken()

        val listBody = getAs(admin, "/seminars/$seminarId/sessions").andExpect {
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

        getAs(admin, "/sessions/$laterSessionId").andExpect {
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

        postAs(adminToken(), "/seminars/$seminarId/sessions", """{"startsAt":"${now().plusDays(1)}"}""").andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `없는 회차 리소스는 404를 반환한다`() {
        val admin = adminToken()
        postAs(admin, "/seminars/999999/sessions", sessionBody()).andExpect {
            status { isNotFound() }
        }

        getAs(admin, "/seminars/999999/sessions").andExpect {
            status { isNotFound() }
        }

        getAs(admin, "/sessions/999999").andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `회차 수정 요청이 유효하지 않으면 400, 없는 회차는 404를 반환한다`() {
        val seminarId = createSeminar()
        val sessionId = createSession(seminarId)
        val admin = adminToken()

        patchAs(admin, "/sessions/$sessionId", """{"title":"   "}""").andExpect { status { isBadRequest() } }
        patchAs(admin, "/sessions/$sessionId", """{"location":""}""").andExpect { status { isBadRequest() } }
        patchAs(admin, "/sessions/$sessionId", """{"assignmentTitle":" "}""").andExpect { status { isBadRequest() } }

        patchAs(admin, "/sessions/999999", mapOf("title" to "Updated title")).andExpect { status { isNotFound() } }
    }

    @Test
    fun `회차의 시작 시각을 바꾸면 회차 번호가 다시 매겨진다`() {
        val seminarId = createSeminar()
        val current = now()
        val firstId = createSession(seminarId, startsAt = current.plusDays(1), title = "First session")
        val secondId = createSession(seminarId, startsAt = current.plusDays(2), title = "Second session")
        val admin = adminToken()

        patchAs(admin, "/sessions/$firstId", mapOf("startsAt" to current.plusDays(3).toString())).andExpect {
            status { isOk() }
            jsonPath("$.id") { value(firstId) }
            jsonPath("$.round") { value(2) }
            jsonPath("$.title") { value("First session") }
        }

        getAs(admin, "/seminars/$seminarId/sessions").andExpect {
            status { isOk() }
            jsonPath("$[0].id") { value(secondId) }
            jsonPath("$[0].round") { value(1) }
            jsonPath("$[1].id") { value(firstId) }
            jsonPath("$[1].round") { value(2) }
        }
    }
}
