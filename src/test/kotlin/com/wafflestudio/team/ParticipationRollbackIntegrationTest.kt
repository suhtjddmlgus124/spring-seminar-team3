package com.wafflestudio.spring2026.team

import com.wafflestudio.spring2026.support.ApiIntegrationTest
import com.wafflestudio.spring2026.waggle.dto.session.FieldPatch
import com.wafflestudio.spring2026.waggle.dto.session.RecordUpdateCommand
import com.wafflestudio.spring2026.waggle.model.Participation
import com.wafflestudio.spring2026.waggle.model.ParticipationAssignmentStatus
import com.wafflestudio.spring2026.waggle.model.ParticipationAttendanceStatus
import com.wafflestudio.spring2026.waggle.repository.ParticipationRepository
import com.wafflestudio.spring2026.waggle.repository.SessionRepository
import com.wafflestudio.spring2026.waggle.service.ParticipationService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.data.repository.findByIdOrNull
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@Import(ParticipationRollbackIntegrationTest.RollbackTestConfig::class)
class ParticipationRollbackIntegrationTest : ApiIntegrationTest() {
    @Autowired
    private lateinit var participationService: ParticipationService

    @Autowired
    private lateinit var participationRepository: ParticipationRepository

    @Autowired
    private lateinit var sessionRepository: SessionRepository

    @Autowired
    private lateinit var failingRepository: FailingParticipationRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `첫 저장 후 두 번째 저장이 실패하면 모든 기록을 원래대로 되돌린다`() {
        val seminarId = createSeminar()
        val sessionId = createSession(seminarId)
        val first = approvedRookie()
        val second = approvedRookie()

        val firstEnrollmentId = responseId(
            enroll(seminarId, first.token).andExpect {
                status { isCreated() }
            },
        )

        val secondEnrollmentId = responseId(
            enroll(seminarId, second.token).andExpect {
                status { isCreated() }
            },
        )

        val enrollmentIds = listOf(firstEnrollmentId, secondEnrollmentId)
        val session = assertNotNull(
            sessionRepository.findByIdOrNull(sessionId),
        )

        for (enrollmentId in enrollmentIds) {
            participationRepository.save(
                Participation(
                    attendanceStatus = ParticipationAttendanceStatus.ABSENT,
                    assignmentStatus = ParticipationAssignmentStatus.NOT_SUBMITTED,
                    enrollmentId = enrollmentId,
                    sessionId = sessionId,
                ),
            )
        }

        val before = records(sessionId, enrollmentIds)

        val changes = enrollmentIds.map { enrollmentId ->
            RecordUpdateCommand(
                enrollmentId = enrollmentId,
                attendanceStatus = FieldPatch.Set(
                    ParticipationAttendanceStatus.PRESENT,
                ),
                assignmentStatus = FieldPatch.Set(
                    ParticipationAssignmentStatus.PASSED,
                ),
            )
        }

        failingRepository.failOnSecondSave()

        try {
            assertFailsWith<ForcedSaveFailure> {
                participationService.batchUpdateRecords(session, changes)
            }
        } finally {
            failingRepository.stopFailing()
        }

        assertEquals(1, failingRepository.successfulSaves)

        val written = assertNotNull(
            failingRepository.firstSavedRecord,
        )

        assertEquals(
            ParticipationAttendanceStatus.PRESENT,
            written.attendanceStatus,
        )

        assertEquals(
            ParticipationAssignmentStatus.PASSED,
            written.assignmentStatus,
        )

        assertEquals(
            before,
            records(sessionId, enrollmentIds),
        )
    }

    private fun records(
        sessionId: Long,
        enrollmentIds: List<Long>,
    ): List<Participation> = assertNotNull(
        TransactionTemplate(transactionManager).execute {
            participationRepository.findAllBySessionIdAndEnrollmentIdIn(
                sessionId,
                enrollmentIds,
            ).sortedBy { it.id }
        },
    )

    @TestConfiguration(proxyBeanMethods = false)
    class RollbackTestConfig {
        @Bean
        @Primary
        fun failingParticipationRepository(
            @Qualifier("participationRepository")
            repository: ParticipationRepository,
        ): FailingParticipationRepository =
            FailingParticipationRepository(repository)
    }

    class FailingParticipationRepository(
        private val repository: ParticipationRepository,
    ) : ParticipationRepository by repository {
        private var enabled = false

        var successfulSaves = 0
            private set

        var firstSavedRecord: Participation? = null
            private set

        fun failOnSecondSave() {
            enabled = true
            successfulSaves = 0
            firstSavedRecord = null
        }

        fun stopFailing() {
            enabled = false
        }

        override fun <S : Participation> save(entity: S): S {
            if (!enabled) {
                return repository.save(entity)
            }

            if (successfulSaves == 1) {
                throw ForcedSaveFailure()
            }

            val saved = repository.save(entity)
            successfulSaves++

            firstSavedRecord = checkNotNull(
                repository.findByIdOrNull(checkNotNull(saved.id)),
            )

            return saved
        }
    }

    class ForcedSaveFailure : RuntimeException("두 번째 저장 실패")
}