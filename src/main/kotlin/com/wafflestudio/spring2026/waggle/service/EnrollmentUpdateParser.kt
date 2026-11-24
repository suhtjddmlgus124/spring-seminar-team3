package com.wafflestudio.spring2026.waggle.service

import com.wafflestudio.spring2026.waggle.dto.enrollment.EnrollmentUpdateRequest
import com.wafflestudio.spring2026.waggle.dto.enrollment.InvalidEnrollmentUpdateRequestException
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

@Component
class EnrollmentUpdateParser {
    fun parse(body: JsonNode): EnrollmentUpdateRequest {
        if (!body.isObject) {
            throw InvalidEnrollmentUpdateRequestException(
                "요청 본문은 JSON 객체여야 합니다.",
            )
        }

        val graceDaysNode = body.get("graceDaysRemaining")
        val droppedNode = body.get("dropped")

        if (graceDaysNode == null && droppedNode == null) {
            throw InvalidEnrollmentUpdateRequestException(
                "수정할 항목을 하나 이상 보내야 합니다.",
            )
        }

        val graceDaysRemaining = graceDaysNode?.let {
            if (!it.isIntegralNumber || !it.canConvertToInt()) {
                throw InvalidEnrollmentUpdateRequestException(
                    "graceDaysRemaining은 정수여야 합니다.",
                )
            }
            it.asInt()
        }

        val dropped = droppedNode?.let {
            if (!it.isBoolean) {
                throw InvalidEnrollmentUpdateRequestException(
                    "dropped는 boolean이어야 합니다.",
                )
            }
            it.asBoolean()
        }

        return EnrollmentUpdateRequest(
            graceDaysRemaining = graceDaysRemaining,
            dropped = dropped,
        )
    }
}