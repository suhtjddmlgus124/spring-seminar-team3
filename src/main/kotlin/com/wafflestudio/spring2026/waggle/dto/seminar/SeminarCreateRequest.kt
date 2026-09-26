package com.wafflestudio.spring2026.waggle.dto.seminar

import java.time.LocalDateTime
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import jakarta.validation.constraints.AssertTrue

data class SeminarCreateRequest(
    @field:NotBlank("비어있을 수 없습니다.")
    @field:Size(max = 255, message = "255자를 넘을 수 없습니다.")
    val title: String,
    
    val description: String?,
    
    @field:Positive("1 이상의 값이어야 합니다.")
    val capacity: Int,
    
    val applyStartAt: LocalDateTime,
    
    val applyEndAt: LocalDateTime,
    
    @field:PositiveOrZero("0 이상의 값이어야 합니다.")
    val totalGraceDays: Int,
) {
    @get:AssertTrue(message = "신청 종료 일시는 신청 시작 일시보다 이후여야 합니다.")
    val isApplyEndAt: Boolean
        get() = applyEndAt.isAfter(applyStartAt)
}
