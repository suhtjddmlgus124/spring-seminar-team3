package com.wafflestudio.spring2026.waggle.service

import java.time.Instant
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import com.wafflestudio.spring2026.waggle.model.User
import com.wafflestudio.spring2026.waggle.model.UserRole
import com.wafflestudio.spring2026.waggle.model.UserStatus
import com.wafflestudio.spring2026.waggle.repository.UserRepository
import com.wafflestudio.spring2026.waggle.repository.SeminarRepository
import com.wafflestudio.spring2026.waggle.service.SeminarNotFoundException

class UserNotFoundException(userId: Long): RuntimeException(
    "ID가 ${userId}인 사용자를 찾을 수 없습니다."
)

class UserEmailAlreadyExistsException: RuntimeException(
    "이미 존재하는 이메일입니다."
)

class UserIsNotPendingException: RuntimeException(
    "사용자가 PENDING 상태가 아닙니다."
)

@Service
class UserService(
    private val userRepository: UserRepository,
    private val seminarRepository: SeminarRepository,
) {
    fun createUser(
        email: String,
        password: String,
        name: String,
        githubUsername: String,
        role: UserRole,
        seminarId: Long?
    ): User {
        // 이메일은 중복될 수 없음
        if(userRepository.existsByEmail(email)) 
            throw UserEmailAlreadyExistsException()
        
        // 세미나가 존재해야 함
        if(seminarId != null && !seminarRepository.existsById(seminarId)) 
            throw SeminarNotFoundException(seminarId)
        
        val user = User(
            email = email,
            password = password, // TODO: SECURITY! - 임시로 비밀번호를 해쉬하지 않고 원문으로 저장함. 배포 환경에서는 반드시 수정해야 함.
            name = name,
            githubUsername = githubUsername,
            role = role,
            seminarId = seminarId,
            status = UserStatus.PENDING,
            createdAt = Instant.now(),
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
}