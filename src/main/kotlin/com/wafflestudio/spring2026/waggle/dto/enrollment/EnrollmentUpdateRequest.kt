package com.wafflestudio.spring2026.waggle.dto.enrollment

import io.swagger.v3.oas.annotations.media.Schema

data class EnrollmentUpdateRequest(
    @field:Schema(
        description = "잔여 Grace Day. 생략할 수 있지만 null은 허용하지 않습니다.",
        minimum = "0",
        nullable = false,
    )
    val graceDaysRemaining: Int? = null,

    @field:Schema(
        description = "탈락 여부. 생략할 수 있지만 null은 허용하지 않습니다.",
        nullable = false,
    )
    val dropped: Boolean? = null,
)

class InvalidEnrollmentUpdateRequestException(
    message: String,
) : RuntimeException(message)