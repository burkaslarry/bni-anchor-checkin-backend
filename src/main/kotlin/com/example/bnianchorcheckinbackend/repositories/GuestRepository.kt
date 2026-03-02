package com.example.bnianchorcheckinbackend.repositories

import com.example.bnianchorcheckinbackend.entities.Guest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface GuestRepository : JpaRepository<Guest, Long> {
    fun findByName(name: String): Optional<Guest>
    fun findByNameIgnoreCase(name: String): Optional<Guest>
    fun existsByNameIgnoreCase(name: String): Boolean
    fun findAllByOrderByNameAsc(): List<Guest>
    fun findByEventDate(eventDate: String): List<Guest>
}
