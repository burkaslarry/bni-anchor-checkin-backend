package com.example.bnianchorcheckinbackend.entities

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(
    name = "bni_anchor_attendances",
    uniqueConstraints = [UniqueConstraint(columnNames = ["member_id", "event_id"])]
)
data class Attendance(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "member_id", nullable = false)
    var memberId: Int,

    @Column(name = "event_id", nullable = false)
    var eventId: Int,

    @Column(name = "check_in_time", nullable = false)
    var checkInTime: OffsetDateTime,

    @Column(nullable = false)
    var status: String = "absent",

    @Column(name = "late_code_used")
    var lateCodeUsed: Boolean = false
)
