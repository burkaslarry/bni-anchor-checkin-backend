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
    @Operation(summary = "Get list of members")
    fun getMembers(): Map<String, List<String>> {
        return mapOf("members" to attendanceService.getMembers())
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

    @PostMapping("/api/events")
    @Operation(summary = "Create event")
    fun createEvent(@RequestBody request: EventRequest): Map<String, String> {
        attendanceService.createEvent(request)
        return mapOf("status" to "success", "message" to "Event created")
    }

    @GetMapping("/api/export")
    @Operation(summary = "Export records as CSV")
    fun exportRecords(): ResponseEntity<ByteArray> {
        val records = attendanceService.getAllRecords()
        val out = ByteArrayOutputStream()
        val writer = PrintWriter(out)
        writer.println("Name,Type,Check-in Time,Server Received Time")
        for (record in records) {
            writer.println("${record.name},${record.type},${record.timestamp},${record.receivedAt}")
        }
        writer.flush()
        writer.close()

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=attendance.csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(out.toByteArray())
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
}
