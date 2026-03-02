package com.example.bnianchorcheckinbackend.repositories

import com.example.bnianchorcheckinbackend.entities.Event
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface EventRepository : JpaRepository<Event, Long> {
    fun findTopByOrderByEventDateDesc(): Event?
    fun findByEventDate(eventDate: LocalDate): Event?
    fun existsByEventDateBetween(start: LocalDate, end: LocalDate): Boolean
}
