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
    val source: String = "guest"  // Track which CSV file it came from
)

@Service
class GuestService {

    private val guests = ConcurrentHashMap<String, GuestData>()
    private val guestFiles = mutableListOf<String>()

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
                
                // Extract date from filename guest-event-YYYYMMDD.csv
                val dateStr = filename.substringAfter("guest-event-").substringBefore(".csv")
                try {
                    val fileDate = LocalDate.parse(dateStr, dateFormatter)
                    // Only load if the file date is today or in the future
                    if (fileDate.isBefore(today)) {
                        println("Skipping old guest file: $filename")
                        continue
                    }
                } catch (e: Exception) {
                    println("Could not parse date from filename $filename, loading anyway")
                }

                guestFiles.add(filename)
                println("Loading $filename...")
                
                BufferedReader(InputStreamReader(resource.inputStream)).use { reader ->
                    reader.lines().skip(1).forEach { line ->
                        // Handle CSV format: Name,Profession,Referrer
                        val parts = line.split(",").map { it.trim() }
                        if (parts.size >= 2 && parts[0].isNotBlank()) {
                            val guest = GuestData(
                                name = parts[0],
                                profession = if (parts.size > 1) parts[1] else "",
                                referrer = if (parts.size > 2) parts[2] else "",
                                source = filename
                            )
                            guests[parts[0].lowercase()] = guest
                            println("Loaded guest: ${parts[0]} - ${parts.getOrNull(1) ?: ""}")
                        }
                    }
                }
            }
            
            if (guestFiles.isEmpty()) {
                println("No current or future guest CSV files found")
            } else {
                println("Total guests loaded: ${guests.size} from ${guestFiles.size} file(s)")
            }
        } catch (e: Exception) {
            System.err.println("Error loading guest CSV data: ${e.message}")
            e.printStackTrace()
        }
    }

    fun getGuestByName(name: String): GuestData? {
        return guests[name.lowercase()]
    }

    fun getAllGuests(): List<GuestData> {
        return guests.values.toList().sortedBy { it.name }
    }

    fun getAllGuestsWithDomain(): List<Map<String, String>> {
        return guests.values
            .sortedBy { it.name }
            .map { 
                mapOf(
                    "name" to it.name, 
                    "profession" to it.profession,
                    "referrer" to it.referrer,
                    "type" to "guest"
                ) 
            }
    }
    
    fun getLoadedFiles(): List<String> {
        return guestFiles.toList()
    }
}
