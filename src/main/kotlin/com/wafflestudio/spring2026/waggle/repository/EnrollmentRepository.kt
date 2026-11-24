package com.wafflestudio.spring2026.waggle.repository

import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.ListCrudRepository
import org.springframework.data.repository.query.Param
import com.wafflestudio.spring2026.waggle.model.Enrollment

interface EnrollmentRepository: ListCrudRepository<Enrollment, Long> {
    fun countBySeminarId(seminarId: Long): Long

    fun existsBySeminarIdAndRookieId(seminarId: Long, rookieId: Long): Boolean

    fun findBySeminarIdAndRookieId(seminarId: Long, rookieId: Long): Enrollment?

    fun countByRookieId(rookieId: Long): Long

    @Modifying
    @Query("DELETE FROM enrollments WHERE id = :id")
    fun deleteByIdReturningCount(@Param("id") id: Long): Int

    /** 내 수강 목록. 최근 신청 순, 신청 일시가 같으면 ID 내림차순. */
    @Query(
        """
        SELECT * FROM enrollments
        WHERE rookieId = :rookieId
        ORDER BY createdAt DESC, id DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun findPageByRookieId(
        @Param("rookieId") rookieId: Long,
        @Param("limit") limit: Int,
        @Param("offset") offset: Long,
    ): List<Enrollment>

    /** 대시보드 수강생 행. 수강 신청 ID 오름차순. */
    @Query(
        """
        SELECT * FROM enrollments
        WHERE seminarId = :seminarId
        ORDER BY id ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun findPageBySeminarId(
        @Param("seminarId") seminarId: Long,
        @Param("limit") limit: Int,
        @Param("offset") offset: Long,
    ): List<Enrollment>
}
