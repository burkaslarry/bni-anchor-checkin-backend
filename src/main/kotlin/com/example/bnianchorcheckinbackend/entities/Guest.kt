package com.example.bnianchorcheckinbackend.entities

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "bni_anchor_guests")
data class Guest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var profession: String,

    @Column
    var referrer: String? = null,

    @Column
    var email: String? = null,

    @Column(name = "phone")
    var phoneNumber: String? = null,

    @Column(name = "event_date")
    var eventDate: String? = null,

    @Column(name = "created_at")
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
