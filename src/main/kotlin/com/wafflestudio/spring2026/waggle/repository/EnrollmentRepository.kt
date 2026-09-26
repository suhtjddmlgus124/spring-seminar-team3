package com.wafflestudio.spring2026.waggle.repository

import org.springframework.data.repository.ListCrudRepository
import com.wafflestudio.spring2026.waggle.model.Enrollment

interface EnrollmentRepository: ListCrudRepository<Enrollment, Long> {
    fun countBySeminarId(seminarId: Long): Long
}