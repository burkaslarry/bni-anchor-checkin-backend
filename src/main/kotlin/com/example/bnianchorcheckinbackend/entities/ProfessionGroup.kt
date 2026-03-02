package com.example.bnianchorcheckinbackend.entities

import jakarta.persistence.*

@Entity
@Table(name = "bni_anchor_profession_groups")
data class ProfessionGroup(
    @Id
    @Column(name = "code", length = 1)
    val code: String,

    @Column(nullable = false, unique = true)
    var name: String
)
