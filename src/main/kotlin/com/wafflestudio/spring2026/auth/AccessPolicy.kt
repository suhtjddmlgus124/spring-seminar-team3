package com.wafflestudio.spring2026.auth

import com.wafflestudio.spring2026.waggle.model.User
import com.wafflestudio.spring2026.waggle.model.UserRole
import com.wafflestudio.spring2026.waggle.model.UserStatus
import com.wafflestudio.spring2026.waggle.repository.EnrollmentRepository
import org.springframework.stereotype.Component

class ForbiddenException : RuntimeException("이 기능을 사용할 권한이 없습니다.")

/**
 * 3주차 권한 표를 한곳에 모은다. 컨트롤러는 요청자와 대상만 넘기고, 규칙은 여기서만 판단한다.
 *
 * 공통 규칙
 *  - 와장은 모든 엔드포인트를 쓸 수 있다.
 *  - 운영진과 루키는 가입이 APPROVED 일 때만 역할에 맞는 권한을 가진다.
 *    PENDING·REJECTED 는 공개 엔드포인트, 로그아웃, 내 정보 조회 외에는 모두 403 이다.
 *
 * 대상이 있어야 판단할 수 있는 규칙(담당 세미나, 심사 대상의 역할)은 대상을 먼저 찾은 뒤 부른다.
 * 그래서 없는 대상은 404 가 먼저 나간다. 403 과 404 가 겹치는 요청은 채점하지 않는다.
 */
@Component
class AccessPolicy(
    private val enrollmentRepository: EnrollmentRepository,
) {
    /** 사용자·세미나 단건 조회: 승인된 사용자면 역할과 관계없이 가능 */
    fun requireApproved(principal: AuthPrincipal) {
        val user = principal.user
        if (user.isAdmin()) return
        if (!user.isApproved()) throw ForbiddenException()
    }

    /** 세미나 개설: 와장만 */
    fun requireAdmin(principal: AuthPrincipal) {
        if (!principal.user.isAdmin()) throw ForbiddenException()
    }

    /** 세미나 수정, 회차 등록·수정: 와장 또는 그 세미나를 담당하는 승인된 운영진 */
    fun requireSeminarManager(principal: AuthPrincipal, seminarId: Long) {
        val user = principal.user
        if (user.isAdmin()) return
        if (!user.isApprovedStaffOf(seminarId)) throw ForbiddenException()
    }

    /** 회차 목록·단건 조회: 와장, 담당 운영진, 그 세미나를 수강 중인 루키 */
    fun requireSessionViewer(principal: AuthPrincipal, seminarId: Long) {
        val user = principal.user
        if (user.isAdmin()) return
        if (user.isApprovedStaffOf(seminarId)) return
        if (user.isApprovedRookie() && enrollmentRepository.existsBySeminarIdAndRookieId(seminarId, user.id!!)) return
        throw ForbiddenException()
    }

    /** 수강 신청·취소, 내 수강 목록: 승인된 루키 */
    fun requireRookie(principal: AuthPrincipal) {
        val user = principal.user
        if (user.isAdmin()) return
        if (!user.isApprovedRookie()) throw ForbiddenException()
    }

    /** 가입 신청 목록: 와장과 승인된 운영진. 운영진에게 루키만 보이는 것은 조회 쪽에서 거른다. */
    fun requireUserListViewer(principal: AuthPrincipal) {
        val user = principal.user
        if (user.isAdmin()) return
        if (!(user.role == UserRole.STAFF && user.isApproved())) throw ForbiddenException()
    }

    /** 가입 심사: 운영진의 신청은 와장만, 루키의 신청은 와장과 승인된 운영진 */
    fun requireApprover(principal: AuthPrincipal, target: User) {
        val user = principal.user
        if (user.isAdmin()) return
        val isApprovedStaff = user.role == UserRole.STAFF && user.isApproved()
        if (!(isApprovedStaff && target.role == UserRole.ROOKIE)) throw ForbiddenException()
    }

    private fun User.isAdmin(): Boolean = role == UserRole.ADMIN

    private fun User.isApproved(): Boolean = status == UserStatus.APPROVED

    private fun User.isApprovedRookie(): Boolean = role == UserRole.ROOKIE && isApproved()

    private fun User.isApprovedStaffOf(seminarId: Long): Boolean =
        role == UserRole.STAFF && isApproved() && this.seminarId == seminarId
}
