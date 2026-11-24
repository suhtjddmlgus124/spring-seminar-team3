package com.wafflestudio.spring2026.waggle.dto.session

import com.wafflestudio.spring2026.waggle.model.ParticipationAssignmentStatus
import com.wafflestudio.spring2026.waggle.model.ParticipationAttendanceStatus

class InvalidRecordUpdateRequestException(message: String) : RuntimeException(message)

sealed interface FieldPatch<out T> {
    data object Omitted : FieldPatch<Nothing>
    data object Clear : FieldPatch<Nothing>
    data class Set<T>(val value: T) : FieldPatch<T>
}

data class RecordUpdateCommand(
    val enrollmentId: Long,
    val attendanceStatus: FieldPatch<ParticipationAttendanceStatus>,
    val assignmentStatus: FieldPatch<ParticipationAssignmentStatus>,
)

data class SessionRecordResponse(
    val enrollmentId: Long,
    val attendanceStatus: ParticipationAttendanceStatus?,
    val assignmentStatus: ParticipationAssignmentStatus?,
)

data class BatchUpdateRecordsResponse(
    val records: List<SessionRecordResponse>,
)