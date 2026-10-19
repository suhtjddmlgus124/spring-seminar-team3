package com.wafflestudio.spring2026.waggle.repository

import java.time.Instant
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.ListCrudRepository
import org.springframework.data.repository.query.Param
import com.wafflestudio.spring2026.waggle.model.Seminar

/**
 * 세미나 목록의 status 는 저장된 컬럼이 아니라 SeminarService.getStatus 와 같은 규칙으로 계산한 값이다.
 * 페이지를 나누기 전에 걸러야 totalElements 가 맞으므로 SQL 안에서 같은 규칙을 다시 쓴다.
 *   정원이 찼거나 신청 기간이 끝났으면 CLOSED, 시작 전이면 BEFORE, 나머지는 OPEN
 */
interface SeminarRepository: ListCrudRepository<Seminar, Long> {
    @Query(
        """
        SELECT s.* FROM seminars s
        WHERE (:keywordPattern IS NULL OR s.title LIKE :keywordPattern)
        AND (:status IS NULL OR $STATUS_EXPRESSION = :status)
        ORDER BY s.id ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun search(
        @Param("keywordPattern") keywordPattern: String?,
        @Param("status") status: String?,
        @Param("now") now: Instant,
        @Param("limit") limit: Int,
        @Param("offset") offset: Long,
    ): List<Seminar>

    @Query(
        """
        SELECT COUNT(*) FROM seminars s
        WHERE (:keywordPattern IS NULL OR s.title LIKE :keywordPattern)
        AND (:status IS NULL OR $STATUS_EXPRESSION = :status)
        """
    )
    fun countSearch(
        @Param("keywordPattern") keywordPattern: String?,
        @Param("status") status: String?,
        @Param("now") now: Instant,
    ): Long

    companion object {
        const val STATUS_EXPRESSION = """
            (CASE
                WHEN (SELECT COUNT(*) FROM enrollments e WHERE e.seminarId = s.id) >= s.capacity
                    OR :now >= s.applyEndAt THEN 'CLOSED'
                WHEN :now < s.applyStartAt THEN 'BEFORE'
                ELSE 'OPEN'
            END)
        """
    }
}
