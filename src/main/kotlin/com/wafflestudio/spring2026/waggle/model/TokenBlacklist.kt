package com.wafflestudio.spring2026.waggle.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("token_blacklist")
data class TokenBlacklist(
    @Id
    val jti: String,
    
    @Column("exp")
    val exp: Instant,
)