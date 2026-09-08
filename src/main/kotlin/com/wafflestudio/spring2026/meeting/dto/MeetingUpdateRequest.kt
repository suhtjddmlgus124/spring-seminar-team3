package com.wafflestudio.spring2026.meeting.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class MeetingUpdateRequest(
    @field:NotBlank(message = "모임 제목은 비어 있을 수 없습니다.")
    val title: String? = null,

    @field:Positive(message = "모임 정원은 1명 이상이어야 합니다.")
    val capacity: Int? = null,
)
