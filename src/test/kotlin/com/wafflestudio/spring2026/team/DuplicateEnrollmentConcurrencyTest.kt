package com.wafflestudio.spring2026.team

import com.wafflestudio.spring2026.support.ApiIntegrationTest
import com.wafflestudio.spring2026.support.runConcurrently
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

/**
 * 팀 자체 테스트: 중복 신청 (4주차 과제 4절 필수 시나리오)
 *
 * 한 루키가 자리가 있는 세미나에 20회 동시 신청한다.
 * 기대: 201 1건, 409 19건, 실제 DB 의 그 루키 신청 1개.
 * 예상한 409 만 중복 실패로 센다. 5xx·다른 상태 코드가 하나라도 섞이면 응답 건수 비교에서 실패한다.
 */
class DuplicateEnrollmentConcurrencyTest : ApiIntegrationTest() {
    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `한 루키의 20회 동시 신청은 1건만 성공하고 나머지는 409이며 신청은 하나만 남는다`() {
        val admin = adminToken()
        val seminarId = createSeminar(capacity = 3)
        val rookie = approvedRookie()

        val statuses = runConcurrently(List(20) { rookie }) { actor ->
            enroll(seminarId, actor.token).andReturn().response.status
        }

        assertEquals(
            mapOf(201 to 1, 409 to 19),
            statuses.groupingBy { it }.eachCount(),
            "응답 건수가 다릅니다. 5xx 가 있으면 DB 오류를 409 로 바꾸지 못했거나 다른 예외가 난 것입니다.",
        )
        assertEquals(1, enrollmentCount(seminarId, rookie.id), "해당 루키의 수강 신청은 DB 에 하나만 있어야 합니다.")
        assertEquals(1, enrollmentCount(seminarId), "실패한 신청이 행을 남기면 안 됩니다.")

        // 실패한 19건이 빈자리를 쓰지 않았는지: 정원 3 에 다른 루키 2명이 더 들어갈 수 있어야 한다.
        repeat(2) { enroll(seminarId, approvedRookie().token).andExpect { status { isCreated() } } }
        enroll(seminarId, approvedRookie().token).andExpect { status { isConflict() } }
        assertEquals(3, enrollmentCount(seminarId))
    }

    private fun enrollmentCount(seminarId: Long, rookieId: Long? = null): Int =
        if (rookieId == null) {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM enrollments WHERE seminarId = ?", Int::class.java, seminarId)!!
        } else {
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM enrollments WHERE seminarId = ? AND rookieId = ?",
                Int::class.java, seminarId, rookieId,
            )!!
        }
}
