package com.wafflestudio.spring2026.waggle.repository

import org.springframework.data.repository.ListCrudRepository
import com.wafflestudio.spring2026.waggle.model.Session

interface SessionRepository: ListCrudRepository<Session, Long> {
    fun countBySeminarId(seminarId: Long): Long
}