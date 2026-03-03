package com.example.bnianchorcheckinbackend.repositories

import com.example.bnianchorcheckinbackend.entities.Attendance
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AttendanceRepository : JpaRepository<Attendance, Long> {
    fun findByEventId(eventId: Int): List<Attendance>
    fun findByEventIdAndMemberId(eventId: Int, memberId: Int): Attendance?
    fun deleteByEventId(eventId: Int)
}
