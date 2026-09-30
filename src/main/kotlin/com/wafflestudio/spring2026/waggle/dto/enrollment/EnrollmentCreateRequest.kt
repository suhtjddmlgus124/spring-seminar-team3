package com.wafflestudio.spring2026.waggle.dto.enrollment

import jakarta.validation.constraints.NotNull

data class EnrollmentCreateRequest(
    @field:NotNull(message = "루키 ID는 필수입니다.")
    val rookieId: Long,
)
