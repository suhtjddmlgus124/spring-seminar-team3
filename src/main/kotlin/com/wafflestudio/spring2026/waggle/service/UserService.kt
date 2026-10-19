package com.wafflestudio.spring2026.waggle.service

import java.time.Instant
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import com.wafflestudio.spring2026.waggle.model.User
import com.wafflestudio.spring2026.waggle.model.UserRole
import com.wafflestudio.spring2026.waggle.model.UserStatus
import com.wafflestudio.spring2026.waggle.repository.UserRepository
import com.wafflestudio.spring2026.waggle.repository.SeminarRepository
import com.wafflestudio.spring2026.waggle.service.SeminarNotFoundException
import com.wafflestudio.spring2026.waggle.dto.PageQuery
import com.wafflestudio.spring2026.waggle.dto.PageResponse

class UserNotFoundException(userId: Long): RuntimeException(
    "ID가 ${userId}인 사용자를 찾을 수 없습니다."
)

class UserEmailAlreadyExistsException: RuntimeException(
    "이미 존재하는 이메일입니다."
)

class UserIsNotPendingException: RuntimeException(
    "사용자가 PENDING 상태가 아닙니다."
)

class InvalidApplicantRoleException: RuntimeException(
    "role 은 STAFF 또는 ROOKIE 여야 합니다."
)

@Service
class UserService(
    private val userRepository: UserRepository,
    private val seminarRepository: SeminarRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    fun createUser(
        email: String,
        password: String,
        name: String,
        githubUsername: String,
        role: UserRole,
        seminarId: Long?,
        now: Instant,
    ): User {
        // 이메일은 중복될 수 없음
        if(userRepository.existsByEmail(email)) 
            throw UserEmailAlreadyExistsException()
        
        // 세미나가 존재해야 함
        if(seminarId != null && !seminarRepository.existsById(seminarId)) 
            throw SeminarNotFoundException(seminarId)
        
        val user = User(
            email = email,
            // 원문 대신 BCrypt Hash 를 저장한다. DB 가 새어도 비밀번호 자체는 남지 않는다.
            // encode 의 반환 타입이 String? 로 선언돼 있지만 BCrypt 는 null 을 돌려주지 않는다.
            password = requireNotNull(passwordEncoder.encode(password)),
            name = name,
            githubUsername = githubUsername,
            role = role,
            seminarId = seminarId,
            status = UserStatus.PENDING,
            createdAt = now,
        )
        return userRepository.save(user)
    }
    
    fun getUser(id: Long): User 
        = userRepository.findByIdOrNull(id) ?: throw UserNotFoundException(id)
    
    fun approvalUser(user: User, status: UserStatus): User {
        if(user.status != UserStatus.PENDING) throw UserIsNotPendingException()
        
        val updatedUser = user.copy(status = status)
        return userRepository.save(updatedUser)
    }

    /**
     * 가입 신청 목록. 와장 계정은 나오지 않는다.
     * 운영진은 루키의 신청만 볼 수 있으므로, 운영진이 보낸 role 은 무시하고 ROOKIE 로 고정한다.
     */
    fun getApplicants(requester: User, status: UserStatus?, role: UserRole?, query: PageQuery): PageResponse<User> {
        if (role == UserRole.ADMIN) throw InvalidApplicantRoleException()

        val visibleRole = if (requester.role == UserRole.STAFF) UserRole.ROOKIE else role
        val statusName = status?.name
        val roleName = visibleRole?.name

        val users = userRepository.searchApplicants(statusName, roleName, query.size, query.offset)
        val total = userRepository.countApplicants(statusName, roleName)
        return PageResponse.of(users, query, total)
    }
}
