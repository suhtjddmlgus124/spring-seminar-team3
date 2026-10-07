package com.wafflestudio.spring2026.auth

import com.wafflestudio.spring2026.support.ApiIntegrationTest
import kotlin.test.Test
import kotlin.test.assertTrue

class AuthorizationApiTest : ApiIntegrationTest() {
    @Test
    fun `가입이 승인되지 않은 사용자는 내 정보 조회 외에는 403을 반환한다`() {
        val seminarId = createSeminar()
        val sessionId = createSession(seminarId)
        val other = approvedRookie()

        val pending = pendingRookie()
        getAs(pending.token, "/users/${other.id}").andExpect { status { isForbidden() } }
        getAs(pending.token, "/users/${pending.id}").andExpect { status { isForbidden() } }
        getAs(pending.token, "/seminars/$seminarId").andExpect { status { isForbidden() } }
        getAs(pending.token, "/sessions/$sessionId").andExpect { status { isForbidden() } }
        getAs(pending.token, "/users/me/enrollments").andExpect { status { isForbidden() } }
        enroll(seminarId, pending.token).andExpect { status { isForbidden() } }

        val rejected = rejectedRookie()
        getAs(rejected.token, "/seminars/$seminarId").andExpect { status { isForbidden() } }
        enroll(seminarId, rejected.token).andExpect { status { isForbidden() } }

        val pendingStaff = pendingStaff(seminarId)
        postAs(pendingStaff.token, "/seminars/$seminarId/sessions", sessionBody()).andExpect { status { isForbidden() } }
        getAs(pendingStaff.token, "/seminars/$seminarId/sessions").andExpect { status { isForbidden() } }
        getAs(pendingStaff.token, "/users").andExpect { status { isForbidden() } }
        approve(signupRookie(), token = pendingStaff.token).andExpect { status { isForbidden() } }
    }

    @Test
    fun `세미나 개설은 와장만, 수정은 와장과 담당 운영진만 할 수 있다`() {
        val seminarId = createSeminar()
        val staff = approvedStaff(seminarId)
        val otherStaff = approvedStaff(createSeminar())
        val rookie = approvedRookie()

        val seminarBody = mapOf(
            "title" to unique("Seminar"),
            "capacity" to 10,
            "applyStartAt" to now().minusDays(1).toString(),
            "applyEndAt" to now().plusDays(1).toString(),
            "totalGraceDays" to 3,
        )
        postAs(staff.token, "/seminars", seminarBody).andExpect { status { isForbidden() } }
        postAs(rookie.token, "/seminars", seminarBody).andExpect { status { isForbidden() } }

        patchAs(otherStaff.token, "/seminars/$seminarId", mapOf("title" to "Other staff")).andExpect { status { isForbidden() } }
        patchAs(rookie.token, "/seminars/$seminarId", mapOf("title" to "Rookie")).andExpect { status { isForbidden() } }

        patchAs(staff.token, "/seminars/$seminarId", mapOf("title" to "Updated by staff")).andExpect {
            status { isOk() }
            jsonPath("$.title") { value("Updated by staff") }
        }
    }

