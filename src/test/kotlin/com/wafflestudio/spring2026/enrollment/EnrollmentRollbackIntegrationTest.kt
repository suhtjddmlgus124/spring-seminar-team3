package com.wafflestudio.spring2026.enrollment

import com.wafflestudio.spring2026.auth.repository.UserRepository
import com.wafflestudio.spring2026.support.ApiIntegrationTest
import com.wafflestudio.spring2026.support.runConcurrently
import com.wafflestudio.spring2026.waggle.model.Enrollment
import com.wafflestudio.spring2026.waggle.repository.EnrollmentRepository
import com.wafflestudio.spring2026.waggle.service.EnrollmentService
import com.wafflestudio.spring2026.waggle.service.SeminarService
import org.aopalliance.intercept.MethodInterceptor
import org.springframework.aop.framework.Advised
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EnrollmentRollbackIntegrationTest : ApiIntegrationTest() {
    @Autowired
    private lateinit var enrollmentService: EnrollmentService

    @Autowired
    private lateinit var seminarService: SeminarService

    @Autowired
    private lateinit var enrollmentRepository: EnrollmentRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `저장 직후 예외가 발생하면 신청이 롤백되고 기존 신청과 빈자리가 유지된다`() {
        val seminarId = createSeminar(capacity = 2)
        val existing = approvedRookie()
        val retrying = approvedRookie()
        enroll(seminarId, existing.token).andExpect { status { isCreated() } }
        val existingBefore = assertNotNull(
            enrollmentRepository.findBySeminarIdAndRookieId(seminarId, existing.id),
        )
        val seminar = seminarService.getSeminar(seminarId)
        val rookie = assertNotNull(userRepository.findByIdOrNull(retrying.id))
        val failure = ForcedSaveFailure()
        var insertedId: Long? = null

        // save 자체는 실제 DB에 수행한다. repository 트랜잭션 처리가 반환된 뒤 예외를 던져
        // repository 단독 롤백이 아니라 서비스 전체의 트랜잭션 경계를 검증한다.
        val failAfterSave = MethodInterceptor { invocation ->
            val result = invocation.proceed()
            if (invocation.method.name == "save" && result is Enrollment &&
                result.seminarId == seminarId && result.rookieId == retrying.id
            ) {
                insertedId = assertNotNull(result.id)
                assertEquals(2L, storedCount(seminarId), "예외 발생 전에 실제 INSERT가 수행되어야 합니다.")
                assertEquals(
                    1L,
                    jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM enrollments WHERE id = ?",
                        Long::class.javaObjectType,
                        insertedId,
                    ),
                )
                throw failure
            }
            result
        }
        val repositoryProxy = enrollmentRepository as Advised
        repositoryProxy.addAdvice(0, failAfterSave)
        try {
            // 테스트 메서드에 @Transactional을 붙이지 않아 서비스가 직접 트랜잭션을 시작하게 한다.
            assertSame(failure, assertFailsWith<ForcedSaveFailure> {
                enrollmentService.createEnrollment(seminar, rookie, Instant.now())
            })
        } finally {
            repositoryProxy.removeAdvice(failAfterSave)
        }

        assertNotNull(insertedId)
        assertEquals(1L, storedCount(seminarId))
        assertNull(enrollmentRepository.findByIdOrNull(insertedId!!))
        assertNull(enrollmentRepository.findBySeminarIdAndRookieId(seminarId, retrying.id))
        assertEquals(existingBefore, enrollmentRepository.findByIdOrNull(existingBefore.id!!))

        // 별도 요청 스레드의 재신청이 완료되어야 롤백 후 락도 해제되었다고 볼 수 있다.
        val retry = runConcurrently(listOf(retrying), timeout = Duration.ofSeconds(10)) { actor ->
            enroll(seminarId, actor.token).andReturn().response
        }.single()
        assertEquals(201, retry.status, retry.contentAsString)
        val retryId = objectMapper.readTree(retry.contentAsString).path("id").asLong()
        assertTrue(retryId > 0)
        assertEquals(retryId, enrollmentRepository.findBySeminarIdAndRookieId(seminarId, retrying.id)?.id)
        assertEquals(2L, storedCount(seminarId))
        assertEquals(existingBefore, enrollmentRepository.findByIdOrNull(existingBefore.id!!))
    }

    private fun storedCount(seminarId: Long): Long =
        assertNotNull(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM enrollments WHERE seminarId = ?",
            Long::class.javaObjectType,
            seminarId,
        ))

    private class ForcedSaveFailure : RuntimeException("롤백 검증용 저장 후 예외")
}
