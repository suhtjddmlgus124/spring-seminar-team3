package com.wafflestudio.spring2026.support

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.testcontainers.containers.MySQLContainer
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.Date
import java.util.concurrent.atomic.AtomicLong

@SpringBootTest
@AutoConfigureMockMvc
abstract class ApiIntegrationTest {
    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    companion object {
        val mysql = MySQLContainer("mysql:8.4")
            .withDatabaseName("waggle_test")
            .withUsername("waggle")
            .withPassword("waggle")
            .apply { start() }

        private val sequence = AtomicLong()

        const val ADMIN_EMAIL = "admin@wafflestudio.com"
        const val ADMIN_PASSWORD = "waggle1234"

        const val PASSWORD = "password"

        const val TEST_JWT_SECRET = "waggle-test-jwt-secret-0123456789abcdef"

        @JvmStatic
        @DynamicPropertySource
        fun mysqlProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("jwt.secret") { TEST_JWT_SECRET }
        }
    }

    protected data class Actor(
        val id: Long,
        val token: String,
    )

    protected fun unique(prefix: String): String = "$prefix-${sequence.incrementAndGet()}"

    protected fun uniqueEmail(): String = "${unique("rookie")}@example.com"

    protected fun now(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC).withNano(0)

    protected fun responseJson(result: ResultActionsDsl): JsonNode =
        objectMapper.readTree(result.andReturn().response.contentAsString)

    protected fun responseId(result: ResultActionsDsl): Long = responseJson(result).path("id").asLong()

    protected fun contentIds(result: ResultActionsDsl): List<Long> =
        responseJson(result).path("content").toList().map { it.path("id").asLong() }

    protected fun loginRequest(email: String, password: String): ResultActionsDsl =
        mockMvc.post("/auth/login") {
            json(mapOf("email" to email, "password" to password))
        }

    protected fun login(email: String, password: String = PASSWORD): String {
        val response = loginRequest(email, password).andReturn().response
        check(response.status == 200) {
            "$email 로 로그인하지 못했습니다. (status=${response.status})" +
                if (email == ADMIN_EMAIL) " POST /auth/login 구현과, 와장 계정을 넣는 Migration 을 확인하세요." else ""
        }

        return objectMapper.readTree(response.contentAsString).path("accessToken").asString()
    }

    protected fun adminToken(): String = login(ADMIN_EMAIL, ADMIN_PASSWORD)

    protected fun logout(token: String?): ResultActionsDsl = postAs(token, "/auth/logout")

    protected fun payloadOf(token: String): JsonNode =
        objectMapper.readTree(Base64.getUrlDecoder().decode(token.split(".")[1]))

    protected fun resign(
        token: String,
        issuedAt: Instant,
        expiresAt: Instant,
    ): String {
        @Suppress("UNCHECKED_CAST")
        val claims = objectMapper.convertValue(payloadOf(token), Map::class.java) as Map<String, Any?>

        return Jwts.builder()
            .claims(claims)
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            .signWith(Keys.hmacShaKeyFor(TEST_JWT_SECRET.toByteArray()), Jwts.SIG.HS256)
            .compact()
    }

    protected fun MockHttpServletRequestDsl.bearer(token: String) {
        header(HttpHeaders.AUTHORIZATION, "Bearer $token")
    }

    protected fun MockHttpServletRequestDsl.json(body: Any) {
        contentType = MediaType.APPLICATION_JSON
        content = if (body is String) body else objectMapper.writeValueAsString(body)
    }

    protected fun getAs(token: String?, path: String): ResultActionsDsl =
        mockMvc.get(path) { token?.let { bearer(it) } }

    protected fun postAs(token: String?, path: String, body: Any? = null): ResultActionsDsl =
        mockMvc.post(path) {
            token?.let { bearer(it) }
            body?.let { json(it) }
        }

    protected fun patchAs(token: String?, path: String, body: Any): ResultActionsDsl =
        mockMvc.patch(path) {
            token?.let { bearer(it) }
            json(body)
        }

    protected fun deleteAs(token: String?, path: String): ResultActionsDsl =
        mockMvc.delete(path) { token?.let { bearer(it) } }

    protected fun signupRookie(email: String = uniqueEmail()): Long =
        signup(email = email, role = "ROOKIE")

    protected fun signupStaff(seminarId: Long, email: String = uniqueEmail()): Long =
        signup(email = email, role = "STAFF", seminarId = seminarId)

    protected fun signup(email: String, role: String, seminarId: Long? = null): Long {
        val result = postAs(
            null,
            "/auth/signup",
            mapOf(
                "email" to email,
                "password" to PASSWORD,
                "name" to unique("Waffle"),
                "githubUsername" to unique("github"),
                "role" to role,
                "seminarId" to seminarId,
            ),
        ).andExpect {
            status { isCreated() }
        }

        return responseId(result)
    }

    protected fun approve(
        userId: Long,
        status: String = "APPROVED",
        token: String = adminToken(),
    ): ResultActionsDsl = patchAs(token, "/users/$userId/approval", mapOf("status" to status))

    protected fun approvedRookie(): Actor = signedUp(role = "ROOKIE", seminarId = null, review = "APPROVED")

    protected fun approvedStaff(seminarId: Long): Actor = signedUp(role = "STAFF", seminarId = seminarId, review = "APPROVED")

    protected fun pendingRookie(): Actor = signedUp(role = "ROOKIE", seminarId = null, review = null)

    protected fun pendingStaff(seminarId: Long): Actor = signedUp(role = "STAFF", seminarId = seminarId, review = null)

    protected fun rejectedRookie(): Actor = signedUp(role = "ROOKIE", seminarId = null, review = "REJECTED")

    private fun signedUp(role: String, seminarId: Long?, review: String?): Actor {
        val email = uniqueEmail()
        val id = signup(email = email, role = role, seminarId = seminarId)
        if (review != null) {
            approve(id, review).andExpect { status { isOk() } }
        }

        return Actor(id = id, token = login(email))
    }

    protected fun createSeminar(
        title: String = unique("Seminar"),
        description: String? = "Seminar description",
        capacity: Int = 10,
        applyStartAt: OffsetDateTime = now().minusDays(1),
        applyEndAt: OffsetDateTime = now().plusDays(1),
        totalGraceDays: Int = 3,
    ): Long {
        val result = postAs(
            adminToken(),
            "/seminars",
            mapOf(
                "title" to title,
                "description" to description,
                "capacity" to capacity,
                "applyStartAt" to applyStartAt.toString(),
                "applyEndAt" to applyEndAt.toString(),
                "totalGraceDays" to totalGraceDays,
            ),
        ).andExpect {
            status { isCreated() }
        }

        return responseId(result)
    }

    protected fun sessionBody(
        startsAt: OffsetDateTime = now().plusDays(1),
        title: String = unique("Session"),
        lectureContent: String? = "Lecture content",
        assignmentContent: String? = "Assignment content",
    ): Map<String, Any?> =
        mapOf(
            "title" to title,
            "startsAt" to startsAt.toString(),
            "location" to "Room 208",
            "assignmentTitle" to unique("Assignment"),
            "lectureContent" to lectureContent,
            "assignmentContent" to assignmentContent,
        )

    protected fun createSession(
        seminarId: Long,
        startsAt: OffsetDateTime = now().plusDays(1),
        title: String = unique("Session"),
        lectureContent: String? = "Lecture content",
        assignmentContent: String? = "Assignment content",
    ): Long {
        val result = postAs(
            adminToken(),
            "/seminars/$seminarId/sessions",
            sessionBody(startsAt, title, lectureContent, assignmentContent),
        ).andExpect {
            status { isCreated() }
        }

        return responseId(result)
    }

    protected fun enroll(seminarId: Long, token: String): ResultActionsDsl =
        postAs(token, "/seminars/$seminarId/enrollments")

    protected fun cancelEnrollment(seminarId: Long, token: String): ResultActionsDsl =
        deleteAs(token, "/seminars/$seminarId/enrollments/me")
}
