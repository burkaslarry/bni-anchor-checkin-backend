package com.example.bnianchorcheckinbackend.entities

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "bni_anchor_members")
data class Member(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    var name: String,

    @Column
    var profession: String? = null,

    @Column(name = "profession_code", nullable = false)
    var professionCode: String = "A",

    @Column
    var position: String = "Member",

    @Column(name = "membership_id", unique = true)
    var membershipId: String? = null,

    @Column(unique = true)
    var email: String? = null,

    @Column(name = "phone_number", unique = true)
    var phoneNumber: String? = null,

    @Column(name = "standing")
    @Enumerated(EnumType.STRING)
    var standing: MemberStanding = MemberStanding.GREEN,

    @Column(name = "created_at")
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class MemberStanding {
    GREEN, YELLOW, RED, BLACK
}
