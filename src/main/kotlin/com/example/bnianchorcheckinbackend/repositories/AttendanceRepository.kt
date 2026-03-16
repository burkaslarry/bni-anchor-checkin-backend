package com.example.bnianchorcheckinbackend.repositories

import com.example.bnianchorcheckinbackend.entities.Attendance
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * JPA repository for [Attendance] (event_id, member_id, status, check_in_time, etc.).
 * Query intent: by event; by event+member; bulk delete by event. No paging; use for single-event scope.
 */
@Repository
interface AttendanceRepository : JpaRepository<Attendance, Long> {
    /** All attendances for an event. Use for report/export. */
    fun findByEventId(eventId: Int): List<Attendance>
    /** Single member's attendance for an event (e.g. duplicate check). */
    fun findByEventIdAndMemberId(eventId: Int, memberId: Int): Attendance?
    /** Remove all attendances for an event (e.g. clear-all). */
    fun deleteByEventId(eventId: Int)
}
