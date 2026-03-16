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

/** Request body for POST /api/attendance/scan: QR payload JSON string. */
data class QrScanRequest(val qrPayload: String)

/**
 * Request body for POST /api/attendance/log: direct attendance log (member or guest).
 * @param attendeeId Null for guests; member ID when type is member.
 * @param attendeeType "member" | "guest" | "vip" | "speaker"
 * @param eventDate YYYY-MM-DD
 * @param checkedInAt ISO or time string
 * @param status e.g. "on-time" | "late"
 */
data class AttendanceLogRequest(
    val attendeeId: Int?,
    val attendeeType: String,
    val attendeeName: String,
    val attendeeProfession: String? = null,
    val eventDate: String,
    val checkedInAt: String,
    val status: String
)

/**
 * REST controller for attendance, members, guests, events, records, export, report, and AI insights.
 * Uses in-memory [AttendanceService] and optional DB ([DatabaseMemberService], [EventDbService]). No auth enforced.
 * Side effects: DB read/write when DB services present; in-memory state; WebSocket not used here (handled elsewhere).
 *
 * Endpoints: POST /api/attendance/scan, GET /api/members, GET /api/guests, POST /api/checkin, GET/DELETE /api/records,
 * POST /api/events, GET /api/report, GET /api/events/current, GET /api/events/check, GET /api/events/check-this-week,
 * GET /api/events/for-date, POST /api/attendance/log, DELETE /api/events/clear-all, GET /api/export,
 * GET /api/attendance/member, GET /api/attendance/event, POST and GET /api/insights (generate, list, data-export).
 */
