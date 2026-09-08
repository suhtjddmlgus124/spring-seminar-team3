package com.wafflestudio.spring2026.meeting.service

import com.wafflestudio.spring2026.meeting.MeetingNotFoundException
import com.wafflestudio.spring2026.meeting.model.Meeting
import com.wafflestudio.spring2026.meeting.repository.MeetingRepository
import org.springframework.stereotype.Service

@Service
class MeetingService(
    private val meetingRepository: MeetingRepository,
) {
    fun createMeeting(
        title: String,
        capacity: Int,
    ): Meeting =
        meetingRepository.save(
            title = title,
            capacity = capacity,
        )

    fun getMeeting(id: Long): Meeting =
        meetingRepository.findById(id)
            ?: throw MeetingNotFoundException(id)
    
    fun getMeetings(): List<Meeting> = meetingRepository.findAll()
    
    fun deleteMeeting(id: Long): Meeting = 
        meetingRepository.deleteById(id) 
            ?: throw MeetingNotFoundException(id)
            
    fun updateMeeting(
        id: Long,
        title: String?,
        capacity: Int?,
    ): Meeting =
        meetingRepository.update(
            id = id,
            title = title,
            capacity = capacity,
        ) ?: throw MeetingNotFoundException(id)
}
