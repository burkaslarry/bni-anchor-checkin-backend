package com.example.bnianchorcheckinbackend.repositories

import com.example.bnianchorcheckinbackend.entities.ProfessionGroup
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ProfessionGroupRepository : JpaRepository<ProfessionGroup, String>
