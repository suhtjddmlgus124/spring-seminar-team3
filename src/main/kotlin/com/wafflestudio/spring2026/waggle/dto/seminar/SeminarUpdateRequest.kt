package com.wafflestudio.spring2026.waggle.dto.seminar

import org.openapitools.jackson.nullable.JsonNullable
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SeminarUpdateRequest(
    
    val title: JsonNullable<
        @NotBlank(message = "비어있을 수 없습니다.")
        @Size(max = 255, message = "255자를 넘을 수 없습니다.")
        String
    > = JsonNullable.undefined(),

    val description: JsonNullable<String> = JsonNullable.undefined(),
)
