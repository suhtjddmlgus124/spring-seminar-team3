package com.wafflestudio.spring2026.auth

import com.wafflestudio.spring2026.support.ApiIntegrationTest
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.get
import java.time.Instant
import java.util.Date
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthenticationApiTest : ApiIntegrationTest() {
    @Test
    fun `가입한 사용자는 승인 전에도 로그인하고 내 정보를 조회할 수 있다`() {
        val pending = pendingRookie()
        getAs(pending.token, "/users/me").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(pending.id) }
            jsonPath("$.role") { value("ROOKIE") }
            jsonPath("$.status") { value("PENDING") }
        }

        val rejected = rejectedRookie()
        getAs(rejected.token, "/users/me").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(rejected.id) }
            jsonPath("$.status") { value("REJECTED") }
        }

        getAs(adminToken(), "/users/me").andExpect {
            status { isOk() }
            jsonPath("$.email") { value(ADMIN_EMAIL) }
            jsonPath("$.role") { value("ADMIN") }
        }
    }

    @Test
    fun `이메일이나 비밀번호가 틀리면 401, 비어 있으면 400을 반환한다`() {
        val email = uniqueEmail()
        signupRookie(email)

        loginRequest(email, PASSWORD).andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { isNotEmpty() }
        }

        loginRequest(email, "wrong-password").andExpect {
            status { isUnauthorized() }
        }

        loginRequest(uniqueEmail(), PASSWORD).andExpect {
            status { isUnauthorized() }
        }

        loginRequest(email, "   ").andExpect {
            status { isBadRequest() }
        }

        loginRequest("   ", PASSWORD).andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `토큰이 없거나 Bearer 형식이 아니면 401을 반환한다`() {
        val seminarId = createSeminar()
        val validToken = approvedRookie().token

        getAs(null, "/users/me").andExpect { status { isUnauthorized() } }
        getAs(null, "/users/me/enrollments").andExpect { status { isUnauthorized() } }
        getAs(null, "/seminars/$seminarId").andExpect { status { isUnauthorized() } }
        postAs(null, "/seminars/$seminarId/enrollments").andExpect { status { isUnauthorized() } }

        mockMvc.get("/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Token $validToken")
        }.andExpect {
            status { isUnauthorized() }
        }

        getAs("not-a-jwt", "/users/me").andExpect { status { isUnauthorized() } }

        postAs(null, "/seminars", """{"title":""}""").andExpect { status { isUnauthorized() } }
        getAs(null, "/seminars/999999").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `서명이 올바르지 않은 토큰은 401을 반환한다`() {
        val rookie = approvedRookie()

        val (header, payload, signature) = rookie.token.split(".")
        val replaced = if (signature.first() == 'A') 'B' else 'A'
        val tampered = "$header.$payload.$replaced${signature.drop(1)}"
        getAs(tampered, "/users/me").andExpect { status { isUnauthorized() } }

        val foreignKey = Keys.hmacShaKeyFor("this-is-not-the-server-secret-key-0123456789".toByteArray())
        val forged = Jwts.builder()
            .subject(rookie.id.toString())
            .claim("userId", rookie.id)
            .claim("role", "ADMIN")
            .issuedAt(Date())
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(foreignKey)
            .compact()
        getAs(forged, "/users/me").andExpect { status { isUnauthorized() } }
        postAs(forged, "/seminars", mapOf("title" to "Forged")).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `발급한 토큰에는 sub, iat, exp 가 있고 1시간 뒤 만료된다`() {
        val email = uniqueEmail()
        val userId = signupRookie(email)

        val payload = payloadOf(login(email))

        assertEquals(userId.toString(), payload.path("sub").asString(), "sub 는 사용자 ID 문자열이어야 합니다.")
        assertTrue(payload.path("iat").isIntegralNumber, "iat 는 초 단위 Unix 시각(정수)이어야 합니다.")
        assertTrue(payload.path("exp").isIntegralNumber, "exp 는 초 단위 Unix 시각(정수)이어야 합니다.")

        val issuedAt = payload.path("iat").asLong()
        assertTrue(abs(issuedAt - Instant.now().epochSecond) < 60, "iat 는 발급한 시각이어야 합니다. (iat=$issuedAt)")
        assertEquals(3600, payload.path("exp").asLong() - issuedAt, "토큰은 발급 후 1시간(3600초) 뒤 만료되어야 합니다.")
    }

    @Test
    fun `만료된 토큰은 401을 반환한다`() {
        val rookie = approvedRookie()
        val now = Instant.now()

        val stillValid = resign(rookie.token, issuedAt = now.minusSeconds(60), expiresAt = now.plusSeconds(3540))
        getAs(stillValid, "/users/me").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(rookie.id) }
        }

        val expired = resign(rookie.token, issuedAt = now.minusSeconds(7200), expiresAt = now.minusSeconds(3600))
        getAs(expired, "/users/me").andExpect { status { isUnauthorized() } }
        getAs(expired, "/users/me/enrollments").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `로그아웃한 토큰은 더 이상 쓸 수 없다`() {
        val seminarId = createSeminar()
        val rookie = approvedRookie()
        getAs(rookie.token, "/users/me").andExpect { status { isOk() } }

        logout(rookie.token).andExpect {
            status { isNoContent() }
        }

        getAs(rookie.token, "/users/me").andExpect { status { isUnauthorized() } }
        getAs(rookie.token, "/seminars/$seminarId").andExpect { status { isUnauthorized() } }
        enroll(seminarId, rookie.token).andExpect { status { isUnauthorized() } }
        logout(rookie.token).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `가입 승인 전이어도 로그아웃할 수 있고, 토큰 없는 로그아웃은 401을 반환한다`() {
        val pending = pendingRookie()
        logout(pending.token).andExpect { status { isNoContent() } }
        getAs(pending.token, "/users/me").andExpect { status { isUnauthorized() } }

        logout(null).andExpect { status { isUnauthorized() } }
        logout("not-a-jwt").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `세미나 목록은 로그인 없이 조회하고 제목으로 검색할 수 있다`() {
        val keyword = unique("Keyword")
        val firstId = createSeminar(title = "$keyword first")
        val secondId = createSeminar(title = "$keyword second")

        getAs(null, "/seminars?keyword=$keyword").andExpect {
            status { isOk() }
            jsonPath("$.totalElements") { value(2) }
            jsonPath("$.totalPages") { value(1) }
            jsonPath("$.page") { value(0) }
            jsonPath("$.content.length()") { value(2) }
            jsonPath("$.content[0].id") { value(firstId) }
            jsonPath("$.content[1].id") { value(secondId) }
            jsonPath("$.content[0].status") { value("OPEN") }
            jsonPath("$.content[0].enrolledCount") { value(0) }
        }
    }
}
