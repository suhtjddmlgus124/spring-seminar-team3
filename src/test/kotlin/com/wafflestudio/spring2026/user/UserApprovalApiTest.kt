package com.wafflestudio.spring2026.user

import com.wafflestudio.spring2026.support.ApiIntegrationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserApprovalApiTest : ApiIntegrationTest() {
    @Test
    fun `루키가 가입 신청하면 대기 상태의 사용자 정보를 조회할 수 있다`() {
        val email = uniqueEmail()
        val rookieId = signupRookie(email)

        getAs(adminToken(), "/users/$rookieId").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(rookieId) }
            jsonPath("$.email") { value(email) }
            jsonPath("$.role") { value("ROOKIE") }
            jsonPath("$.status") { value("PENDING") }
            jsonPath("$.createdAt") { exists() }
        }
    }

    @Test
    fun `운영진은 담당 세미나와 함께 가입 신청한다`() {
        val seminarId = createSeminar()
        val staffId = signupStaff(seminarId)

        getAs(adminToken(), "/users/$staffId").andExpect {
            status { isOk() }
            jsonPath("$.role") { value("STAFF") }
            jsonPath("$.seminarId") { value(seminarId) }
            jsonPath("$.status") { value("PENDING") }
        }
    }

    @Test
    fun `대기 중인 가입 신청은 승인 또는 반려할 수 있다`() {
        val approvedUserId = signupRookie()
        val rejectedUserId = signupRookie()

        approve(approvedUserId).andExpect {
            status { isOk() }
            jsonPath("$.id") { value(approvedUserId) }
            jsonPath("$.status") { value("APPROVED") }
        }

        approve(rejectedUserId, "REJECTED").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(rejectedUserId) }
            jsonPath("$.status") { value("REJECTED") }
        }
    }

    @Test
    fun `유효하지 않은 가입 또는 심사 요청은 400을 반환한다`() {
        postAs(
            null,
            "/auth/signup",
            """{"email":"invalid","password":"","name":"","githubUsername":"","role":"ADMIN"}""",
        ).andExpect {
            status { isBadRequest() }
        }

        val pendingUserId = signupRookie()
        approve(pendingUserId, "PENDING").andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `가입과 심사의 대표 오류 상태를 반환한다`() {
        postAs(
            null,
            "/auth/signup",
            """{"email":"${uniqueEmail()}","password":"password","name":"Staff","githubUsername":"staff","role":"STAFF","seminarId":999999}""",
        ).andExpect {
            status { isNotFound() }
        }

        val email = uniqueEmail()
        signupRookie(email)
        postAs(
            null,
            "/auth/signup",
            """{"email":"$email","password":"password","name":"Duplicate","githubUsername":"duplicate","role":"ROOKIE"}""",
        ).andExpect {
            status { isConflict() }
        }

        val admin = adminToken()
        getAs(admin, "/users/999999").andExpect {
            status { isNotFound() }
        }

        approve(999999, token = admin).andExpect {
            status { isNotFound() }
        }

        val approvedUserId = signupRookie()
        approve(approvedUserId, token = admin).andExpect { status { isOk() } }
        approve(approvedUserId, "REJECTED", admin).andExpect {
            status { isConflict() }
        }
    }

    @Test
    fun `가입 신청 목록의 status 나 role 이 정해진 값이 아니면 400을 반환한다`() {
        val admin = adminToken()

        getAs(admin, "/users?status=UNKNOWN").andExpect { status { isBadRequest() } }
        getAs(admin, "/users?role=ADMIN").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `가입 신청 목록은 상태로 거르고 최근 가입 순으로 보여 주며 와장은 나오지 않는다`() {
        val admin = adminToken()
        val olderId = signupRookie()
        val newerId = signupRookie()
        val approvedId = signupRookie()
        approve(approvedId, token = admin).andExpect { status { isOk() } }

        val pendingBody = getAs(admin, "/users?status=PENDING&role=ROOKIE&size=100").andExpect {
            status { isOk() }
            jsonPath("$.content[0].id") { value(newerId) }
            jsonPath("$.content[1].id") { value(olderId) }
        }.andReturn().response.contentAsString
        val pending = objectMapper.readTree(pendingBody).path("content").toList()
        assertTrue(pending.all { it.path("status").asString() == "PENDING" }, "status=PENDING 이면 대기 중인 신청만 보여야 합니다.")
        assertTrue(pending.none { it.path("id").asLong() == approvedId })

        var page = 0
        var totalPages = 1
        val approvedIds = mutableListOf<Long>()
        while (page < totalPages) {
            val body = responseJson(
                getAs(admin, "/users?status=APPROVED&size=100&page=$page").andExpect {
                    status { isOk() }
                },
            )
            val content = body.path("content").toList()
            totalPages = body.path("totalPages").asInt()
            assertTrue(content.none { it.path("email").asString() == ADMIN_EMAIL }, "와장 계정은 가입 신청 목록에 나오지 않아야 합니다.")
            assertTrue(content.none { it.path("role").asString() == "ADMIN" })
            approvedIds += content.map { it.path("id").asLong() }
            page++
        }
        assertTrue(approvedId in approvedIds)
    }

    @Test
    fun `가입 신청 목록은 page 와 size 에 맞춰 나눠 보여 준다`() {
        val admin = adminToken()
        val seminarId = createSeminar()
        val newestFirst = List(5) { signupStaff(seminarId) }
            .onEach { approve(it, "REJECTED", admin).andExpect { status { isOk() } } }
            .reversed()

        fun requestPage(page: Int) = getAs(admin, "/users?status=REJECTED&role=STAFF&page=$page&size=2").andExpect {
            status { isOk() }
            jsonPath("$.page") { value(page) }
            jsonPath("$.size") { value(2) }
        }

        val first = requestPage(0)
        val totalElements = responseJson(first).path("totalElements").asInt()
        val totalPages = responseJson(first).path("totalPages").asInt()
        assertEquals((totalElements + 1) / 2, totalPages, "totalPages 는 totalElements 를 size 로 나눠 올림한 값이어야 합니다.")

        assertEquals(newestFirst.subList(0, 2), contentIds(first), "page=0 의 항목이 다릅니다.")
        assertEquals(newestFirst.subList(2, 4), contentIds(requestPage(1)), "page=1 의 항목이 다릅니다.")
        assertEquals(newestFirst[4], contentIds(requestPage(2)).first(), "page=2 의 첫 항목이 다릅니다.")

        val lastPage = totalPages - 1
        assertEquals(totalElements - lastPage * 2, contentIds(requestPage(lastPage)).size, "마지막 페이지에는 남은 항목만 있어야 합니다.")
    }
}
