package com.example.bnianchorcheckinbackend.entities

import jakarta.persistence.*
import java.time.OffsetDateTime

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

    @Column(name = "phone_number")
    var phoneNumber: String? = null,

    @Column(name = "event_date")
    var eventDate: String? = null,

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: OffsetDateTime? = null,

    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: OffsetDateTime? = null
)
