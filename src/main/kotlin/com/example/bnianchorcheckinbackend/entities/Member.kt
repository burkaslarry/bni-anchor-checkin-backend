package com.example.bnianchorcheckinbackend.entities

import jakarta.persistence.*
import java.time.OffsetDateTime

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

    @Column(name = "created_at", insertable = false, updatable = false)
    var createdAt: OffsetDateTime? = null,

    @Column(name = "updated_at", insertable = false, updatable = false)
    var updatedAt: OffsetDateTime? = null
)

enum class MemberStanding {
    GREEN, YELLOW, RED, BLACK
}