@RestController
@Tag(name = "Attendance", description = "Endpoints for scanning and querying attendance records.")
class AttendanceController(
    private val attendanceService: AttendanceService,
    private val guestService: GuestService,
    @Autowired(required = false) private val databaseMemberService: DatabaseMemberService?,
    @Autowired(required = false) private val eventDbService: EventDbService?
) {
    private val log = org.slf4j.LoggerFactory.getLogger(AttendanceController::class.java)

    /**
     * Run a DB operation with retries (delays 0, 1s, 3s + jitter). Side effect: [block] may perform DB I/O.
     * @param operation Name for logging
     * @param maxAttempts Retry count
     * @param block DB operation (e.g. eventDbService.getReportData())
     * @return Result of [block]
     * @throws Exception Last exception after retries, or [InterruptedException] if interrupted
     */
    private fun <T> withDbRetry(
        operation: String,
        maxAttempts: Int = 3,
        block: () -> T
    ): T {
        val delays = listOf(0L, 1000L, 3000L)
        var lastError: Exception? = null
        for (attempt in 1..maxAttempts) {
            val delay = delays.getOrElse(attempt - 1) { 3000L } + kotlin.random.Random.nextLong(0, 300)
            if (delay > 0) {
                try {
                    Thread.sleep(delay)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw RuntimeException("Retry interrupted for $operation", ie)
                }
            }
            try {
                return block()
            } catch (e: Exception) {
                lastError = e
                if (attempt < maxAttempts) {
                    log.warn("DB {} failed (attempt {}/{}): {}", operation, attempt, maxAttempts, e.message)
                }
            }
        }
        throw lastError ?: RuntimeException("DB $operation failed after retries")
    }

    /**
     * Record attendance from QR scan. POST /api/attendance/scan. Validates payload (member or guest); no DB write for scan itself.
     * @param request Body: { "qrPayload": "..." } (JSON of MemberQRData or GuestQRData)
     * @return 200 { "message": "..." } | 400 { "message": "Invalid ..." }
     */
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

    /**
     * Get list of members (DB if available, else CSV fallback). GET /api/members. Side effect: DB read or CSV read.
     * @return { "members": [ { "id", "name", "domain", ... } ] }
     */
    @GetMapping("/api/members")
    @Operation(summary = "Get list of members with domain info and standing")
    fun getMembers(): Map<String, List<Map<String, Any>>> {
        val csvFallback: List<Map<String, Any>> = attendanceService.getMembersWithDomain()
            .mapIndexed { idx, m -> m + ("id" to (m["id"] ?: (idx + 1))) }
        return try {
            if (databaseMemberService != null) {
                try {
                    val dbMembers = withDbRetry("getMembers") { databaseMemberService.getAllMembers() }
                    if (dbMembers.isNotEmpty()) mapOf("members" to dbMembers)
                    else mapOf("members" to csvFallback)
                } catch (e: Exception) {
                    log.warn("DB getMembers failed ({}), using CSV fallback", e.message)
                    mapOf("members" to csvFallback)
                }
            } else {
                mapOf("members" to csvFallback)
            }
        } catch (e: Exception) {
            log.error("getMembers failed", e)
            mapOf("members" to csvFallback)
        }
    }

    /**
     * Get list of guests. GET /api/guests. Optional ?eventDate=YYYY-MM-DD: return only guests for that event (onsite support).
     * When eventDate is provided, only guests for that date are returned (DB or CSV filtered). When omitted, returns all guests.
     * Side effect: DB or CSV read.
     * @param eventDate Optional; when set, return only guests for this event date (e.g. latest event for check-in form).
     * @return { "guests": [ { "name", "profession", "referrer", "eventDate" } ] }
     */
    @GetMapping("/api/guests")
    @Operation(summary = "Get list of pre-registered guests with profession info")
    fun getGuests(@RequestParam(required = false) eventDate: String?): Map<String, List<Map<String, String>>> {
        val filterByDate = eventDate?.trim()?.takeIf { it.isNotEmpty() }

        return if (databaseMemberService != null) {
            try {
                val dbGuests = if (filterByDate != null) {
                    withDbRetry("getGuestsForEventDate") { databaseMemberService.getGuestsForEventDate(filterByDate) }
                } else {
                    withDbRetry("getGuests") { databaseMemberService.getAllGuests() }
                }
                if (dbGuests.isNotEmpty()) {
                    mapOf("guests" to dbGuests)
                } else if (filterByDate == null) {
                    mapOf("guests" to guestService.getAllGuestsWithDomain())
                } else {
                    val allCsv = guestService.getAllGuestsWithDomain()
                    val filtered = allCsv.filter { (it["eventDate"] as? String).orEmpty() == filterByDate }
                    mapOf("guests" to filtered)
                }
            } catch (e: Exception) {
                if (filterByDate != null) {
                    val allCsv = guestService.getAllGuestsWithDomain()
                    val filtered = allCsv.filter { (it["eventDate"] as? String).orEmpty() == filterByDate }
                    mapOf("guests" to filtered)
                } else {
                    mapOf("guests" to guestService.getAllGuestsWithDomain())
                }
            }
        } else {
            val allCsv = guestService.getAllGuestsWithDomain()
            val list = if (filterByDate != null) {
                allCsv.filter { (it["eventDate"] as? String).orEmpty() == filterByDate }
            } else allCsv
            mapOf("guests" to list)
        }
    }

    /**
     * Manual check-in. POST /api/checkin. In-memory always; member type also persisted to DB when [EventDbService] present.
     * Side effects: in-memory add; DB write for members; WebSocket broadcast (via service).
     * @param request name, type (member/guest/vip/speaker), currentTime, domain, role, tags, referrer
     * @return 200 { "status", "message" } | 400 duplicate or invalid type
     */
    @PostMapping("/api/checkin")
    @Operation(summary = "Record check-in (in-memory + DB for members)")
    fun checkIn(@RequestBody request: CheckInRequest): ResponseEntity<Map<String, String>> {
        return try {
            val message = attendanceService.recordCheckIn(request)

            // Also persist member check-ins to DB so they appear in /api/report
            if (request.type.equals("member", ignoreCase = true) && eventDbService != null) {
                try {
                    val today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Hong_Kong")).toString()
                    val logReq = AttendanceLogRequest(
                        attendeeId = null,
                        attendeeType = "member",
                        attendeeName = request.name,
                        attendeeProfession = request.domain,
                        eventDate = today,
                        checkedInAt = request.currentTime,
                        status = "on-time"
                    )
                    withDbRetry("checkIn-logAttendance") { eventDbService.logAttendance(logReq) }
                } catch (e: Exception) {
                    log.warn("DB persist for /api/checkin member '{}' failed: {}", request.name, e.message)
                }
            }

            ResponseEntity.ok(mapOf("status" to "success", "message" to message))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("status" to "error", "message" to e.message!!))
        }
    }

    /**
     * Get all check-in records: DB members (from report) + in-memory, merged and deduped by name. GET /api/records.
     * Side effect: DB read when [EventDbService] present.
     * @return { "records": [ CheckInRecord ] } sorted by timestamp desc
     */
    @GetMapping("/api/records")
    @Operation(summary = "Get all records (DB members + in-memory guests merged)")
    fun getRecords(): Map<String, List<CheckInRecord>> {
        val hkt = java.time.ZoneId.of("Asia/Hong_Kong")
        val hktFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

        val normalizedInMemory = attendanceService.getAllRecords().map { r ->
            val hktTimestamp = try {
                java.time.ZonedDateTime.parse(r.timestamp).withZoneSameInstant(hkt).format(hktFmt)
            } catch (_: Exception) {
                try { java.time.Instant.parse(r.timestamp).atZone(hkt).format(hktFmt) }
                catch (_: Exception) { r.timestamp }
            }
            r.copy(timestamp = hktTimestamp, receivedAt = hktTimestamp)
        }

        val dbRecords = try {
            val reportData = eventDbService?.getReportData()
            if (reportData != null) {
                val allMembers = try {
                    databaseMemberService?.getAllMembers() ?: emptyList()
                } catch (_: Exception) { emptyList() }
                val memberDomainMap = allMembers.associate { (it["name"] as String) to (it["domain"] as? String ?: "") }
                val eventDate = reportData.eventDate

                reportData.attendees.filter { it.role == "MEMBER" }.map { att ->
                    val isoTimestamp = if (att.checkInTime != null && !att.checkInTime.contains("T"))
                        "${eventDate}T${att.checkInTime}+08:00" else (att.checkInTime ?: "")
                    CheckInRecord(
                        name = att.memberName,
                        domain = memberDomainMap[att.memberName] ?: "",
                        type = "member",
                        timestamp = isoTimestamp,
                        receivedAt = isoTimestamp,
                        role = "MEMBER"
                    )
                }
            } else emptyList()
        } catch (_: Exception) { emptyList<CheckInRecord>() }

        val inMemoryNames = normalizedInMemory.map { it.name.lowercase() }.toSet()
        val deduped = dbRecords.filter { it.name.lowercase() !in inMemoryNames }

        val merged = (deduped + normalizedInMemory).sortedByDescending { it.timestamp }
        return mapOf("records" to merged)
    }

    /** Clear all in-memory records. DELETE /api/records. Side effect: in-memory clear; WebSocket broadcast. */
    @DeleteMapping("/api/records")
    @Operation(summary = "Clear all records")
    fun clearRecords(): Map<String, String> {
        attendanceService.clearAllRecords()
        return mapOf("status" to "success", "message" to "All records cleared")
    }

    /**
     * Delete one record by index. DELETE /api/records/{index}. Side effect: in-memory remove; WebSocket broadcast.
     * @param index 0-based index in current records list
     * @return 200 | 404 if index out of bounds
     */
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

    /**
     * Create event (DB if available, else in-memory). POST /api/events. All members initialized as absent.
     * Does NOT delete old events; existing events are kept. Latest event is used for attendance, guest list, CSV export.
     * Side effects: DB write or in-memory; WebSocket broadcast. Date YYYY-MM-DD; times HH:mm or HH:mm:ss.
     * @param request name, date, startTime, endTime, registrationStartTime, onTimeCutoff
     * @return 200 { "status", "message", "event" } | 400 invalid format | 500 on DB error
     */
    @PostMapping("/api/events")
    @Operation(summary = "Create event with time settings, initializes all members as absent")
    fun createEvent(@RequestBody request: EventRequest): ResponseEntity<Map<String, Any>> {
        return try {
            val eventData = if (eventDbService != null) {
                try {
                    withDbRetry("createEvent") { eventDbService.createEvent(request) }
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
    
    /**
     * Get report for current event: DB report merged with in-memory check-ins (guests + members not in DB). GET /api/report.
     * Side effect: DB read when [EventDbService] present.
     * @return 200 ReportData | 404 if no current event
     */
    @GetMapping("/api/report")
    @Operation(summary = "Get report data for the current event (DB members + in-memory guests)")
    fun getReportData(): ResponseEntity<ReportData> {
        val reportData = try {
            if (eventDbService != null) withDbRetry("getReportData") { eventDbService.getReportData() } else null
        } catch (e: Exception) {
            log.warn("DB getReportData failed: {}", e.message)
            null
        } ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        // Merge ALL in-memory check-ins into the DB report (guests always, members if not already in DB)
        val allInMemory = attendanceService.getAllRecords()
        if (allInMemory.isEmpty()) {
            return ResponseEntity.ok(reportData)
        }

        val hkt = java.time.ZoneId.of("Asia/Hong_Kong")
        val timeFmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
        val dbAttendeeNames = reportData.attendees.map { it.memberName.lowercase() }.toSet()

        val extraAttendees = allInMemory
            .filter { r -> r.name.lowercase() !in dbAttendeeNames }
            .map { r ->
                val role = when {
                    r.role.uppercase() in listOf("VIP", "SPEAKER") -> r.role.uppercase()
                    r.type.equals("member", ignoreCase = true) -> "MEMBER"
                    else -> "GUEST"
                }
                val timeStr = toHktLocalTime(r.timestamp)?.format(timeFmt) ?: r.timestamp

                val status = try {
                    val cutoff = java.time.LocalTime.parse(reportData.onTimeCutoff)
                    val checkIn = java.time.LocalTime.parse(timeStr)
                    if (checkIn.isBefore(cutoff)) "on-time" else "late"
                } catch (_: Exception) { "on-time" }

                AttendanceRecord(memberName = r.name, status = status, checkInTime = timeStr, role = role)
            }

        if (extraAttendees.isEmpty()) {
            return ResponseEntity.ok(reportData)
        }

        val guestExtra = extraAttendees.filter { it.role != "MEMBER" }
        val memberExtra = extraAttendees.filter { it.role == "MEMBER" }
        val updatedAbsentees = reportData.absentees.filter { ab -> ab.memberName.lowercase() !in memberExtra.map { it.memberName.lowercase() }.toSet() }

        val merged = reportData.copy(
            attendees = reportData.attendees + extraAttendees,
            absentees = updatedAbsentees,
            stats = reportData.stats.copy(
                totalAttendees = reportData.stats.totalAttendees + extraAttendees.size,
                absentCount = updatedAbsentees.size,
                guestCount = guestExtra.count { it.role == "GUEST" },
                vipCount = guestExtra.count { it.role == "VIP" || it.role == "SPEAKER" },
                vipArrivedCount = guestExtra.count { it.role == "VIP" || it.role == "SPEAKER" },
                speakerCount = guestExtra.count { it.role == "SPEAKER" },
                onTimeCount = reportData.stats.onTimeCount + extraAttendees.count { it.status == "on-time" },
                lateCount = reportData.stats.lateCount + extraAttendees.count { it.status == "late" }
            )
        )
        return ResponseEntity.ok(merged)
    }
    
    /** Get current event (DB or in-memory). GET /api/events/current. Side effect: DB read when present. @return 200 | 404 */
    @GetMapping("/api/events/current")
    @Operation(summary = "Get current event (DB only, no attendance data)")
    fun getCurrentEvent(): ResponseEntity<EventData> {
        val event = try {
            if (eventDbService != null) withDbRetry("getCurrentEvent") { eventDbService.getCurrentEvent() } else null
        } catch (e: Exception) {
            log.warn("DB getCurrentEvent failed: {}", e.message)
            null
        }
        return if (event != null) {
            ResponseEntity.ok(event)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }
    
    /** Check if event exists for date. GET /api/events/check?date=YYYY-MM-DD. @return { "exists": true|false } */
    @GetMapping("/api/events/check")
    @Operation(summary = "Check if event exists for a given date")
    fun checkEventExists(@RequestParam date: String): Map<String, Any> {
        val exists = try {
            if (eventDbService != null) withDbRetry("checkEventExists") { eventDbService.hasEventForDate(date) } else null
        } catch (e: Exception) { null } ?: (attendanceService.getCurrentEvent()?.date == date)
        return mapOf("exists" to exists)
    }
    
    /** Check if event exists in current week (Mon–Sun). GET /api/events/check-this-week. @return { "exists": true|false } */
    @GetMapping("/api/events/check-this-week")
    @Operation(summary = "Check if event exists in the current week")
    fun checkEventThisWeek(): Map<String, Any> {
        val exists = try {
            if (eventDbService != null) withDbRetry("checkEventThisWeek") { eventDbService.hasEventThisWeek() } else null
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
    
    /** Get event for date (DB then in-memory). GET /api/events/for-date?date=YYYY-MM-DD. @return 200 { "id", "name" } | 404 */
    @GetMapping("/api/events/for-date")
    @Operation(summary = "Get event details for a specific date")
    fun getEventForDate(@RequestParam date: String): ResponseEntity<Map<String, Any>> {
        val dbEvent = try {
            if (eventDbService != null) withDbRetry("getEventForDate") { eventDbService.getEventForDate(date) } else null
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
    
    /**
     * Log attendance directly. POST /api/attendance/log. Members → DB (or in-memory fallback); guests → in-memory.
     * Side effects: DB write for members when [EventDbService] present; in-memory add for guests/fallback.
     * @return 200 | 409 already checked in | 500 on failure
     */
    @PostMapping("/api/attendance/log")
    @Operation(summary = "Log attendance record directly")
    fun logAttendance(@RequestBody request: AttendanceLogRequest): ResponseEntity<Map<String, String>> {
        val isGuestType = request.attendeeType.lowercase() in listOf("guest", "vip", "speaker")

        // Members → DB (bni_anchor_attendances uses member_id FK)
        if (!isGuestType) {
            val dbError: Exception? = try {
                if (eventDbService != null) withDbRetry("logAttendance") { eventDbService.logAttendance(request) }
                null
            } catch (e: Exception) {
                log.warn("DB logAttendance failed ({}), falling back to in-memory", e.message)
                e
            }
            if (dbError == null) {
                return ResponseEntity.ok(mapOf("status" to "success", "message" to "Attendance logged successfully"))
            }
        }

        // Guests / fallback → in-memory (so they appear in report via getAllRecords)
        return try {
            val normalizedType = if (request.attendeeType.lowercase() in listOf("vip", "speaker")) "guest" else request.attendeeType
            val role = when (request.attendeeType.lowercase()) {
                "vip" -> "VIP"
                "speaker" -> "SPEAKER"
                "guest" -> "GUEST"
                else -> "MEMBER"
            }
            val fallbackRequest = CheckInRequest(
                name = request.attendeeName,
                type = normalizedType,
                currentTime = request.checkedInAt,
                domain = request.attendeeProfession ?: "",
                role = role
            )
            attendanceService.recordCheckIn(fallbackRequest)
            ResponseEntity.ok(mapOf("status" to "success", "message" to "Attendance logged"))
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
    
    /** Clear all events and attendance (DB when present, then in-memory). DELETE /api/events/clear-all. Side effect: DB + memory clear. */
    @DeleteMapping("/api/events/clear-all")
    @Operation(summary = "Clear all events and attendance records")
    fun clearAllEventsAndAttendance(): Map<String, String> {
        try {
            if (eventDbService != null) withDbRetry("clearAllEventsAndAttendance") { eventDbService.clearAllEventsAndAttendance() }
        } catch (e: Exception) {
            log.warn("DB clearAllEventsAndAttendance failed: {}", e.message)
        }
        attendanceService.clearAllEventsAndAttendance()
        return mapOf("status" to "success", "message" to "All events and attendance records cleared")
    }

    /**
     * Export current event report as CSV (UTF-8 BOM). GET /api/export. Members + absent + guests. After export, batch upsert to DB.
     * Side effects: DB read for report/members/guests; optional batch upsert to [EventDbService].
     * @return 200 CSV body, Content-Disposition: attachment; filename=attendance.csv
     */
    @GetMapping("/api/export")
    @Operation(summary = "Export records as CSV with attendance status (current event, all 47 members including absent)")
    fun exportRecords(): ResponseEntity<ByteArray> {
        val out = ByteArrayOutputStream()
        // Add UTF-8 BOM for Excel compatibility
        out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        val writer = PrintWriter(out)
        writer.println("姓名,專業領域,類別,出席狀態,簽到時間")

        // Prefer DB report (event_id, event_date, all bni_anchor_members including absent)
        val reportData = try {
            eventDbService?.getReportData()
        } catch (e: Exception) {
            org.slf4j.LoggerFactory.getLogger(AttendanceController::class.java)
                .warn("DB getReportData failed ({}), using in-memory", e.message)
            null
        } ?: attendanceService.getReportData()

        val records = attendanceService.getAllRecords()

        if (reportData != null) {
            // Members: prefer DB (47 from bni_anchor_members), fallback to CSV
            val membersWithDomain = try {
                databaseMemberService?.getAllMembers()?.map { m ->
                    mapOf("name" to (m["name"] as String), "domain" to (m["domain"] as? String ?: ""))
                } ?: attendanceService.getMembersWithDomain()
            } catch (_: Exception) {
                attendanceService.getMembersWithDomain()
            }
            val memberDomainMap = membersWithDomain.associate { (it["name"] as String) to (it["domain"] as? String ?: "") }

            // Export all members who attended (from reportData.attendees where role=MEMBER)
            for (attendee in reportData.attendees) {
                if (attendee.role != "MEMBER") continue
                val domain = (memberDomainMap[attendee.memberName] ?: "").replace(",", "，")
                val statusText = when (attendee.status) {
                    "on-time" -> "準時"
                    "late" -> "遲到"
                    "late_with_code" -> "遲到(有代碼)"
                    else -> attendee.status
                }
                writer.println("${attendee.memberName},${domain},member,${statusText},${attendee.checkInTime ?: ""}")
            }

            // Export all absent members (HARD RULE: include remaining absent members)
            for (absentee in reportData.absentees) {
                val domain = (memberDomainMap[absentee.memberName] ?: "").replace(",", "，")
                writer.println("${absentee.memberName},${domain},member,缺席,")
            }

            // Export guests with profession (prefer DB bni_anchor_guests, fallback to CSV/in-memory)
            val guestDomainMap = try {
                databaseMemberService?.getAllGuests()
                    ?.associate { g -> (g["name"] ?: "") to (g["profession"] ?: "") }
                    ?.filterKeys { it.isNotBlank() }
                    ?: guestService.getAllGuestsWithDomain().associate {
                        (it["name"] as String) to (it["profession"] as? String ?: "")
                    }
            } catch (_: Exception) {
                guestService.getAllGuestsWithDomain().associate {
                    (it["name"] as String) to (it["profession"] as? String ?: "")
                }
            }
            for (attendee in reportData.attendees) {
                if (attendee.role !in listOf("GUEST", "VIP", "SPEAKER")) continue
                val domain = (guestDomainMap[attendee.memberName] ?: "").replace(",", "，")
                val roleLabel = attendee.role.lowercase()
                val statusText = when (attendee.status) {
                    "on-time" -> "準時"
                    "late" -> "遲到"
                    else -> attendee.status
                }
                writer.println("${attendee.memberName},${domain},${roleLabel},${statusText},${attendee.checkInTime ?: ""}")
            }
            // If DB report has no guest records, fallback to in-memory check-in records
            if (reportData.attendees.none { it.role in listOf("GUEST", "VIP", "SPEAKER") }) {
                for (guest in records.filter { it.type.equals("guest", ignoreCase = true) }) {
                    val domain = guest.domain.replace(",", "，")
                    val roleLabel = guest.role.lowercase().ifEmpty { "guest" }
                    val guestStatus = determineGuestStatus(guest.timestamp, reportData.onTimeCutoff)
                    val hktTime = toHktLocalTime(guest.timestamp)?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")) ?: guest.timestamp
                    writer.println("${guest.name},${domain},${roleLabel},${guestStatus},${hktTime}")
                }
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

        // After successful CSV export: take batch records (reportData + records),
        // insert/upsert into bni_anchor_attendances via EventDbService (AttendanceRepository).
        try {
            if (eventDbService != null) {
                withDbRetry("batchUpsertCurrentEventAttendancesForExport") {
                    eventDbService.batchUpsertCurrentEventAttendancesForExport(reportData, records)
                }
            }
        } catch (e: Exception) {
            log.warn("Post-export attendance batch upsert failed: {}", e.message)
        }

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=attendance.csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(out.toByteArray())
    }
    
    /** Parse timestamp to HKT LocalTime (ZonedDateTime, Instant, or HH:mm:ss regex). No side effects. */
    private fun toHktLocalTime(timestamp: String): java.time.LocalTime? {
        val hkt = java.time.ZoneId.of("Asia/Hong_Kong")
        return try {
            java.time.ZonedDateTime.parse(timestamp).withZoneSameInstant(hkt).toLocalTime()
        } catch (_: Exception) {
            try {
                java.time.Instant.parse(timestamp).atZone(hkt).toLocalTime()
            } catch (_: Exception) {
                val m = Regex("(\\d{2}:\\d{2}:\\d{2})").find(timestamp)
                if (m != null) java.time.LocalTime.parse(m.groupValues[1]) else null
            }
        }
    }

    /** Classify guest status as 準時 or 遲到 from timestamp and onTimeCutoff. Returns "已簽到" on parse error. */
    private fun determineGuestStatus(timestamp: String, onTimeCutoff: String): String {
        return try {
            val cutoffTime = java.time.LocalTime.parse(onTimeCutoff)
            val checkInTime = toHktLocalTime(timestamp) ?: return "已簽到"
            if (checkInTime.isBefore(cutoffTime)) "準時" else "遲到"
        } catch (e: Exception) {
            "已簽到"
        }
    }

    /** Search member attendance history by name (case-insensitive partial). GET /api/attendance/member?name= */
    @GetMapping("/api/attendance/member")
    @Operation(summary = "Fetch attendance history for a specific member.")
    fun searchMemberAttendance(@RequestParam name: String): List<MemberAttendance> {
        return attendanceService.searchMemberAttendance(name)
    }

    /** Get attendance roster for event date. GET /api/attendance/event?date=YYYY-MM-DD. In-memory only. */
    @GetMapping("/api/attendance/event")
    @Operation(summary = "Get attendance roster for a given event date.")
    fun searchEventAttendance(@RequestParam date: String): List<EventAttendance> {
        return attendanceService.searchEventAttendance(date)
    }
    
    // ===== AI Insights Endpoints (Phase 2 - For Future AI Integration) =====
    
    /** Generate AI insights for event. POST /api/insights/generate. Uses [AttendanceService] + DeepSeek for retention. Side effect: AI API call; cache write. */
    @PostMapping("/api/insights/generate")
    @Operation(summary = "Generate AI insights report for an event (stub for future AI integration)")
    fun generateAIInsights(@RequestBody request: AIInsightRequest): ResponseEntity<AIInsightResponse> {
        val insights = attendanceService.generateInsights(request)
        return ResponseEntity.ok(insights)
    }
    
    /** Get cached AI insights for event. GET /api/insights/{eventId}. No side effects. */
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
    
    /** Export event data for AI (attendance records + summary). GET /api/insights/data-export/{eventId}. @return 200 | 404 */
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