    @Test
    fun `운영진은 루키의 가입 신청만 심사할 수 있다`() {
        val seminarId = createSeminar()
        val staff = approvedStaff(seminarId)
        val rookie = approvedRookie()

        approve(signupStaff(seminarId), token = staff.token).andExpect { status { isForbidden() } }
        approve(signupRookie(), token = rookie.token).andExpect { status { isForbidden() } }

        approve(signupRookie(), token = staff.token).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("APPROVED") }
        }
    }

    @Test
    fun `가입 신청 목록은 와장과 운영진만 볼 수 있고 운영진에게는 루키의 신청만 보인다`() {
        val seminarId = createSeminar()
        val staff = approvedStaff(seminarId)
        val pendingStaffId = signupStaff(seminarId)
        val pendingRookieId = signupRookie()

        getAs(approvedRookie().token, "/users").andExpect { status { isForbidden() } }

        val staffView = getAs(staff.token, "/users?status=PENDING&role=STAFF&size=100").andExpect {
            status { isOk() }
            jsonPath("$.page") { value(0) }
            jsonPath("$.size") { value(100) }
            jsonPath("$.totalElements") { exists() }
        }.andReturn().response.contentAsString
        val staffContent = objectMapper.readTree(staffView).path("content").toList()
        assertTrue(staffContent.all { it.path("role").asString() == "ROOKIE" }, "운영진에게는 루키의 신청만 보여야 합니다.")
        assertTrue(staffContent.any { it.path("id").asLong() == pendingRookieId })
        assertTrue(staffContent.none { it.path("id").asLong() == pendingStaffId })

        val adminView = getAs(adminToken(), "/users?status=PENDING&role=STAFF&size=100").andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString
        val adminContent = objectMapper.readTree(adminView).path("content").toList()
        assertTrue(adminContent.all { it.path("role").asString() == "STAFF" })
        assertTrue(adminContent.any { it.path("id").asLong() == pendingStaffId })
    }

    @Test
    fun `회차 등록과 수정은 와장과 담당 운영진만 할 수 있다`() {
        val seminarId = createSeminar()
        val staff = approvedStaff(seminarId)
        val otherStaff = approvedStaff(createSeminar())
        val rookie = approvedRookie()

        postAs(otherStaff.token, "/seminars/$seminarId/sessions", sessionBody()).andExpect { status { isForbidden() } }
        postAs(rookie.token, "/seminars/$seminarId/sessions", sessionBody()).andExpect { status { isForbidden() } }

        val sessionId = responseId(
            postAs(staff.token, "/seminars/$seminarId/sessions", sessionBody(lectureContent = "Lecture")).andExpect {
                status { isCreated() }
            },
        )

        patchAs(otherStaff.token, "/sessions/$sessionId", mapOf("title" to "Other staff")).andExpect { status { isForbidden() } }
        patchAs(rookie.token, "/sessions/$sessionId", mapOf("title" to "Rookie")).andExpect { status { isForbidden() } }

        patchAs(staff.token, "/sessions/$sessionId", """{"title":"Updated by staff","lectureContent":null}""").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(sessionId) }
            jsonPath("$.title") { value("Updated by staff") }
            jsonPath("$.lectureContent") { value(null) }
            jsonPath("$.assignmentContent") { value("Assignment content") }
        }

        patchAs(adminToken(), "/sessions/$sessionId", mapOf("location" to "Room 301")).andExpect {
            status { isOk() }
            jsonPath("$.location") { value("Room 301") }
            jsonPath("$.title") { value("Updated by staff") }
        }
    }

    @Test
    fun `회차는 와장과 담당 운영진, 수강 중인 루키만 조회할 수 있다`() {
        val seminarId = createSeminar()
        val sessionId = createSession(seminarId)
        val staff = approvedStaff(seminarId)
        val otherStaff = approvedStaff(createSeminar())
        val enrolled = approvedRookie()
        val notEnrolled = approvedRookie()
        enroll(seminarId, enrolled.token).andExpect { status { isCreated() } }

        for (token in listOf(notEnrolled.token, otherStaff.token)) {
            getAs(token, "/seminars/$seminarId/sessions").andExpect { status { isForbidden() } }
            getAs(token, "/sessions/$sessionId").andExpect { status { isForbidden() } }
        }

        for (token in listOf(enrolled.token, staff.token)) {
            getAs(token, "/seminars/$seminarId/sessions").andExpect {
                status { isOk() }
                jsonPath("$[0].id") { value(sessionId) }
            }
            getAs(token, "/sessions/$sessionId").andExpect {
                status { isOk() }
                jsonPath("$.id") { value(sessionId) }
            }
        }
    }

    @Test
    fun `수강 신청과 취소, 내 수강 목록은 루키만 사용할 수 있다`() {
        val firstSeminarId = createSeminar()
        val secondSeminarId = createSeminar()
        val staff = approvedStaff(firstSeminarId)

        enroll(firstSeminarId, staff.token).andExpect { status { isForbidden() } }
        getAs(staff.token, "/users/me/enrollments").andExpect { status { isForbidden() } }

        val rookie = approvedRookie()
        enroll(firstSeminarId, rookie.token).andExpect { status { isCreated() } }
        val secondEnrollmentId = responseId(enroll(secondSeminarId, rookie.token).andExpect { status { isCreated() } })

        getAs(rookie.token, "/users/me/enrollments").andExpect {
            status { isOk() }
            jsonPath("$.totalElements") { value(2) }
            jsonPath("$.content[0].id") { value(secondEnrollmentId) }
            jsonPath("$.content[0].seminar.id") { value(secondSeminarId) }
            jsonPath("$.content[0].graceDaysRemaining") { value(3) }
            jsonPath("$.content[1].seminar.id") { value(firstSeminarId) }
        }
    }

    @Test
    fun `반려된 사용자도 사용자 조회와 내 수강 목록에서 403을 반환한다`() {
        val other = approvedRookie()
        val rejected = rejectedRookie()

        getAs(rejected.token, "/users/${other.id}").andExpect { status { isForbidden() } }
        getAs(rejected.token, "/users/${rejected.id}").andExpect { status { isForbidden() } }
        getAs(rejected.token, "/users/me/enrollments").andExpect { status { isForbidden() } }
    }

    @Test
    fun `승인 전의 담당 운영진은 회차를 수정할 수 없다`() {
        val seminarId = createSeminar()
        val sessionId = createSession(seminarId)
        val pendingStaff = pendingStaff(seminarId)

        patchAs(pendingStaff.token, "/sessions/$sessionId", mapOf("title" to "Pending staff")).andExpect { status { isForbidden() } }
    }

    @Test
    fun `승인된 사용자는 역할과 관계없이 다른 사용자와 세미나를 조회할 수 있다`() {
        val seminarId = createSeminar()
        val staff = approvedStaff(seminarId)
        val rookie = approvedRookie()

        getAs(rookie.token, "/users/${staff.id}").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(staff.id) }
            jsonPath("$.role") { value("STAFF") }
            jsonPath("$.seminarId") { value(seminarId) }
        }
        getAs(staff.token, "/users/${rookie.id}").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(rookie.id) }
        }
        getAs(staff.token, "/seminars/$seminarId").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(seminarId) }
        }
    }
}
