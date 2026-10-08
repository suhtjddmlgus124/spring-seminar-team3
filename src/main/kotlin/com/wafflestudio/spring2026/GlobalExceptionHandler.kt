package com.wafflestudio.spring2026

import com.wafflestudio.spring2026.meeting.MeetingNotFoundException
import com.wafflestudio.spring2026.waggle.service.SeminarNotFoundException
import com.wafflestudio.spring2026.waggle.service.UserNotFoundException
import com.wafflestudio.spring2026.waggle.service.UserEmailAlreadyExistsException
import com.wafflestudio.spring2026.waggle.service.UserIsNotPendingException
import com.wafflestudio.spring2026.waggle.service.EnrollmentNotFoundException
import com.wafflestudio.spring2026.waggle.service.MyEnrollmentNotFoundException
import com.wafflestudio.spring2026.waggle.service.UserIsNotRookieException
import com.wafflestudio.spring2026.waggle.service.RookieIsNotApprovedException
import com.wafflestudio.spring2026.waggle.service.SeminarIsNotOpenException
import com.wafflestudio.spring2026.waggle.service.AlreadyEnrolledException
import com.wafflestudio.spring2026.waggle.service.EnrollmentPeriodClosedException
import com.wafflestudio.spring2026.waggle.service.SessionNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import com.wafflestudio.spring2026.auth.InvalidCredentialsException
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

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(
        e: InvalidCredentialsException,
    ): ResponseEntity<ApiErrorResponse> = ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body(ApiErrorResponse(code = "INVALID_CREDENTIALS", message = e.message!!))

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
        
    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFound(
        exception: UserNotFoundException
    ): ResponseEntity<ApiErrorResponse> = 
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiErrorResponse(
                code = "USER_NOT_FOUND",
                message = exception.message ?: "사용자를 찾을 수 없습니다."
            )
        )
    
    @ExceptionHandler(UserEmailAlreadyExistsException::class)
    fun handleUserEmailAlreadyExists(
        exception: UserEmailAlreadyExistsException
    ): ResponseEntity<ApiErrorResponse> = 
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiErrorResponse(
                code = "USER_EMAIL_ALREADY_EXISTS",
                message = exception.message ?: "이미 존재하는 이메일입니다."
            )
        )
    
    @ExceptionHandler(UserIsNotPendingException::class)
    fun handleUserIsNotPending(
        exception: UserIsNotPendingException
    ): ResponseEntity<ApiErrorResponse> = 
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiErrorResponse(
                code = "USER_IS_NOT_PENDING",
                message = exception.message ?: "사용자가 PENDING 상태가 아닙니다."
            )
        )

    @ExceptionHandler(MyEnrollmentNotFoundException::class)
    fun handleMyEnrollmentNotFound(
        e: MyEnrollmentNotFoundException,
    ): ResponseEntity<ApiErrorResponse> = ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .body(ApiErrorResponse(code = "ENROLLMENT_NOT_FOUND", message = e.message!!))

    @ExceptionHandler(EnrollmentNotFoundException::class)
    fun handleEnrollmentNotFound(
        exception: EnrollmentNotFoundException
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiErrorResponse(
                code = "ENROLLMENT_NOT_FOUND",
                message = exception.message ?: "수강 신청을 찾을 수 없습니다."
            )
        )

    @ExceptionHandler(UserIsNotRookieException::class)
    fun handleUserIsNotRookie(
        exception: UserIsNotRookieException
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiErrorResponse(
                code = "USER_IS_NOT_ROOKIE",
                message = exception.message ?: "루키만 수강 신청할 수 있습니다."
            )
        )

    @ExceptionHandler(RookieIsNotApprovedException::class)
    fun handleRookieIsNotApproved(
        exception: RookieIsNotApprovedException
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ApiErrorResponse(
                code = "ROOKIE_IS_NOT_APPROVED",
                message = exception.message ?: "가입이 승인된 루키만 수강 신청할 수 있습니다."
            )
        )

    @ExceptionHandler(SeminarIsNotOpenException::class)
    fun handleSeminarIsNotOpen(
        exception: SeminarIsNotOpenException
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiErrorResponse(
                code = "SEMINAR_IS_NOT_OPEN",
                message = exception.message ?: "신청할 수 있는 상태의 세미나가 아닙니다."
            )
        )

    @ExceptionHandler(AlreadyEnrolledException::class)
    fun handleAlreadyEnrolled(
        exception: AlreadyEnrolledException
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiErrorResponse(
                code = "ALREADY_ENROLLED",
                message = exception.message ?: "이미 신청한 세미나입니다."
            )
        )

    @ExceptionHandler(EnrollmentPeriodClosedException::class)
    fun handleEnrollmentPeriodClosed(
        exception: EnrollmentPeriodClosedException
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiErrorResponse(
                code = "ENROLLMENT_PERIOD_CLOSED",
                message = exception.message ?: "신청 기간에만 취소할 수 있습니다."
            )
        )
        
    @ExceptionHandler(SessionNotFoundException::class)
    fun handleSessionNotFound(
        exception: SessionNotFoundException
    ): ResponseEntity<ApiErrorResponse> = 
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiErrorResponse(
                code = "SESSION_NOT_FOUND",
                message = exception.message ?: "회차가 존재하지 않습니다."
            )
        )
}
