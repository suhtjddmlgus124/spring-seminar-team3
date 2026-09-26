package com.wafflestudio.spring2026.waggle.service

import com.wafflestudio.spring2026.waggle.model.Seminar
import com.wafflestudio.spring2026.waggle.repository.SeminarRepository
import com.wafflestudio.spring2026.waggle.repository.EnrollmentRepository
import com.wafflestudio.spring2026.waggle.repository.SessionRepository
import com.wafflestudio.spring2026.waggle.dto.seminar.SeminarStatus
import java.time.LocalDateTime
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

class SeminarNotFoundException(seminarId: Long): RuntimeException(
    "ID가 ${seminarId}인 세미나를 찾을 수 없습니다."
)

@Service
class SeminarService(
    private val seminarRepository: SeminarRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val sessionRepository: SessionRepository
) {
    fun createSeminar(
        title: String,
        description: String?,
        capacity: Int,
        applyStartAt: LocalDateTime,
        applyEndAt: LocalDateTime,
        totalGraceDays: Int,
    ): Seminar {
        val seminar = Seminar(
            title = title,
            description = description,
            capacity = capacity,
            applyStartAt = applyStartAt,
            applyEndAt = applyEndAt,
            totalGraceDays = totalGraceDays,
        )
        return seminarRepository.save(seminar)
    }
    
    fun getSeminar(id: Long): Seminar
        = seminarRepository.findByIdOrNull(id) ?: throw SeminarNotFoundException(id)
        
    fun getEnrolledCount(seminar: Seminar): Long
        = enrollmentRepository.countBySeminarId(seminar.id!!)
    
    fun getSessionCount(seminar: Seminar): Long
        = sessionRepository.countBySeminarId(seminar.id!!)
        
    fun getStatus(seminar: Seminar, enrolledCount: Long, now: LocalDateTime): SeminarStatus {
        return if(enrolledCount >= seminar.capacity.toLong() || !now.isBefore(seminar.applyEndAt)) 
            SeminarStatus.CLOSED 
        else if(now.isBefore(seminar.applyStartAt))
            SeminarStatus.BEFORE
        else 
            SeminarStatus.OPEN
    }
    
    fun updateSeminar(
        seminar: Seminar, 
        title: String = seminar.title, 
        description: String? = seminar.description
    ): Seminar {
        val updatedSeminar = seminar.copy(title = title, description = description)
        return seminarRepository.save(updatedSeminar)
    }
}