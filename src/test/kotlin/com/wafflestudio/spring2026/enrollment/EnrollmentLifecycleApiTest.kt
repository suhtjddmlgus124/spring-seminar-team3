package com.wafflestudio.spring2026.enrollment

import com.wafflestudio.spring2026.support.ApiIntegrationTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import kotlin.test.Test

class EnrollmentLifecycleApiTest : ApiIntegrationTest() {
    @Test
    fun `승인된 루키는 세미나의 그레이스 데이로 수강 신청한다`() {
        val seminarId = createSeminar(totalGraceDays = 4)
        val rookieId = signupRookie()

        enroll(seminarId, rookieId).andExpect {
            status { isForbidden() }
        }

        approve(rookieId).andExpect { status { isOk() } }
        enroll(seminarId, rookieId).andExpect {
            status { isCreated() }
            jsonPath("$.graceDaysRemaining") { value(4) }
            jsonPath("$.createdAt") { exists() }
        }

        mockMvc.get("/seminars/$seminarId").andExpect {
            status { isOk() }
            jsonPath("$.enrolledCount") { value(1) }
        }
    }

    @Test
    fun `루키가 아닌 사용자의 수강 신청은 400을 반환한다`() {
        val seminarId = createSeminar()
        val staffId = signupStaff(seminarId)

        enroll(seminarId, staffId).andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `중복 또는 신청 불가 상태의 수강 신청은 409를 반환한다`() {
        val openSeminarId = createSeminar()
        val rookieId = signupRookie()
        approve(rookieId).andExpect { status { isOk() } }
        enroll(openSeminarId, rookieId).andExpect { status { isCreated() } }

        enroll(openSeminarId, rookieId).andExpect {
            status { isConflict() }
        }

        val futureSeminarId = createSeminar(
            applyStartAt = now().plusDays(1),
            applyEndAt = now().plusDays(2),
        )
        val anotherRookieId = signupRookie()
        approve(anotherRookieId).andExpect { status { isOk() } }
        enroll(futureSeminarId, anotherRookieId).andExpect {
            status { isConflict() }
        }
    }

    @Test
    fun `정원이 찬 세미나를 취소하면 자리가 열린다`() {
        val seminarId = createSeminar(capacity = 1)
        val firstRookieId = signupRookie()
        approve(firstRookieId).andExpect { status { isOk() } }
        val enrollmentId = responseId(enroll(seminarId, firstRookieId).andExpect { status { isCreated() } })

        mockMvc.get("/seminars/$seminarId").andExpect {
            status { isOk() }
            jsonPath("$.enrolledCount") { value(1) }
            jsonPath("$.status") { value("CLOSED") }
        }

        mockMvc.delete("/seminars/$seminarId/enrollments/$enrollmentId").andExpect {
            status { isNoContent() }
            content { string("") }
        }

        mockMvc.get("/seminars/$seminarId").andExpect {
            status { isOk() }
            jsonPath("$.enrolledCount") { value(0) }
            jsonPath("$.status") { value("OPEN") }
        }

        val nextRookieId = signupRookie()
        approve(nextRookieId).andExpect { status { isOk() } }
        enroll(seminarId, nextRookieId).andExpect {
            status { isCreated() }
        }
    }

    @Test
    fun `없는 수강 신청 리소스는 404를 반환한다`() {
        val seminarId = createSeminar()
        val rookieId = signupRookie()
        approve(rookieId).andExpect { status { isOk() } }

        enroll(999999, rookieId).andExpect {
            status { isNotFound() }
        }

        mockMvc.delete("/seminars/$seminarId/enrollments/999999").andExpect {
            status { isNotFound() }
        }
    }
}
