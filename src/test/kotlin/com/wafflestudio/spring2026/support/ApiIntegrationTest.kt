package com.wafflestudio.spring2026.support

import tools.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.testcontainers.containers.MySQLContainer
import java.time.OffsetDateTime
import java.time.ZoneOffset
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

        @JvmStatic
        @DynamicPropertySource
        fun mysqlProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
        }
    }

    protected fun unique(prefix: String): String = "$prefix-${sequence.incrementAndGet()}"

    protected fun uniqueEmail(): String = "${unique("rookie")}@example.com"

    protected fun now(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC).withNano(0)

    protected fun responseId(result: ResultActionsDsl): Long =
        objectMapper.readTree(result.andReturn().response.contentAsString).path("id").asLong()

    protected fun signupRookie(email: String = uniqueEmail()): Long =
        signup(email = email, role = "ROOKIE")

    protected fun signupStaff(seminarId: Long, email: String = uniqueEmail()): Long =
        signup(email = email, role = "STAFF", seminarId = seminarId)

    protected fun signup(email: String, role: String, seminarId: Long? = null): Long {
        val result = mockMvc.post("/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "email" to email,
                    "password" to "password",
                    "name" to unique("Waffle"),
                    "githubUsername" to unique("github"),
                    "role" to role,
                    "seminarId" to seminarId,
                ),
            )
        }.andExpect {
            status { isCreated() }
        }

        return responseId(result)
    }

    protected fun approve(userId: Long, status: String = "APPROVED"): ResultActionsDsl =
        mockMvc.patch("/users/$userId/approval") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("status" to status))
        }

    protected fun createSeminar(
        title: String = unique("Seminar"),
        description: String? = "Seminar description",
        capacity: Int = 10,
        applyStartAt: OffsetDateTime = now().minusDays(1),
        applyEndAt: OffsetDateTime = now().plusDays(1),
        totalGraceDays: Int = 3,
    ): Long {
        val result = mockMvc.post("/seminars") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "title" to title,
                    "description" to description,
                    "capacity" to capacity,
                    "applyStartAt" to applyStartAt.toString(),
                    "applyEndAt" to applyEndAt.toString(),
                    "totalGraceDays" to totalGraceDays,
                ),
            )
        }.andExpect {
            status { isCreated() }
        }

        return responseId(result)
    }

    protected fun createSession(
        seminarId: Long,
        startsAt: OffsetDateTime,
        title: String = unique("Session"),
        lectureContent: String? = "Lecture content",
        assignmentContent: String? = "Assignment content",
    ): Long {
        val result = mockMvc.post("/seminars/$seminarId/sessions") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "title" to title,
                    "startsAt" to startsAt.toString(),
                    "location" to "Room 208",
                    "assignmentTitle" to unique("Assignment"),
                    "lectureContent" to lectureContent,
                    "assignmentContent" to assignmentContent,
                ),
            )
        }.andExpect {
            status { isCreated() }
        }

        return responseId(result)
    }

    protected fun enroll(seminarId: Long, rookieId: Long): ResultActionsDsl =
        mockMvc.post("/seminars/$seminarId/enrollments") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("rookieId" to rookieId))
        }
}
