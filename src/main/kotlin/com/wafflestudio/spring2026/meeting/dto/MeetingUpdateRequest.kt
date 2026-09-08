package com.wafflestudio.spring2026.meeting.dto

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive

data class MeetingUpdateRequest(
    @field:Pattern(regexp = "^\\s*\\S.*$")
    val title: String? = null,

    @field:Positive(message = "모임 정원은 1명 이상이어야 합니다.")
    val capacity: Int? = null,
)