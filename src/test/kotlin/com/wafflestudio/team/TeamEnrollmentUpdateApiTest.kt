package com.wafflestudio.spring2026.team

import com.wafflestudio.spring2026.support.Week4ApiIntegrationTest
import com.wafflestudio.spring2026.waggle.model.Participation
import com.wafflestudio.spring2026.waggle.model.ParticipationAssignmentStatus
import com.wafflestudio.spring2026.waggle.model.ParticipationAttendanceStatus
import com.wafflestudio.spring2026.waggle.repository.EnrollmentRepository
import com.wafflestudio.spring2026.waggle.repository.ParticipationRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TeamEnrollmentUpdateApiTest : Week4ApiIntegrationTest() {
    @Autowired
    private lateinit var enrollmentRepository: EnrollmentRepository

    @Autowired
    private lateinit var participationRepository: ParticipationRepository

    @Test
    fun `보낸 항목만 수정하고 신청 정보와 출석 기록을 유지한다`() {
        val seminarId = createSeminar(totalGraceDays = 4)
        val sessionId = createSession(seminarId)
        val enrolled = enrolledRookie(seminarId)
        val admin = adminToken()

        val before = assertNotNull(
            enrollmentRepository.findByIdOrNull(enrolled.enrollmentId),
        )

        val record = participationRepository.save(
            Participation(
                attendanceStatus = ParticipationAttendanceStatus.PRESENT,
                assignmentStatus = ParticipationAssignmentStatus.PASSED,
                enrollmentId = enrolled.enrollmentId,
                sessionId = sessionId,
            ),
        )

        val response = responseJson(
            updateEnrollment(
                seminarId,
                enrolled.enrollmentId,
                admin,
                mapOf("graceDaysRemaining" to 0),
            ).andExpect {
                status { isOk() }
                jsonPath("$.id") { value(enrolled.enrollmentId) }
                jsonPath("$.graceDaysRemaining") { value(0) }
                jsonPath("$.dropped") { value(false) }
            },
        )

        assertEquals(
            before.createdAt,
            OffsetDateTime.parse(
                response.path("createdAt").asString(),
            ).toInstant(),
        )

        assertEquals(
            before.copy(graceDaysRemaining = 0),
            enrollmentRepository.findByIdOrNull(enrolled.enrollmentId),
        )

        repeat(2) {
            updateEnrollment(
                seminarId,
                enrolled.enrollmentId,
                admin,
                mapOf("dropped" to true),
            ).andExpect {
                status { isOk() }
            }
        }

        assertEquals(
            before.copy(graceDaysRemaining = 0, isFailed = true),
            enrollmentRepository.findByIdOrNull(enrolled.enrollmentId),
        )

        assertEquals(
            record,
            participationRepository.findByIdOrNull(record.id!!),
        )

        val mine = myEnrollments(enrolled.actor).single()
        assertEquals(enrolled.enrollmentId, mine.path("id").asLong())
        assertEquals(0, mine.path("graceDaysRemaining").asInt())
        assertEquals(true, mine.path("dropped").asBoolean())

        updateEnrollment(
            seminarId,
            enrolled.enrollmentId,
            admin,
            mapOf(
                "graceDaysRemaining" to 2,
                "dropped" to false,
                "ignored" to true,
            ),
        ).andExpect {
            status { isOk() }
        }

        assertEquals(
            before.copy(graceDaysRemaining = 2, isFailed = false),
            enrollmentRepository.findByIdOrNull(enrolled.enrollmentId),
        )
    }

    @Test
    fun `Grace Day는 0부터 세미나의 총량까지 수정할 수 있다`() {
        val admin = adminToken()

        for (total in listOf(0, 4)) {
            val seminarId = createSeminar(totalGraceDays = total)
            val enrolled = enrolledRookie(seminarId)

            for (graceDays in listOf(0, total).distinct()) {
                updateEnrollment(
                    seminarId,
                    enrolled.enrollmentId,
                    admin,
                    mapOf("graceDaysRemaining" to graceDays),
                ).andExpect {
                    status { isOk() }
                    jsonPath("$.graceDaysRemaining") { value(graceDays) }
                }
            }

            val before = enrollmentRepository.findByIdOrNull(
                enrolled.enrollmentId,
            )

            for (graceDays in listOf(-1, total + 1)) {
                updateEnrollment(
                    seminarId,
                    enrolled.enrollmentId,
                    admin,
                    mapOf(
                        "graceDaysRemaining" to graceDays,
                        "dropped" to true,
                    ),
                ).andExpect {
                    status { isBadRequest() }
                }

                assertEquals(
                    before,
                    enrollmentRepository.findByIdOrNull(enrolled.enrollmentId),
                )
            }
        }
    }

    @Test
    fun `빈 요청과 null 및 잘못된 타입은 어느 값도 수정하지 않는다`() {
        val seminarId = createSeminar()
        val enrolled = enrolledRookie(seminarId)
        val admin = adminToken()

        val before = assertNotNull(
            enrollmentRepository.findByIdOrNull(enrolled.enrollmentId),
        )

        val bodies = listOf(
            """{}""",
            """{"ignored":true}""",
            """{"graceDaysRemaining":null,"dropped":true}""",
            """{"graceDaysRemaining":0,"dropped":null}""",
            """{"graceDaysRemaining":1.5,"dropped":true}""",
            """{"graceDaysRemaining":"1","dropped":true}""",
            """{"graceDaysRemaining":2147483648,"dropped":true}""",
            """{"graceDaysRemaining":0,"dropped":1}""",
            """{"graceDaysRemaining":0,"dropped":"true"}""",
            """[]""",
        )

        for (body in bodies) {
            updateEnrollment(
                seminarId,
                enrolled.enrollmentId,
                admin,
                body,
            ).andExpect {
                status { isBadRequest() }
            }

            assertEquals(
                before,
                enrollmentRepository.findByIdOrNull(enrolled.enrollmentId),
                body,
            )
        }
    }

    @Test
    fun `와장과 담당 운영진만 수정하고 다른 세미나의 신청은 수정할 수 없다`() {
        val seminarId = createSeminar()
        val otherSeminarId = createSeminar()
        val enrolled = enrolledRookie(seminarId)
        val other = enrolledRookie(otherSeminarId)

        val admin = adminToken()
        val staff = approvedStaff(seminarId)
        val otherStaff = approvedStaff(otherSeminarId)
        val pending = pendingStaff(seminarId)

        for (token in listOf(admin, staff.token)) {
            updateEnrollment(
                seminarId,
                enrolled.enrollmentId,
                token,
                mapOf("dropped" to true),
            ).andExpect {
                status { isOk() }
            }
        }

        val before = enrollmentRepository.findByIdOrNull(
            enrolled.enrollmentId,
        )

        patchOperation(
            null,
            "/seminars/$seminarId/enrollments/${enrolled.enrollmentId}",
            mapOf("dropped" to false),
        ).andExpect {
            status { isUnauthorized() }
        }

        for (token in listOf(enrolled.actor.token, otherStaff.token, pending.token)) {
            updateEnrollment(
                seminarId,
                enrolled.enrollmentId,
                token,
                mapOf("dropped" to false),
            ).andExpect {
                status { isForbidden() }
            }
        }

        val missingTargets = listOf(
            seminarId to other.enrollmentId,
            seminarId to MISSING_ID,
            MISSING_ID to enrolled.enrollmentId,
        )

        for ((targetSeminarId, targetEnrollmentId) in missingTargets) {
            updateEnrollment(
                targetSeminarId,
                targetEnrollmentId,
                admin,
                mapOf("dropped" to false),
            ).andExpect {
                status { isNotFound() }
            }
        }

        assertEquals(
            before,
            enrollmentRepository.findByIdOrNull(enrolled.enrollmentId),
        )

        assertEquals(
            false,
            enrollmentRepository.findByIdOrNull(other.enrollmentId)?.isFailed,
        )
    }
}