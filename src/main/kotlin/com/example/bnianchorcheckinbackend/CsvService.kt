package com.example.bnianchorcheckinbackend

import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

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

    init {
        loadCsvData()
    }

    private fun loadCsvData() {
        val resource = "members.csv"
        try {
            InputStreamReader(javaClass.classLoader.getResourceAsStream(resource)).use { isr ->
                BufferedReader(isr).use { reader ->
                    reader.lines().skip(1).forEach { line ->
                        val parts = line.split("|").map { it.trim() }
                        if (parts.size == 5) {
                            val membershipId = if (parts[2].equals("Member", ignoreCase = true)) parts[3] else null
                            val referrer = if (parts[2].equals("Guest", ignoreCase = true)) parts[4] else null
                            val member = MemberData(
                                name = parts[0],
                                domain = parts[1],
                                type = parts[2],
                                membershipId = membershipId,
                                referrer = referrer
                            )
                            members[parts[0].lowercase()] = member
                        }
                    }
                }
            }
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
