package com.wafflestudio.spring2026.auth.repository

import java.time.Instant
import org.springframework.data.repository.ListCrudRepository
import com.wafflestudio.spring2026.auth.model.TokenBlacklist

interface TokenBlacklistRepository: ListCrudRepository<TokenBlacklist, String> {
    fun existsByJti(jti: String): Boolean
    fun deleteByExpLessThanEqual(now: Instant): Long
}