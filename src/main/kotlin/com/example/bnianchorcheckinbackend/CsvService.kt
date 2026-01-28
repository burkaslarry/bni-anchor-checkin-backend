package com.example.bnianchorcheckinbackend

import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import jakarta.annotation.PostConstruct

data class MemberData(
    val name: String,
    val domain: String,
    val type: String,
    val membershipId: String?,
    val referrer: String?
)

@Service
class CsvService {

    private val members = ConcurrentHashMap<String, MemberData>()

    @PostConstruct
    fun init() {
        loadCsvData()
    }

    private fun loadCsvData() {
        try {
            // Try to load member-anchor.csv first, fallback to members.csv
            val inputStream = Thread.currentThread().contextClassLoader.getResourceAsStream("member-anchor.csv")
                ?: javaClass.getResourceAsStream("/member-anchor.csv")
                ?: Thread.currentThread().contextClassLoader.getResourceAsStream("members.csv")
                ?: javaClass.getResourceAsStream("/members.csv")
                ?: throw IllegalStateException("No member CSV file found in classpath")
            
            println("Loading member CSV file...")
            
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.lines().skip(1).forEach { line ->
                    // Handle CSV format: Name,Type,Membership ID
                    val parts = line.split(",").map { it.trim() }
                    if (parts.size >= 2 && parts[0].isNotBlank()) {
                        val member = MemberData(
                            name = parts[0],
                            domain = if (parts.size > 1) parts[1] else "",
                            type = "Member",
                            membershipId = if (parts.size > 2) parts[2] else null,
                            referrer = null
                        )
                        members[parts[0].lowercase()] = member
                        println("Loaded member: ${parts[0]} - ${parts.getOrNull(1) ?: ""}")
                    }
                }
            }
            println("Total members loaded: ${members.size}")
        } catch (e: Exception) {
            System.err.println("Error loading CSV data: ${e.message}")
            e.printStackTrace()
        }
    }

    fun getMemberByName(name: String): MemberData? {
        return members[name.lowercase()]
    }

    fun getAllMembers(): List<String> {
        return members.values.map { it.name }.sorted()
    }

    fun getAllMembersWithDomain(): List<Map<String, String>> {
        return members.values
            .sortedBy { it.name }
            .map { mapOf("name" to it.name, "domain" to it.domain) }
    }
    
    /**
     * Get members list for matching API
     */
    fun getMembers(): List<MemberData> {
        return members.values.toList().sortedBy { it.name }
    }
}
