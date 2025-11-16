package com.example.bnianchorcheckinbackend

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class QrScanRequest(val qrPayload: String)

@RestController
@RequestMapping("/api/attendance")
@Tag(name = "Attendance", description = "Endpoints for scanning and querying attendance records.")
class AttendanceController(private val attendanceService: AttendanceService) {

    @PostMapping("/scan")
    @Operation(summary = "Record attendance using a QR payload.")
    fun recordAttendance(@RequestBody request: QrScanRequest): ResponseEntity<Map<String, String>> {
        return try {
            val message = attendanceService.recordAttendance(request.qrPayload)
            ResponseEntity.ok(mapOf("message" to message))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("message" to e.message!!))
        }
    }

    @GetMapping("/member")
    @Operation(summary = "Fetch attendance history for a specific member.")
    fun searchMemberAttendance(@RequestParam name: String): List<MemberAttendance> {
        return attendanceService.searchMemberAttendance(name)
    }

    @GetMapping("/event")
    @Operation(summary = "Get attendance roster for a given event date.")
    fun searchEventAttendance(@RequestParam date: String): List<EventAttendance> {
        return attendanceService.searchEventAttendance(date)
    }
}
