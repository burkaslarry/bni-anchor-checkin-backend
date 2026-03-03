package com.example.bnianchorcheckinbackend.entities

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalTime

@Entity
@Table(name = "bni_anchor_events")
data class Event(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    var name: String,

    @Column(name = "create_date", nullable = false)
    var createDate: LocalDate = LocalDate.now(),

    @Column(name = "registration_start_time", nullable = false)
    var registrationStartTime: LocalTime,

    @Column(name = "event_date", nullable = false)
    var eventDate: LocalDate,

    @Column(name = "start_time", nullable = false)
    var startTime: LocalTime,

    @Column(name = "end_time")
    var endTime: LocalTime? = null,

    @Column(name = "on_time_cutoff_time", nullable = false)
    var onTimeCutoffTime: LocalTime,

    @Column(name = "late_cutoff_time")
    var lateCutoffTime: LocalTime? = null
)
