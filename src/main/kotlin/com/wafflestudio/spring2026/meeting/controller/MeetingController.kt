package com.wafflestudio.spring2026.meeting.controller

import com.wafflestudio.spring2026.meeting.dto.MeetingCreateRequest
import com.wafflestudio.spring2026.meeting.dto.MeetingUpdateRequest
import com.wafflestudio.spring2026.meeting.dto.MeetingResponse
import com.wafflestudio.spring2026.meeting.service.MeetingService
import org.springframework.http.HttpStatus
import jakarta.validation.Valid
import java.net.URI
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.ResponseStatus

@RestController
@RequestMapping("/meetings")
class MeetingController(
    private val meetingService: MeetingService,
) {
    @PostMapping
    fun createMeeting(
        @Valid @RequestBody request: MeetingCreateRequest,
    ): ResponseEntity<MeetingResponse> {
        val meeting = meetingService.createMeeting(
            title = request.title,
            capacity = request.capacity,
        )

        val response = MeetingResponse.from(meeting)

        return ResponseEntity
            .created(URI.create("/meetings/${meeting.id}"))
            .body(response)
    }

    @GetMapping("/{id}")
    fun getMeeting(
        @PathVariable("id") id: Long,
    ): ResponseEntity<MeetingResponse> {
        val meeting = meetingService.getMeeting(id)

        return ResponseEntity.ok(
            MeetingResponse.from(meeting),
        )
    }
    
    @GetMapping
    fun getMeetings(): ResponseEntity<List<MeetingResponse>> {
        val meetings = meetingService.getMeetings()
        
        return ResponseEntity.ok(
            meetings.map {MeetingResponse.from(it)}
        )
    }
    
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMeeting(
        @PathVariable("id") id: Long
    ) {
        meetingService.deleteMeeting(id)  
    }
    
    @PatchMapping("/{id}")
    fun updateMeeting(
        @PathVariable("id") id: Long,
        @Valid @RequestBody request: MeetingUpdateRequest,
    ): ResponseEntity<MeetingResponse> {
        val meeting = meetingService.updateMeeting(
            id = id,
            title = request.title,
            capacity = request.capacity,
        )
        
        return ResponseEntity.ok(
            MeetingResponse.from(meeting),
        )
    }
}
