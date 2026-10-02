package com.wafflestudio.spring2026.waggle.dto.user

import com.wafflestudio.spring2026.waggle.model.UserStatus
import jakarta.validation.constraints.AssertTrue

data class UserApprovalRequest(
    val status: UserStatus
) {
    @get:AssertTrue(message = "status를 PENDING으로 바꿀 수 없습니다.")
    val isStatus: Boolean
        get() = status != UserStatus.PENDING
}
