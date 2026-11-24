package com.wafflestudio.spring2026.waggle.service

import com.wafflestudio.spring2026.waggle.dto.session.BatchUpdateRecordsResponse
import com.wafflestudio.spring2026.waggle.dto.session.FieldPatch
import com.wafflestudio.spring2026.waggle.dto.session.RecordUpdateCommand
import com.wafflestudio.spring2026.waggle.dto.session.SessionRecordResponse
import com.wafflestudio.spring2026.waggle.model.Participation
import com.wafflestudio.spring2026.waggle.model.Session
import com.wafflestudio.spring2026.waggle.repository.EnrollmentRepository
import com.wafflestudio.spring2026.waggle.repository.ParticipationRepository
import com.wafflestudio.spring2026.waggle.repository.SeminarRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
class ParticipationService(
    private val participationRepository: ParticipationRepository,
    private val enrollmentRepository: EnrollmentRepository,
    private val seminarRepository: SeminarRepository,
) {
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun batchUpdateRecords(
        session: Session,
        commands: List<RecordUpdateCommand>,
    ): BatchUpdateRecordsResponse {
        seminarRepository.findByIdForUpdate(session.seminarId)
            ?: throw SeminarNotFoundException(session.seminarId)

        val sessionId = session.id!!

        val enrollmentsById = commands.associate { command ->
            val enrollment = enrollmentRepository.findByIdOrNull(
                command.enrollmentId,
            )

            if (enrollment == null || enrollment.seminarId != session.seminarId) {
                throw EnrollmentNotFoundException(command.enrollmentId)
            }

            command.enrollmentId to enrollment
        }

        val results = commands.map { command ->
            val enrollmentId = enrollmentsById.getValue(
                command.enrollmentId,
            ).id!!

            val existing = participationRepository.findByEnrollmentIdAndSessionId(
                enrollmentId,
                sessionId,
            )

            val attendance = when (val patch = command.attendanceStatus) {
                FieldPatch.Omitted -> existing?.attendanceStatus
                FieldPatch.Clear -> null
                is FieldPatch.Set -> patch.value
            }

            val assignment = when (val patch = command.assignmentStatus) {
                FieldPatch.Omitted -> existing?.assignmentStatus
                FieldPatch.Clear -> null
                is FieldPatch.Set -> patch.value
            }

            if (attendance == null && assignment == null) {
                if (existing != null) {
                    participationRepository.delete(existing)
                }
            } else {
                participationRepository.save(
                    Participation(
                        id = existing?.id,
                        attendanceStatus = attendance,
                        assignmentStatus = assignment,
                        enrollmentId = enrollmentId,
                        sessionId = sessionId,
                    ),
                )
            }

            SessionRecordResponse(
                enrollmentId = enrollmentId,
                attendanceStatus = attendance,
                assignmentStatus = assignment,
            )
        }

        return BatchUpdateRecordsResponse(records = results)
    }
}