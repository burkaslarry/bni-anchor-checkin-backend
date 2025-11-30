package com.example.bnianchorcheckinbackend

import org.springframework.core.io.ClassPathResource
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
            val resource = ClassPathResource("members.csv")
            println("Loading members.csv from classpath: ${resource.exists()}")
            
            resource.inputStream.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.lines().skip(1).forEach { line ->
                        val parts = line.split("|").map { it.trim() }
                        if (parts.size >= 3) {
                            val membershipId = if (parts.size > 3 && parts[2].equals("Member", ignoreCase = true)) parts[3] else null
                            val referrer = if (parts.size > 4 && parts[2].equals("Guest", ignoreCase = true)) parts[4] else null
                            val member = MemberData(
                                name = parts[0],
                                domain = if (parts.size > 1) parts[1] else "",
                                type = if (parts.size > 2) parts[2] else "Member",
                                membershipId = membershipId,
                                referrer = referrer
                            )
                            members[parts[0].lowercase()] = member
                            println("Loaded member: ${parts[0]}")
                        }
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
}
