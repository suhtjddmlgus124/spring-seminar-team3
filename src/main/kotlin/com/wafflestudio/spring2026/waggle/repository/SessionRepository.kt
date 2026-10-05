package com.wafflestudio.spring2026.waggle.repository

import org.springframework.data.repository.ListCrudRepository
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.query.Param
import com.wafflestudio.spring2026.waggle.model.Session

interface SessionRepository: ListCrudRepository<Session, Long>{
    fun countBySeminarId(seminarId: Long): Long

    fun findBySeminarIdOrderByStartsAtAscIdAsc(seminarId: Long): List<Session>

    @Query(
        """
        SELECT COUNT(*) + 1 AS session_round
        FROM sessions s
        JOIN sessions target ON target.id = :id
        WHERE s.seminar_id = target.seminar_id
        AND (s.starts_at < target.starts_at OR (s.starts_at = target.starts_at AND s.id < target.id))
        """
    )
    fun findRoundBySessionId(@Param("id") sessionId: Long): Long

    // @Query(
    //     """
    //     SELECT *, ROW_NUMBER() OVER (ORDER BY starts_at ASC, id ASC) AS sessions_round
    //     FROM sessions
    //     WHERE seminar_id = :seminarId
    //     ORDER BY sessions_round ASC
    //     """
    // )
    // fun findAllBySeminarIdOrderByStartsAtAscIdAscWithRound(@Param("seminarId") seminarId: Long): List<SessionRoundInfo>
}