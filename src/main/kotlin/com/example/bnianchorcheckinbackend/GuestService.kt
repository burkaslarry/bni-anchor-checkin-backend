package com.example.bnianchorcheckinbackend

import org.springframework.stereotype.Service
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import jakarta.annotation.PostConstruct
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class GuestData(
    val name: String,
    val profession: String,
    val referrer: String,
    val source: String = "guest",
    val eventDate: String? = null
)

@Service
class GuestService {

    private val guests = ConcurrentHashMap<String, GuestData>()
    private val guestFiles = mutableListOf<String>()
    private val bulkImportedGuests = ConcurrentHashMap<String, GuestData>()

    @PostConstruct
    fun init() {
        loadGuestData()
    }

    private fun loadGuestData() {
        try {
            val resolver = PathMatchingResourcePatternResolver()
            val resources = resolver.getResources("classpath*:guest-event-*.csv")
            val today = LocalDate.now()
            val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

            for (resource in resources) {
                val filename = resource.filename ?: continue
                val dateStr = filename.substringAfter("guest-event-").substringBefore(".csv")
                var eventDateForFile: String? = null
                try {
                    val fileDate = LocalDate.parse(dateStr, dateFormatter)
                    eventDateForFile = fileDate.toString()
                    val sevenDaysAgo = today.minusDays(7)
                    if (fileDate.isBefore(sevenDaysAgo)) continue
                } catch (e: Exception) { /* load anyway */ }

                guestFiles.add(filename)
                BufferedReader(InputStreamReader(resource.inputStream)).use { reader ->
                    reader.lines().skip(1).forEach { line ->
                        val parts = line.split(",").map { it.trim() }
                        if (parts.size >= 2 && parts[0].isNotBlank()) {
                            val guest = GuestData(
                                name = parts[0],
                                profession = if (parts.size > 1) parts[1] else "",
                                referrer = if (parts.size > 2) parts[2] else "",
                                source = filename,
                                eventDate = eventDateForFile
                            )
                            guests[parts[0].lowercase()] = guest
                        }
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("Error loading guest CSV data: ${e.message}")
        }
    }

    fun getGuestByName(name: String): GuestData? = guests[name.lowercase()] ?: bulkImportedGuests.values.find { it.name.equals(name, ignoreCase = true) }

    fun getAllGuestsWithDomain(): List<Map<String, String>> {
        val csvList = guests.values.map {
            mapOf("name" to it.name, "profession" to it.profession, "referrer" to it.referrer, "type" to "guest", "eventDate" to (it.eventDate ?: ""))
        }
        val bulkList = bulkImportedGuests.values.map {
            mapOf("name" to it.name, "profession" to it.profession, "referrer" to it.referrer, "type" to "guest", "eventDate" to (it.eventDate ?: ""))
        }
        return (csvList + bulkList).sortedBy { it["name"] }
    }

    private fun normalizeEventDate(s: String?): String? {
        val t = s?.trim() ?: return null
        if (t.isBlank()) return null
        return when {
            t.length == 8 && t.all { it.isDigit() } -> "${t.take(4)}-${t.takeLast(4).take(2)}-${t.takeLast(2)}"
            t.contains("-") -> t
            else -> t
        }
    }

    fun addBulkImportedGuests(records: List<ImportRecord>): ImportResult {
        var inserted = 0
        val errors = mutableListOf<String>()
        for (record in records) {
            try {
                val eventDate = normalizeEventDate(record.eventDate) ?: ""
                val key = "${record.name.lowercase()}|$eventDate"
                bulkImportedGuests[key] = GuestData(
                    name = record.name,
                    profession = record.profession,
                    referrer = record.referrer?.takeIf { it.isNotBlank() } ?: "",
                    source = "bulk-import",
                    eventDate = eventDate.ifBlank { null }
                )
                inserted++
            } catch (e: Exception) {
                errors.add("Failed to add ${record.name}: ${e.message}")
            }
        }
        return ImportResult(total = records.size, inserted = inserted, updated = 0, failed = records.size - inserted, errors = errors)
    }
}
