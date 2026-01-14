package com.example.bnianchorcheckinbackend

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.io.ByteArrayOutputStream
import java.io.PrintWriter

data class QrScanRequest(val qrPayload: String)

@RestController
@Tag(name = "Attendance", description = "Endpoints for scanning and querying attendance records.")
class AttendanceController(private val attendanceService: AttendanceService) {

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
    @Operation(summary = "Get list of members with domain info")
    fun getMembers(): Map<String, List<Map<String, String>>> {
        return mapOf("members" to attendanceService.getMembersWithDomain())
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
        val eventData = attendanceService.createEvent(request)
        return ResponseEntity.ok(mapOf(
            "status" to "success", 
            "message" to "Event created with all members set to absent",
            "event" to eventData
        ))
    }
    
    @GetMapping("/api/report")
    @Operation(summary = "Get report data for the current event")
    fun getReportData(): ResponseEntity<ReportData> {
        val reportData = attendanceService.getReportData()
        return if (reportData != null) {
            ResponseEntity.ok(reportData)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }
    
    @GetMapping("/api/events/current")
    @Operation(summary = "Get current event")
    fun getCurrentEvent(): ResponseEntity<EventData> {
        val event = attendanceService.getCurrentEvent()
        return if (event != null) {
            ResponseEntity.ok(event)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }
    
    @DeleteMapping("/api/events/clear-all")
    @Operation(summary = "Clear all events and attendance records")
    fun clearAllEventsAndAttendance(): Map<String, String> {
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
            
            // Combine attendees and absentees
            for (attendee in reportData.attendees) {
                val memberDomain = membersWithDomain.find { it["name"] == attendee.memberName }?.get("domain") ?: ""
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
                val memberDomain = membersWithDomain.find { it["name"] == absentee.memberName }?.get("domain") ?: ""
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
