package com.wafflestudio.spring2026.waggle.service

import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import com.wafflestudio.spring2026.waggle.dto.session.FieldPatch
import com.wafflestudio.spring2026.waggle.dto.session.InvalidRecordUpdateRequestException
import com.wafflestudio.spring2026.waggle.dto.session.RecordUpdateCommand
import com.wafflestudio.spring2026.waggle.model.ParticipationAssignmentStatus
import com.wafflestudio.spring2026.waggle.model.ParticipationAttendanceStatus

@Component
class BatchUpdateRecordsParser {

    fun parse(body: JsonNode): List<RecordUpdateCommand> {
        if (!body.isObject) {
            throw InvalidRecordUpdateRequestException("요청 본문은 JSON 객체여야 합니다.")
        }

        val recordsNode = body.get("records")
            ?: throw InvalidRecordUpdateRequestException("records 필드는 필수입니다.")

        if (!recordsNode.isArray || recordsNode.isEmpty) {
            throw InvalidRecordUpdateRequestException("records는 비어 있지 않은 배열이어야 합니다.")
        }

        val seenEnrollmentIds = mutableSetOf<Long>()
        return (0 until recordsNode.size()).map { index ->
            val itemNode = recordsNode.get(index)
            if (!itemNode.isObject) {
                throw InvalidRecordUpdateRequestException("records의 각 항목은 JSON 객체여야 합니다.")
            }

            val enrollmentIdNode = itemNode.get("enrollmentId")
                ?: throw InvalidRecordUpdateRequestException("enrollmentId는 필수입니다.")

            if (!enrollmentIdNode.isIntegralNumber || enrollmentIdNode.longValue() <= 0) {
                throw InvalidRecordUpdateRequestException("enrollmentId는 양의 정수여야 합니다.")
            }

            val enrollmentId = enrollmentIdNode.longValue()
            if (!seenEnrollmentIds.add(enrollmentId)) {
                throw InvalidRecordUpdateRequestException("중복된 enrollmentId가 포함되어 있습니다: $enrollmentId")
            }

            val attendancePatch = parseEnumPatch(
                parent = itemNode,
                fieldName = "attendanceStatus",
                parser = { raw ->
                    runCatching { ParticipationAttendanceStatus.valueOf(raw) }.getOrElse {
                        throw InvalidRecordUpdateRequestException("유효하지 않은 attendanceStatus 값입니다: $raw")
                    }
                },
            )

            val assignmentPatch = parseEnumPatch(
                parent = itemNode,
                fieldName = "assignmentStatus",
                parser = { raw ->
                    runCatching { ParticipationAssignmentStatus.valueOf(raw) }.getOrElse {
                        throw InvalidRecordUpdateRequestException("유효하지 않은 assignmentStatus 값입니다: $raw")
                    }
                },
            )

            RecordUpdateCommand(
                enrollmentId = enrollmentId,
                attendanceStatus = attendancePatch,
                assignmentStatus = assignmentPatch,
            )
        }
    }

    private fun <T> parseEnumPatch(
        parent: JsonNode,
        fieldName: String,
        parser: (String) -> T,
    ): FieldPatch<T> {
        if (!parent.has(fieldName)) {
            return FieldPatch.Omitted
        }
        val fieldNode = parent.get(fieldName)
        if (fieldNode.isNull) {
            return FieldPatch.Clear
        }
        if (!fieldNode.isString) {
            throw InvalidRecordUpdateRequestException("$fieldName 값은 문자열 또는 null이어야 합니다.")
        }
        return FieldPatch.Set(parser(fieldNode.asString()))
    }
}