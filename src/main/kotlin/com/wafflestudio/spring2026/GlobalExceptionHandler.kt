package com.wafflestudio.spring2026

import com.wafflestudio.spring2026.meeting.MeetingNotFoundException
import com.wafflestudio.spring2026.waggle.service.SeminarNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(
        exception: MethodArgumentNotValidException,
    ): ResponseEntity<ApiErrorResponse> {
        val fieldErrors = exception.bindingResult.fieldErrors.map { error ->
            FieldErrorResponse(
                field = error.field,
                message = error.defaultMessage ?: "잘못된 값입니다.",
            )
        }

        return ResponseEntity.badRequest().body(
            ApiErrorResponse(
                code = "INVALID_REQUEST",
                message = "요청값이 올바르지 않습니다.",
                fieldErrors = fieldErrors,
            ),
        )
    }

    @ExceptionHandler(MeetingNotFoundException::class)
    fun handleMeetingNotFound(
        exception: MeetingNotFoundException,
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiErrorResponse(
                code = "MEETING_NOT_FOUND",
                message = exception.message ?: "모임을 찾을 수 없습니다.",
            ),
        )
        
    @ExceptionHandler(SeminarNotFoundException::class)
    fun handleSeminarNotFound(
        exception: SeminarNotFoundException
    ): ResponseEntity<ApiErrorResponse> = 
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiErrorResponse(
                code = "SEMINAR_NOT_FOUND",
                message = exception.message ?: "세미나를 찾을 수 없습니다."
            )
        )
}
