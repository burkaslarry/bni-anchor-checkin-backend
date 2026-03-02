package com.example.bnianchorcheckinbackend

import com.example.bnianchorcheckinbackend.entities.Event
import com.example.bnianchorcheckinbackend.repositories.AttendanceLogRepository
import com.example.bnianchorcheckinbackend.repositories.EventRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class EventDbService(
    private val eventRepository: EventRepository,
    private val attendanceLogRepository: AttendanceLogRepository,
    private val databaseMemberService: DatabaseMemberService
) {

    private fun parseTime(s: String): LocalTime {
        val trimmed = s.trim()
        if (trimmed.isEmpty()) return LocalTime.of(9, 0)
        return when {
            trimmed.matches(Regex("^\\d{1,2}:\\d{2}$")) -> {
                val parts = trimmed.split(":")
                LocalTime.of(parts[0].toInt(), parts[1].toInt())
            }
            trimmed.matches(Regex("^\\d{1,2}:\\d{2}:\\d{2}$")) -> {
                val parts = trimmed.split(":")
                LocalTime.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
            }
            else -> LocalTime.parse(trimmed)
        }
    }

    @Transactional
    fun createEvent(request: EventRequest): EventData {
        val eventDate = LocalDate.parse(request.date)
        val startTime = parseTime(request.startTime)
        val endTime = parseTime(request.endTime)
        val regStartTime = parseTime(request.registrationStartTime)
        val onTimeCutoff = parseTime(request.onTimeCutoff)

        val nowIso = java.time.Instant.now().toString()
        val event = Event(
            name = request.name,
            createDate = LocalDate.now(),
            eventDate = eventDate,
            startTime = startTime,
            endTime = endTime,
            createdAt = nowIso,
            registrationStartTime = regStartTime,
            onTimeCutoffTime = onTimeCutoff,
            lateCutoffTime = null
        )
        val saved = eventRepository.save(event)

        return EventData(
            id = saved.id!!.toInt(),
            name = saved.name,
            date = saved.eventDate.toString(),
            startTime = saved.startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
            endTime = saved.endTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "09:00",
            registrationStartTime = saved.registrationStartTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: request.registrationStartTime,
            onTimeCutoff = saved.onTimeCutoffTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: request.onTimeCutoff,
            createdAt = saved.createdAt ?: nowIso
        )
    }

    fun getReportData(): ReportData? {
        val event = eventRepository.findTopByOrderByEventDateDesc() ?: return null
        val eventDateStr = event.eventDate.toString()

        val members = databaseMemberService.getAllMembers().map { it["name"] as String }
        val logs = attendanceLogRepository.findByEventDate(eventDateStr)

        val checkedInNames = logs.map { it.attendeeName }.toSet()
        val attendees = logs.map { log ->
            AttendanceRecord(
                memberName = log.attendeeName,
                status = log.status,
                checkInTime = java.time.Instant.ofEpochMilli(log.checkedInAt.toEpochMilli())
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalTime()
                    .format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                role = if (log.attendeeType == "member") "MEMBER" else "GUEST"
            )
        }.sortedByDescending { it.checkInTime }

        val absentees = members
            .filter { it !in checkedInNames }
            .map { AttendanceRecord(memberName = it, status = "absent", role = "MEMBER") }
            .sortedBy { it.memberName }

        val stats = ReportStats(
            totalAttendees = attendees.size,
            onTimeCount = attendees.count { it.status == "on-time" },
            lateCount = attendees.count { it.status == "late" },
            absentCount = absentees.size,
            guestCount = attendees.count { it.role == "GUEST" },
            vipCount = 0,
            vipArrivedCount = 0,
            speakerCount = 0
        )

        return ReportData(
            eventId = event.id!!.toInt(),
            eventName = event.name,
            eventDate = eventDateStr,
            onTimeCutoff = event.onTimeCutoffTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "07:05",
            attendees = attendees,
            absentees = absentees,
            stats = stats
        )
    }

    fun getCurrentEvent(): EventData? {
        val event = eventRepository.findTopByOrderByEventDateDesc() ?: return null
        return EventData(
            id = event.id!!.toInt(),
            name = event.name,
            date = event.eventDate.toString(),
            startTime = event.startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
            endTime = event.endTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "09:00",
            registrationStartTime = event.registrationStartTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "06:30",
            onTimeCutoff = event.onTimeCutoffTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "07:05",
            createdAt = java.time.LocalDateTime.now().toString()
        )
    }

    fun hasEventThisWeek(): Boolean {
        val now = LocalDate.now()
        val startOfWeek = now.minusDays(now.dayOfWeek.value.toLong() - 1)
        val endOfWeek = startOfWeek.plusDays(6)
        return eventRepository.existsByEventDateBetween(startOfWeek, endOfWeek)
    }

    fun hasEventForDate(eventDate: String): Boolean {
        return try {
            val date = LocalDate.parse(eventDate)
            eventRepository.findByEventDate(date) != null
        } catch (e: Exception) {
            false
        }
    }
    
    fun getEventForDate(eventDate: String): EventData? {
        return try {
            val date = LocalDate.parse(eventDate)
            val event = eventRepository.findByEventDate(date) ?: return null
            EventData(
                id = event.id!!.toInt(),
                name = event.name,
                date = event.eventDate.toString(),
                startTime = event.startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                endTime = event.endTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "09:00",
                registrationStartTime = event.registrationStartTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "06:30",
                onTimeCutoff = event.onTimeCutoffTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "07:05",
                createdAt = event.createDate.toString()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    @Transactional
    fun logAttendance(request: AttendanceLogRequest) {
        // Check for duplicate check-in
        val existing = attendanceLogRepository.findByEventDate(request.eventDate)
            .find { it.attendeeId == request.attendeeId && it.attendeeType == request.attendeeType }
        
        if (existing != null) {
            throw IllegalArgumentException("${request.attendeeName} 已經簽到過了")
        }
        
        val attendanceLog = com.example.bnianchorcheckinbackend.entities.AttendanceLog(
            attendeeId = request.attendeeId ?: 0,
            attendeeType = request.attendeeType,
            attendeeName = request.attendeeName,
            eventDate = request.eventDate,
            checkedInAt = java.time.Instant.parse(request.checkedInAt),
            status = request.status
        )
        attendanceLogRepository.save(attendanceLog)
    }

    @Transactional
    fun clearAllEventsAndAttendance() {
        attendanceLogRepository.deleteAll()
        eventRepository.deleteAll()
    }
}
