package com.wafflestudio.spring2026.waggle.service

import com.wafflestudio.spring2026.waggle.model.Seminar
import com.wafflestudio.spring2026.waggle.repository.SeminarRepository
import com.wafflestudio.spring2026.waggle.repository.EnrollmentRepository
import com.wafflestudio.spring2026.waggle.repository.SessionRepository
import com.wafflestudio.spring2026.waggle.dto.seminar.SeminarStatus
import com.wafflestudio.spring2026.waggle.dto.PageQuery
import com.wafflestudio.spring2026.waggle.dto.PageResponse
import java.time.Instant
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
        applyStartAt: Instant,
        applyEndAt: Instant,
        totalGraceDays: Int,
        now: Instant,
    ): Seminar {
        val seminar = Seminar(
            title = title,
            description = description,
            capacity = capacity,
            applyStartAt = applyStartAt,
            applyEndAt = applyEndAt,
            totalGraceDays = totalGraceDays,
            createdAt = now,
        )
        return seminarRepository.save(seminar)
    }
    
    fun getSeminar(id: Long): Seminar
        = seminarRepository.findByIdOrNull(id) ?: throw SeminarNotFoundException(id)
        
    fun getEnrolledCount(seminar: Seminar): Long
        = enrollmentRepository.countBySeminarId(seminar.id!!)
    
    fun getSessionCount(seminar: Seminar): Long
        = sessionRepository.countBySeminarId(seminar.id!!)
        
    fun getStatus(seminar: Seminar, enrolledCount: Long, now: Instant): SeminarStatus {
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

    /**
     * 세미나 목록. 세미나 ID 오름차순.
     * keyword 가 없거나 공백뿐이면 전체다. LIKE 의 특수문자(%, _)는 글자 그대로 찾도록 이스케이프한다.
     */
    fun searchSeminars(keyword: String?, status: SeminarStatus?, query: PageQuery, now: Instant): PageResponse<Seminar> {
        val keywordPattern = keyword
            ?.takeIf { it.isNotBlank() }
            ?.let { "%" + it.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%" }
        val statusName = status?.name

        val seminars = seminarRepository.search(keywordPattern, statusName, now, query.size, query.offset)
        val total = seminarRepository.countSearch(keywordPattern, statusName, now)
        return PageResponse.of(seminars, query, total)
    }
}
