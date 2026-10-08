package com.wafflestudio.spring2026.auth

import com.wafflestudio.spring2026.waggle.model.TokenBlacklist
import com.wafflestudio.spring2026.waggle.repository.TokenBlacklistRepository
import org.springframework.data.jdbc.core.JdbcAggregateTemplate
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * 로그아웃된 토큰의 jti 를 DB 에 남긴다. 서버가 재시작돼도 무효가 유지돼야 하므로 메모리에 두지 않는다.
 *
 * save() 대신 JdbcAggregateTemplate.insert() 를 쓰는 이유:
 * TokenBlacklist 의 @Id 는 직접 넣는 String 이다. Spring Data JDBC 는 id 가 null 이 아니면
 * 이미 있는 행으로 보고 UPDATE 를 날리는데, 그러면 영향 행이 0 이라 예외가 난다.
 * insert() 는 INSERT 를 강제한다.
 */
@Service
class TokenBlacklistService(
    private val tokenBlacklistRepository: TokenBlacklistRepository,
    private val jdbcAggregateTemplate: JdbcAggregateTemplate,
) {
    fun isBlacklisted(jti: String): Boolean = tokenBlacklistRepository.existsByJti(jti)

    fun blacklist(jti: String, expiresAt: Instant) {
        if (tokenBlacklistRepository.existsByJti(jti)) return
        jdbcAggregateTemplate.insert(TokenBlacklist(jti = jti, exp = expiresAt))
    }

    /** 이미 만료된 토큰은 어차피 검증에서 걸리므로 블랙리스트에 남겨 둘 이유가 없다. */
    fun purgeExpired(now: Instant): Long = tokenBlacklistRepository.deleteByExpLessThanEqual(now)
}
