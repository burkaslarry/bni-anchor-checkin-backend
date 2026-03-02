package com.example.bnianchorcheckinbackend.repositories

import com.example.bnianchorcheckinbackend.entities.Member
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface MemberRepository : JpaRepository<Member, Long> {
    fun findByName(name: String): Optional<Member>
    fun findByNameIgnoreCase(name: String): Optional<Member>
    fun existsByNameIgnoreCase(name: String): Boolean
    fun findAllByOrderByNameAsc(): List<Member>
}
