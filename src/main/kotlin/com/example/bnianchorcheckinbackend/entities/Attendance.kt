package com.example.bnianchorcheckinbackend.entities

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalTime

@Entity
@Table(
    name = "bni_anchor_attendances",
    uniqueConstraints = [UniqueConstraint(columnNames = ["member_name", "event_id"])]
)
data class Attendance(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "event_id", nullable = false)
    var eventId: Int,

    @Column(name = "event_date", nullable = false)
    var eventDate: LocalDate,

    @Column(name = "member_name", nullable = false)
    var memberName: String,

    @Column(nullable = false)
    var status: String = "absent",

    @Column(name = "check_in_time")
    var checkInTime: LocalTime? = null,

    @Column
    var role: String? = "MEMBER"
)
