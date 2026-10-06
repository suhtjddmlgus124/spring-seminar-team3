package com.wafflestudio.spring2026.waggle.dto.session

import java.time.OffsetDateTime
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SessionCreateRequest(
    @field:NotBlank(message = "비어있을 수 없습니다.")
    @field:Size(max = 255, message = "255자를 넘을 수 없습니다.")
    val title: String,

    val startsAt: OffsetDateTime,

    @field:NotBlank(message = "비어있을 수 없습니다.")
    @field:Size(max = 255, message = "255자를 넘을 수 없습니다.")
    val location: String,

    @field:NotBlank(message = "비어있을 수 없습니다.")
    @field:Size(max = 255, message = "255자를 넘을 수 없습니다.")
    val assignmentTitle: String,

    val lectureContent: String? = null,

    val assignmentContent: String? = null,
)