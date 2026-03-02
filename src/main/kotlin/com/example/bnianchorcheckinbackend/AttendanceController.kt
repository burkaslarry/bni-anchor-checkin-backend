package com.example.bnianchorcheckinbackend

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.io.ByteArrayOutputStream
import java.io.PrintWriter

data class QrScanRequest(val qrPayload: String)

data class AttendanceLogRequest(
    val attendeeId: Int?,
    val attendeeType: String,
    val attendeeName: String,
    val eventDate: String,
    val checkedInAt: String,
    val status: String
)

@RestController
@Tag(name = "Attendance", description = "Endpoints for scanning and querying attendance records.")
class AttendanceController(
    private val attendanceService: AttendanceService,
    private val guestService: GuestService,
    private val databaseMemberService: DatabaseMemberService?,
    @Autowired(required = false) private val eventDbService: EventDbService?
) {

    @PostMapping("/api/attendance/scan")
    @Operation(summary = "Record attendance using a QR payload.")
    fun recordAttendance(@RequestBody request: QrScanRequest): ResponseEntity<Map<String, String>> {
        return try {
            val message = attendanceService.recordAttendance(request.qrPayload)
            ResponseEntity.ok(mapOf("message" to message))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("message" to e.message!!))
        }
    }

    @GetMapping("/api/members")
    @Operation(summary = "Get list of members with domain info and standing")
    fun getMembers(): Map<String, List<Map<String, Any>>> {
        // Try to get from PostgreSQL database first, fallback to CSV
        return if (databaseMemberService != null) {
            try {
                val dbMembers = databaseMemberService.getAllMembers()
                if (dbMembers.isNotEmpty()) {
                    mapOf("members" to dbMembers)
                } else {
                    // Fallback to CSV if database is empty
                    mapOf("members" to attendanceService.getMembersWithDomain())
                }
            } catch (e: Exception) {
                // Fallback to CSV on any database error
                mapOf("members" to attendanceService.getMembersWithDomain())
            }
        } else {
            // Use CSV if database service is not available
            mapOf("members" to attendanceService.getMembersWithDomain())
        }
    }

    @GetMapping("/api/guests")
    @Operation(summary = "Get list of pre-registered guests with profession info")
    fun getGuests(): Map<String, List<Map<String, String>>> {
        // Try to get from PostgreSQL database first, fallback to CSV
        return if (databaseMemberService != null) {
            try {
                val dbGuests = databaseMemberService.getAllGuests()
                if (dbGuests.isNotEmpty()) {
                    mapOf("guests" to dbGuests)
                } else {
                    // Fallback to CSV if database is empty
                    mapOf("guests" to guestService.getAllGuestsWithDomain())
                }
            } catch (e: Exception) {
                // Fallback to CSV on any database error
                mapOf("guests" to guestService.getAllGuestsWithDomain())
            }
        } else {
            // Use CSV if database service is not available
            mapOf("guests" to guestService.getAllGuestsWithDomain())
        }
    }

    @PostMapping("/api/checkin")
    @Operation(summary = "Record check-in")
    fun checkIn(@RequestBody request: CheckInRequest): ResponseEntity<Map<String, String>> {
        return try {
            val message = attendanceService.recordCheckIn(request)
            ResponseEntity.ok(mapOf("status" to "success", "message" to message))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("status" to "error", "message" to e.message!!))
        }
    }

    @GetMapping("/api/records")
    @Operation(summary = "Get all records")
    fun getRecords(): Map<String, List<CheckInRecord>> {
        return mapOf("records" to attendanceService.getAllRecords())
    }

    @DeleteMapping("/api/records")
    @Operation(summary = "Clear all records")
    fun clearRecords(): Map<String, String> {
        attendanceService.clearAllRecords()
        return mapOf("status" to "success", "message" to "All records cleared")
    }

    @DeleteMapping("/api/records/{index}")
    @Operation(summary = "Delete a specific record by index")
    fun deleteRecord(@PathVariable index: Int): ResponseEntity<Map<String, String>> {
        return try {
            attendanceService.deleteRecord(index)
            ResponseEntity.ok(mapOf("status" to "success", "message" to "Record deleted"))
        } catch (e: IndexOutOfBoundsException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("status" to "error", "message" to "Record not found"))
        }
    }

    @PostMapping("/api/events")
    @Operation(summary = "Create event with time settings, initializes all members as absent")
    fun createEvent(@RequestBody request: EventRequest): ResponseEntity<Map<String, Any>> {
        val log = org.slf4j.LoggerFactory.getLogger(AttendanceController::class.java)
        return try {
            val eventData = if (eventDbService != null) {
                try {
                    eventDbService.createEvent(request)
                } catch (dbEx: Exception) {
                    log.warn("DB createEvent failed ({}), falling back to in-memory", dbEx.message)
                    attendanceService.createEvent(request)
                }
            } else {
                attendanceService.createEvent(request)
            }
            ResponseEntity.ok(mapOf(
                "status" to "success",
                "message" to "Event created with all members set to absent",
                "event" to eventData
            ))
        } catch (e: java.time.format.DateTimeParseException) {
            val msg = "Invalid date or time format. Use date YYYY-MM-DD and times HH:mm or HH:mm:ss. ${e.message}"
            log.warn("Create event failed: {}", msg)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf(
                "status" to "error",
                "message" to msg
            ))
        } catch (e: IllegalArgumentException) {
            log.warn("Create event failed: {}", e.message)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf(
                "status" to "error",
                "message" to (e.message ?: "Invalid request"))
            )
        } catch (e: Exception) {
            log.error("Create event failed", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf(
                "status" to "error",
                "message" to (e.message ?: "Event creation failed. Check server logs for details.")
            ))
        }
    }
    
    @GetMapping("/api/report")
    @Operation(summary = "Get report data for the current event")
    fun getReportData(): ResponseEntity<ReportData> {
        val reportData = try {
            eventDbService?.getReportData()
        } catch (e: Exception) {
            org.slf4j.LoggerFactory.getLogger(AttendanceController::class.java)
                .warn("DB getReportData failed ({}), falling back to in-memory", e.message)
            null
        } ?: attendanceService.getReportData()
        return if (reportData != null) {
            ResponseEntity.ok(reportData)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }
    
    @GetMapping("/api/events/current")
    @Operation(summary = "Get current event")
    fun getCurrentEvent(): ResponseEntity<EventData> {
        val event = try {
            eventDbService?.getCurrentEvent()
        } catch (e: Exception) {
            null
        } ?: attendanceService.getCurrentEvent()
        return if (event != null) {
            ResponseEntity.ok(event)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }
    
    @GetMapping("/api/events/check")
    @Operation(summary = "Check if event exists for a given date")
    fun checkEventExists(@RequestParam date: String): Map<String, Any> {
        val exists = try {
            eventDbService?.hasEventForDate(date)
        } catch (e: Exception) { null } ?: (attendanceService.getCurrentEvent()?.date == date)
        return mapOf("exists" to exists)
    }
    
    @GetMapping("/api/events/check-this-week")
    @Operation(summary = "Check if event exists in the current week")
    fun checkEventThisWeek(): Map<String, Any> {
        val exists = try {
            eventDbService?.hasEventThisWeek()
        } catch (e: Exception) { null } ?: run {
            val memEvent = attendanceService.getCurrentEvent()
            if (memEvent != null) {
                val eventDate = java.time.LocalDate.parse(memEvent.date)
                val today = java.time.LocalDate.now()
                val weekStart = today.with(java.time.DayOfWeek.MONDAY)
                val weekEnd = today.with(java.time.DayOfWeek.SUNDAY)
                !eventDate.isBefore(weekStart) && !eventDate.isAfter(weekEnd)
            } else false
        }
        return mapOf("exists" to exists)
    }
    
    @GetMapping("/api/events/for-date")
    @Operation(summary = "Get event details for a specific date")
    fun getEventForDate(@RequestParam date: String): ResponseEntity<Map<String, Any>> {
        val dbEvent = try {
            eventDbService?.getEventForDate(date)
        } catch (e: Exception) { null }
        if (dbEvent != null) {
            return ResponseEntity.ok(mapOf("id" to dbEvent.id, "name" to dbEvent.name))
        }
        // Fallback: check in-memory
        val memEvent = attendanceService.getCurrentEvent()
        return if (memEvent != null && memEvent.date == date) {
            ResponseEntity.ok(mapOf("id" to memEvent.id, "name" to memEvent.name))
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }
    
    @PostMapping("/api/attendance/log")
    @Operation(summary = "Log attendance record directly")
    fun logAttendance(@RequestBody request: AttendanceLogRequest): ResponseEntity<Map<String, String>> {
        val log = org.slf4j.LoggerFactory.getLogger(AttendanceController::class.java)
        // Try DB first
        val dbError: Exception? = try {
            eventDbService?.logAttendance(request)
            null
        } catch (e: Exception) {
            log.warn("DB logAttendance failed ({}), falling back to in-memory", e.message)
            e
        }
        if (dbError == null) {
            return ResponseEntity.ok(mapOf("status" to "success", "message" to "Attendance logged successfully"))
        }
        // Fallback: record in in-memory service so it shows up in the report
        return try {
            val fallbackRequest = CheckInRequest(
                name = request.attendeeName,
                type = request.attendeeType,
                currentTime = request.checkedInAt,
                domain = ""
            )
            attendanceService.recordCheckIn(fallbackRequest)
            ResponseEntity.ok(mapOf("status" to "success", "message" to "Attendance logged (in-memory)"))
        } catch (e2: Exception) {
            if (e2.message?.contains("已經簽到") == true) {
                ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(mapOf("status" to "already_checked", "message" to (e2.message ?: "Already checked in")))
            } else {
                log.error("In-memory logAttendance also failed", e2)
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(mapOf("status" to "error", "message" to (e2.message ?: "Failed to log attendance")))
            }
        }
    }
    
    @DeleteMapping("/api/events/clear-all")
    @Operation(summary = "Clear all events and attendance records")
    fun clearAllEventsAndAttendance(): Map<String, String> {
        eventDbService?.clearAllEventsAndAttendance()
        attendanceService.clearAllEventsAndAttendance()
        return mapOf("status" to "success", "message" to "All events and attendance records cleared")
    }

    @GetMapping("/api/export")
    @Operation(summary = "Export records as CSV with attendance status")
    fun exportRecords(): ResponseEntity<ByteArray> {
        val out = ByteArrayOutputStream()
        // Add UTF-8 BOM for Excel compatibility
        out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        val writer = PrintWriter(out)
        writer.println("姓名,專業領域,類別,出席狀態,簽到時間")
        
        // Get current event's attendance data
        val reportData = attendanceService.getReportData()
        val records = attendanceService.getAllRecords()
        
        if (reportData != null) {
            // Export all members with their status from the event attendance
            val membersWithDomain = attendanceService.getMembersWithDomain()
            val validMemberNames = membersWithDomain.map { it["name"] }.toSet()
            
            // Combine attendees and absentees (only valid members)
            for (attendee in reportData.attendees) {
                // Skip if not a valid member (not in members.csv)
                if (!validMemberNames.contains(attendee.memberName)) continue
                
                val memberDomain = membersWithDomain.find { it["name"] == attendee.memberName }?.get("domain") as? String ?: ""
                val domain = memberDomain.replace(",", "，")
                val statusText = when (attendee.status) {
                    "on-time" -> "準時"
                    "late" -> "遲到"
                    else -> attendee.status
                }
                val checkInTime = attendee.checkInTime ?: ""
                writer.println("${attendee.memberName},${domain},member,${statusText},${checkInTime}")
            }
            
            for (absentee in reportData.absentees) {
                // Skip if not a valid member (not in members.csv)
                if (!validMemberNames.contains(absentee.memberName)) continue
                
                val memberDomain = membersWithDomain.find { it["name"] == absentee.memberName }?.get("domain") as? String ?: ""
                val domain = memberDomain.replace(",", "，")
                writer.println("${absentee.memberName},${domain},member,缺席,")
            }
            
            // Export guests from check-in records
            val guests = records.filter { it.type.equals("guest", ignoreCase = true) }
            for (guest in guests) {
                val domain = guest.domain.replace(",", "，")
                // Determine guest status based on timestamp and cutoff
                val guestStatus = determineGuestStatus(guest.timestamp, reportData.onTimeCutoff)
                writer.println("${guest.name},${domain},guest,${guestStatus},${guest.timestamp}")
            }
        } else {
            // Fallback: export raw records if no event exists
            for (record in records) {
                val domain = record.domain.replace(",", "，")
                writer.println("${record.name},${domain},${record.type},已簽到,${record.timestamp}")
            }
        }
        
        writer.flush()
        writer.close()

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=attendance.csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(out.toByteArray())
    }
    
    private fun determineGuestStatus(timestamp: String, onTimeCutoff: String): String {
        return try {
            val cutoffTime = java.time.LocalTime.parse(onTimeCutoff)
            // Try to extract time from timestamp (supports various formats)
            val timePattern = Regex("T?(\\d{2}:\\d{2}:\\d{2})")
            val match = timePattern.find(timestamp)
            if (match != null) {
                val checkInTime = java.time.LocalTime.parse(match.groupValues[1])
                if (checkInTime.isBefore(cutoffTime)) "準時" else "遲到"
            } else {
                "已簽到"
            }
        } catch (e: Exception) {
            "已簽到"
        }
    }

    @GetMapping("/api/attendance/member")
    @Operation(summary = "Fetch attendance history for a specific member.")
    fun searchMemberAttendance(@RequestParam name: String): List<MemberAttendance> {
        return attendanceService.searchMemberAttendance(name)
    }

    @GetMapping("/api/attendance/event")
    @Operation(summary = "Get attendance roster for a given event date.")
    fun searchEventAttendance(@RequestParam date: String): List<EventAttendance> {
        return attendanceService.searchEventAttendance(date)
    }
    
    // ===== AI Insights Endpoints (Phase 2 - For Future AI Integration) =====
    
    @PostMapping("/api/insights/generate")
    @Operation(summary = "Generate AI insights report for an event (stub for future AI integration)")
    fun generateAIInsights(@RequestBody request: AIInsightRequest): ResponseEntity<AIInsightResponse> {
        val insights = attendanceService.generateInsights(request)
        return ResponseEntity.ok(insights)
    }
    
    @GetMapping("/api/insights/{eventId}")
    @Operation(summary = "Get previously generated AI insights for an event")
    fun getEventInsights(@PathVariable eventId: Int): ResponseEntity<List<AIInsightResponse>> {
        val insights = attendanceService.getEventInsights(eventId)
        return if (insights.isNotEmpty()) {
            ResponseEntity.ok(insights)
        } else {
            ResponseEntity.ok(emptyList())
        }
    }
    
    @GetMapping("/api/insights/data-export/{eventId}")
    @Operation(summary = "Export event data in AI-ready format for external processing")
    fun exportAIReadyData(@PathVariable eventId: Int): ResponseEntity<Map<String, Any>> {
        val exportData = attendanceService.exportAIReadyData(eventId)
        return if (exportData != null) {
            ResponseEntity.ok(exportData)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "Event not found"))
        }
    }
}
