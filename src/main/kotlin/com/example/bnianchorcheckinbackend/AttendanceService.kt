package com.example.bnianchorcheckinbackend

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@Service
class AttendanceService(
    private val csvService: CsvService,
    private val objectMapper: ObjectMapper,
    private val webSocketHandler: AttendanceWebSocketHandler
) {

    private val attendanceRecords = ConcurrentHashMap<String, MutableList<EventAttendance>>()
    private val memberAttendanceRecords = ConcurrentHashMap<String, MutableList<MemberAttendance>>()
    
    private val allRecords = CopyOnWriteArrayList<CheckInRecord>()
    private val events = CopyOnWriteArrayList<EventData>()
    private val eventIdCounter = AtomicInteger(1)
    
    // Event attendance records: eventId -> (memberName -> AttendanceRecord)
    private val eventAttendanceMap = ConcurrentHashMap<Int, ConcurrentHashMap<String, AttendanceRecord>>()

    fun recordAttendance(qrPayload: String): String {
        val attendanceData = try {
            objectMapper.readValue<AttendanceQRData>(qrPayload)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid QR Payload: ${e.message}")
        }

        val name: String
        val membershipId: String?
        val type: String

        when (attendanceData) {
            is MemberQRData -> {
                val memberData = csvService.getMemberByName(attendanceData.name)
                if (memberData == null || memberData.membershipId != attendanceData.membershipId) {
                    throw IllegalArgumentException("Invalid member or membership ID.")
                }
                name = attendanceData.name
                membershipId = attendanceData.membershipId
                type = attendanceData.type
            }
            is GuestQRData -> {
                val referrerData = csvService.getMemberByName(attendanceData.referrer)
                if (referrerData == null) {
                    throw IllegalArgumentException("Invalid referrer for guest.")
                }
                name = attendanceData.name
                membershipId = null
                type = attendanceData.type
            }
        }

        val eventDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val status = "Present"
        val eventName = "BNI Anchor Meeting"

        attendanceRecords.computeIfAbsent(eventDate) { mutableListOf() }.add(
            EventAttendance(memberName = name, membershipId = membershipId, status = status)
        )

        memberAttendanceRecords.computeIfAbsent(name.lowercase()) { mutableListOf() }.add(
            MemberAttendance(eventName = eventName, eventDate = eventDate, status = status)
        )

        return "Attendance recorded successfully for $name (${type.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }})."
    }

    fun recordCheckIn(request: CheckInRequest): String {
        if (request.type.lowercase() !in listOf("guest", "member")) {
            throw IllegalArgumentException("Invalid user type")
        }

        // Check for duplicate entry (same name and type)
        val isDuplicate = allRecords.any { 
            it.name.equals(request.name, ignoreCase = true) && 
            it.type.equals(request.type, ignoreCase = true)
        }
        
        if (isDuplicate) {
            throw IllegalArgumentException("${request.name} 已經簽到過了 (Already checked in)")
        }

        // Lookup domain logic
        var domain = request.domain
        if (domain.isEmpty() && request.type.equals("member", ignoreCase = true)) {
            val memberData = csvService.getMemberByName(request.name)
            if (memberData != null) {
                domain = memberData.domain
            }
        }

        val now = LocalDateTime.now()
        val record = CheckInRecord(
            name = request.name,
            type = request.type.lowercase(),
            domain = domain,
            timestamp = request.currentTime,
            receivedAt = now.toString()
        )

        allRecords.add(record)
        
        // Update attendance for report page (only for members)
        if (request.type.equals("member", ignoreCase = true)) {
            updateAttendance(request.name, now)
        }

        webSocketHandler.broadcast(mapOf(
            "type" to "new_checkin",
            "data" to record
        ))

        return "Check-in successful"
    }

    fun getAllRecords(): List<CheckInRecord> {
        return allRecords
    }

    fun clearAllRecords() {
        allRecords.clear()
        webSocketHandler.broadcast(mapOf(
            "type" to "records_cleared"
        ))
    }

    fun deleteRecord(index: Int) {
        if (index < 0 || index >= allRecords.size) {
            throw IndexOutOfBoundsException("Invalid record index")
        }
        val removed = allRecords.removeAt(index)
        webSocketHandler.broadcast(mapOf(
            "type" to "record_deleted",
            "data" to removed
        ))
    }

    fun getMembers(): List<String> {
        return csvService.getAllMembers()
    }

    fun getMembersWithDomain(): List<Map<String, String>> {
        return csvService.getAllMembersWithDomain()
    }
    
    fun createEvent(event: EventRequest): EventData {
        val eventId = eventIdCounter.getAndIncrement()
        val eventData = EventData(
            id = eventId,
            name = event.name,
            date = event.date,
            startTime = event.startTime,
            endTime = event.endTime,
            registrationStartTime = event.registrationStartTime,
            onTimeCutoff = event.onTimeCutoff,
            createdAt = LocalDateTime.now().toString()
        )
        events.add(eventData)
        
        // Initialize all members as absent for this event
        val members = csvService.getAllMembers()
        val attendanceMap = ConcurrentHashMap<String, AttendanceRecord>()
        members.forEach { memberName ->
            attendanceMap[memberName] = AttendanceRecord(
                memberName = memberName,
                status = "absent",
                checkInTime = null
            )
        }
        eventAttendanceMap[eventId] = attendanceMap
        
        // Broadcast event creation
        webSocketHandler.broadcast(mapOf(
            "type" to "event_created",
            "data" to eventData
        ))
        
        return eventData
    }
    
    fun getCurrentEvent(): EventData? {
        return events.lastOrNull()
    }
    
    fun getReportData(): ReportData? {
        val currentEvent = getCurrentEvent() ?: return null
        val attendanceMap = eventAttendanceMap[currentEvent.id] ?: return null
        
        val attendees = attendanceMap.values
            .filter { it.status == "on-time" || it.status == "late" }
            .sortedByDescending { it.checkInTime }
            
        val absentees = attendanceMap.values
            .filter { it.status == "absent" }
            .sortedBy { it.memberName }
            
        return ReportData(
            eventId = currentEvent.id,
            eventName = currentEvent.name,
            eventDate = currentEvent.date,
            onTimeCutoff = currentEvent.onTimeCutoff,
            attendees = attendees,
            absentees = absentees
        )
    }
    
    fun updateAttendance(memberName: String, checkInTime: LocalDateTime): AttendanceRecord? {
        val currentEvent = getCurrentEvent() ?: return null
        val attendanceMap = eventAttendanceMap[currentEvent.id] ?: return null
        
        // Determine status based on on-time cutoff
        val onTimeCutoff = LocalTime.parse(currentEvent.onTimeCutoff)
        val checkInLocalTime = checkInTime.toLocalTime()
        val status = if (checkInLocalTime.isBefore(onTimeCutoff)) "on-time" else "late"
        
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        val record = AttendanceRecord(
            memberName = memberName,
            status = status,
            checkInTime = checkInTime.toLocalTime().format(timeFormatter)
        )
        
        attendanceMap[memberName] = record
        
        // Broadcast attendance update for report page
        webSocketHandler.broadcast(mapOf(
            "type" to "attendance_updated",
            "data" to record
        ))
        
        return record
    }

    fun searchMemberAttendance(name: String): List<MemberAttendance> {
        // Case-insensitive partial match search
        val searchTerm = name.lowercase()
        return memberAttendanceRecords.entries
            .filter { it.key.contains(searchTerm) }
            .flatMap { it.value }
    }

    fun searchEventAttendance(date: String): List<EventAttendance> {
        return attendanceRecords[date] ?: emptyList()
    }
    
    fun clearAllEventsAndAttendance() {
        events.clear()
        eventAttendanceMap.clear()
        allRecords.clear()
        attendanceRecords.clear()
        memberAttendanceRecords.clear()
        
        // Broadcast clear event
        webSocketHandler.broadcast(mapOf(
            "type" to "all_cleared"
        ))
    }
}
