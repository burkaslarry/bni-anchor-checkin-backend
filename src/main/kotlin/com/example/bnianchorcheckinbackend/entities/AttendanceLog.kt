package com.example.bnianchorcheckinbackend.entities

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "bni_anchor_attendance_logs")
data class AttendanceLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "attendee_id", nullable = false)
    var attendeeId: Int,

    @Column(name = "attendee_type", nullable = false)
    var attendeeType: String,

    @Column(name = "attendee_name", nullable = false)
    var attendeeName: String,

    @Column(name = "event_date", nullable = false)
    var eventDate: String,

    @Column(name = "checked_in_at", nullable = false)
    var checkedInAt: Instant = Instant.now(),

    @Column(nullable = false)
    var status: String = "on-time"
)
