package com.example.bnianchorcheckinbackend

import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import jakarta.annotation.PostConstruct

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
            // List of known guest-event CSV files to load
            val guestCsvFiles = listOf(
                "guest-event-20260218.csv"
                // Add more files here as needed
            )
            
            for (filename in guestCsvFiles) {
                val inputStream = Thread.currentThread().contextClassLoader.getResourceAsStream(filename)
                    ?: javaClass.getResourceAsStream("/$filename")
                
                if (inputStream == null) {
                    println("Guest file $filename not found, skipping")
                    continue
                }
                
                guestFiles.add(filename)
                println("Loading $filename...")
                
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
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
                println("No guest CSV files found")
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
