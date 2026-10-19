package com.wafflestudio.spring2026.waggle.dto.session

import java.time.OffsetDateTime
import org.openapitools.jackson.nullable.JsonNullable
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/**
 * 보낸 항목만 고친다. SeminarUpdateRequest 처럼 JsonNullable 로 "키 없음" 과 "null" 을 구분한다.
 * title, startsAt, location, assignmentTitle 은 보낸다면 비울 수 없다.
 * lectureContent, assignmentContent 는 null 을 보내면 설명을 지운다.
 */
data class SessionUpdateRequest(
    val title: JsonNullable<
        @NotBlank(message = "비어있을 수 없습니다.")
        @Size(max = 255, message = "255자를 넘을 수 없습니다.")
        String
    > = JsonNullable.undefined(),

    val startsAt: JsonNullable<
        @NotNull(message = "비어있을 수 없습니다.")
        OffsetDateTime
    > = JsonNullable.undefined(),

    val location: JsonNullable<
        @NotBlank(message = "비어있을 수 없습니다.")
        @Size(max = 255, message = "255자를 넘을 수 없습니다.")
        String
    > = JsonNullable.undefined(),

    val assignmentTitle: JsonNullable<
        @NotBlank(message = "비어있을 수 없습니다.")
        @Size(max = 255, message = "255자를 넘을 수 없습니다.")
        String
    > = JsonNullable.undefined(),

    val lectureContent: JsonNullable<String> = JsonNullable.undefined(),

    val assignmentContent: JsonNullable<String> = JsonNullable.undefined(),
)
