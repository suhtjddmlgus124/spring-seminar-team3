package com.wafflestudio.spring2026.waggle.repository

import org.springframework.data.repository.ListCrudRepository
import com.wafflestudio.spring2026.waggle.model.Participation

interface ParticipationRepository: ListCrudRepository<Participation, Long> {
    /** 대시보드: 이번 페이지 수강생들의 기록만 한 번에 읽는다. */
    fun findByEnrollmentIdIn(enrollmentIds: Collection<Long>): List<Participation>
}
