package com.wafflestudio.spring2026.waggle.controller

import java.time.LocalDateTime
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.http.ResponseEntity
import com.wafflestudio.spring2026.waggle.service.SeminarService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import jakarta.validation.Valid
import java.net.URI
import com.wafflestudio.spring2026.waggle.dto.seminar.SeminarResponse
import com.wafflestudio.spring2026.waggle.dto.seminar.SeminarCreateRequest
import com.wafflestudio.spring2026.waggle.dto.seminar.SeminarCreateResponse
import com.wafflestudio.spring2026.waggle.dto.seminar.SeminarUpdateRequest

@RequestMapping("/seminars")
@RestController
class SeminarController(
    private val seminarService: SeminarService
) {
    
    @GetMapping("/{id}")
    fun getSeminar(@PathVariable("id") id: Long): ResponseEntity<SeminarResponse> {
        val seminar = seminarService.getSeminar(id)
        val enrolledCount = seminarService.getEnrolledCount(seminar)
        val sessionCount = seminarService.getSessionCount(seminar)
        val status = seminarService.getStatus(seminar, enrolledCount, LocalDateTime.now())

        val response = SeminarResponse.from(seminar, enrolledCount, status, sessionCount)        
        return ResponseEntity.ok(response)
    }
    
    @PostMapping
    fun createSeminar(@Valid @RequestBody request: SeminarCreateRequest): ResponseEntity<SeminarCreateResponse> {
        val seminar = seminarService.createSeminar(
            title = request.title,
            description = request.description,
            capacity = request.capacity,
            applyStartAt = request.applyStartAt,
            applyEndAt = request.applyEndAt,
            totalGraceDays = request.totalGraceDays,
        )
        
        val response = SeminarCreateResponse.from(seminar, LocalDateTime.now())
        return ResponseEntity
            .created(URI.create("/seminars/${seminar.id}"))
            .body(response)
    }
    
    @PatchMapping("/{id}")
    fun updateSeminar(@PathVariable("id") id: Long, @Valid @RequestBody request: SeminarUpdateRequest): ResponseEntity<SeminarResponse> {
        val seminar = seminarService.getSeminar(id)
        
        val title = 
            if(request.title.isPresent) request.title.get() 
            else seminar.title
        val description = 
            if(request.description.isPresent) request.description.orElse(null) 
            else seminar.description
        val updatedSeminar = seminarService.updateSeminar(seminar, title, description)
        
        val enrolledCount = seminarService.getEnrolledCount(seminar)
        val sessionCount = seminarService.getSessionCount(seminar)
        val status = seminarService.getStatus(seminar, enrolledCount, LocalDateTime.now())
        
        val response = SeminarResponse.from(updatedSeminar, enrolledCount, status, sessionCount)
        return ResponseEntity.ok(response)
    }
}