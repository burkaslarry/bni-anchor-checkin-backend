package com.example.bnianchorcheckinbackend.repositories

import com.example.bnianchorcheckinbackend.entities.AttendanceLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AttendanceLogRepository : JpaRepository<AttendanceLog, Long> {
    fun findByEventDate(eventDate: String): List<AttendanceLog>
}
