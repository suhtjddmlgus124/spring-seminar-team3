package com.wafflestudio.spring2026.user

import com.wafflestudio.spring2026.support.ApiIntegrationTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import kotlin.test.Test

class UserApprovalApiTest : ApiIntegrationTest() {
    @Test
    fun `루키가 가입 신청하면 대기 상태의 사용자 정보를 조회할 수 있다`() {
        val email = uniqueEmail()
        val rookieId = signupRookie(email)

        mockMvc.get("/users/$rookieId").andExpect {
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

        mockMvc.get("/users/$staffId").andExpect {
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
        mockMvc.post("/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"invalid","password":"","name":"","githubUsername":"","role":"ADMIN"}"""
        }.andExpect {
            status { isBadRequest() }
        }

        val pendingUserId = signupRookie()
        mockMvc.patch("/users/$pendingUserId/approval") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"status":"PENDING"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `가입과 심사의 대표 오류 상태를 반환한다`() {
        mockMvc.post("/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"${uniqueEmail()}","password":"password","name":"Staff","githubUsername":"staff","role":"STAFF","seminarId":999999}"""
        }.andExpect {
            status { isNotFound() }
        }

        val email = uniqueEmail()
        signupRookie(email)
        mockMvc.post("/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"password","name":"Duplicate","githubUsername":"duplicate","role":"ROOKIE"}"""
        }.andExpect {
            status { isConflict() }
        }

        mockMvc.get("/users/999999").andExpect {
            status { isNotFound() }
        }

        mockMvc.patch("/users/999999/approval") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"status":"APPROVED"}"""
        }.andExpect {
            status { isNotFound() }
        }

        val approvedUserId = signupRookie()
        approve(approvedUserId).andExpect { status { isOk() } }
        approve(approvedUserId, "REJECTED").andExpect {
            status { isConflict() }
        }
    }
}
