package com.example.bnianchorcheckinbackend.repositories

import com.example.bnianchorcheckinbackend.entities.Attendance
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface AttendanceRepository : JpaRepository<Attendance, Long> {
    fun findByEventId(eventId: Int): List<Attendance>
    fun findByEventIdAndMemberName(eventId: Int, memberName: String): Attendance?
    fun findByEventDate(eventDate: LocalDate): List<Attendance>
    fun deleteByEventId(eventId: Int)
}
