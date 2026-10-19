package com.wafflestudio.spring2026.waggle.repository

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.ListCrudRepository
import org.springframework.data.repository.query.Param
import com.wafflestudio.spring2026.waggle.model.User

interface UserRepository: ListCrudRepository<User, Long> {
    fun existsByEmail(email: String): Boolean
    fun findByEmail(email: String): User?

    /** 가입 신청 목록. 와장 계정은 나오지 않는다. 최근 가입 순, 가입 일시가 같으면 ID 내림차순. */
    @Query(
        """
        SELECT * FROM users
        WHERE role <> 'ADMIN'
        AND (:status IS NULL OR status = :status)
        AND (:role IS NULL OR role = :role)
        ORDER BY createdAt DESC, id DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun searchApplicants(
        @Param("status") status: String?,
        @Param("role") role: String?,
        @Param("limit") limit: Int,
        @Param("offset") offset: Long,
    ): List<User>

    @Query(
        """
        SELECT COUNT(*) FROM users
        WHERE role <> 'ADMIN'
        AND (:status IS NULL OR status = :status)
        AND (:role IS NULL OR role = :role)
        """
    )
    fun countApplicants(
        @Param("status") status: String?,
        @Param("role") role: String?,
    ): Long
}
