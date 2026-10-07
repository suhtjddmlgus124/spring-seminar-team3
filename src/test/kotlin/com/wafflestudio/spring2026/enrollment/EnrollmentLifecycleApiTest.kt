package com.wafflestudio.spring2026.enrollment

import com.wafflestudio.spring2026.support.ApiIntegrationTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EnrollmentLifecycleApiTest : ApiIntegrationTest() {
    @Test
    fun `승인된 루키는 세미나의 그레이스 데이로 수강 신청한다`() {
        val seminarId = createSeminar(totalGraceDays = 4)
        val rookie = approvedRookie()

        enroll(seminarId, rookie.token).andExpect {
            status { isCreated() }
            jsonPath("$.graceDaysRemaining") { value(4) }
            jsonPath("$.createdAt") { exists() }
        }

        getAs(rookie.token, "/seminars/$seminarId").andExpect {
            status { isOk() }
            jsonPath("$.enrolledCount") { value(1) }
        }
    }

    @Test
    fun `중복 또는 신청 불가 상태의 수강 신청은 409를 반환한다`() {
        val openSeminarId = createSeminar()
        val rookie = approvedRookie()
        enroll(openSeminarId, rookie.token).andExpect { status { isCreated() } }

        enroll(openSeminarId, rookie.token).andExpect {
            status { isConflict() }
        }

        val futureSeminarId = createSeminar(
            applyStartAt = now().plusDays(1),
            applyEndAt = now().plusDays(2),
        )
        enroll(futureSeminarId, approvedRookie().token).andExpect {
            status { isConflict() }
        }
    }

    @Test
    fun `정원이 찬 세미나를 취소하면 자리가 열린다`() {
        val seminarId = createSeminar(capacity = 1)
        val first = approvedRookie()
        enroll(seminarId, first.token).andExpect { status { isCreated() } }

        getAs(first.token, "/seminars/$seminarId").andExpect {
            status { isOk() }
            jsonPath("$.enrolledCount") { value(1) }
            jsonPath("$.status") { value("CLOSED") }
        }

        cancelEnrollment(seminarId, first.token).andExpect {
            status { isNoContent() }
            content { string("") }
        }

        getAs(first.token, "/seminars/$seminarId").andExpect {
            status { isOk() }
            jsonPath("$.enrolledCount") { value(0) }
            jsonPath("$.status") { value("OPEN") }
        }

        enroll(seminarId, approvedRookie().token).andExpect {
            status { isCreated() }
        }
    }

    @Test
    fun `없는 수강 신청 리소스는 404를 반환한다`() {
        val seminarId = createSeminar()
        val rookie = approvedRookie()

        enroll(999999, rookie.token).andExpect {
            status { isNotFound() }
        }

        cancelEnrollment(seminarId, rookie.token).andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `내 수강 목록은 페이지 단위로 신청한 세미나의 정보를 보여 준다`() {
        val seminarId = createSeminar(title = unique("Mine"), capacity = 5, totalGraceDays = 2)
        val rookie = approvedRookie()

        getAs(rookie.token, "/users/me/enrollments").andExpect {
            status { isOk() }
            jsonPath("$.content.length()") { value(0) }
            jsonPath("$.page") { value(0) }
            jsonPath("$.size") { value(20) }
            jsonPath("$.totalElements") { value(0) }
            jsonPath("$.totalPages") { value(0) }
        }

        val enrollmentId = responseId(enroll(seminarId, rookie.token).andExpect { status { isCreated() } })

        getAs(rookie.token, "/users/me/enrollments?page=0&size=5").andExpect {
            status { isOk() }
            jsonPath("$.size") { value(5) }
            jsonPath("$.totalElements") { value(1) }
            jsonPath("$.totalPages") { value(1) }
            jsonPath("$.content[0].id") { value(enrollmentId) }
            jsonPath("$.content[0].seminar.id") { value(seminarId) }
            jsonPath("$.content[0].seminar.status") { value("OPEN") }
            jsonPath("$.content[0].seminar.capacity") { value(5) }
            jsonPath("$.content[0].seminar.enrolledCount") { value(1) }
            jsonPath("$.content[0].seminar.applyStartAt") { exists() }
            jsonPath("$.content[0].seminar.applyEndAt") { exists() }
            jsonPath("$.content[0].graceDaysRemaining") { value(2) }
            jsonPath("$.content[0].dropped") { value(false) }
            jsonPath("$.content[0].createdAt") { exists() }
        }

        cancelEnrollment(seminarId, rookie.token).andExpect { status { isNoContent() } }
        getAs(rookie.token, "/users/me/enrollments").andExpect {
            jsonPath("$.totalElements") { value(0) }
        }
    }

    @Test
    fun `내 수강 목록은 page 와 size 에 맞춰 나눠 보여 준다`() {
        val rookie = approvedRookie()
        val enrollmentIds = List(5) { createSeminar() }.map { seminarId ->
            responseId(enroll(seminarId, rookie.token).andExpect { status { isCreated() } })
        }

        for ((page, expected) in enrollmentIds.reversed().chunked(2).withIndex()) {
            val result = getAs(rookie.token, "/users/me/enrollments?page=$page&size=2").andExpect {
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
