package com.wafflestudio.spring2026.waggle.repository

import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.ListCrudRepository
import org.springframework.data.repository.query.Param
import com.wafflestudio.spring2026.waggle.model.Participation

interface ParticipationRepository: ListCrudRepository<Participation, Long> {
    /** 대시보드: 이번 페이지 수강생들의 기록만 한 번에 읽는다. */
    fun findByEnrollmentIdIn(enrollmentIds: Collection<Long>): List<Participation>

    fun findBySessionIdAndEnrollmentId(sessionId: Long, enrollmentId: Long): Participation?

    fun findByEnrollmentIdAndSessionId(enrollmentId: Long, sessionId: Long): Participation?

    fun findAllBySessionIdAndEnrollmentIdIn(
        sessionId: Long,
        enrollmentIds: Collection<Long>,
    ): List<Participation>

    @Modifying
    @Query("DELETE FROM participations WHERE enrollmentId = :enrollmentId")
    fun deleteAllByEnrollmentId(@Param("enrollmentId") enrollmentId: Long): Int
}
