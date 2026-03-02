package com.example.bnianchorcheckinbackend

import com.example.bnianchorcheckinbackend.entities.Attendance
import com.example.bnianchorcheckinbackend.entities.Event
import com.example.bnianchorcheckinbackend.repositories.AttendanceLogRepository
import com.example.bnianchorcheckinbackend.repositories.AttendanceRepository
import com.example.bnianchorcheckinbackend.repositories.EventRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class EventDbService(
    private val eventRepository: EventRepository,
    private val attendanceLogRepository: AttendanceLogRepository,
    private val attendanceRepository: AttendanceRepository,
    private val databaseMemberService: DatabaseMemberService
) {
    @Transactional
    private fun ensureAbsentRowsForAllMembers(eventId: Int, eventDate: LocalDate) {
        val existingNames = attendanceRepository.findByEventId(eventId).map { it.memberName }.toSet()
        val allMemberNames = databaseMemberService.getAllMembers().map { it["name"] as String }.toSet()
        for (memberName in allMemberNames) {
            if (memberName in existingNames) continue
            attendanceRepository.save(
                Attendance(
                    eventId = eventId,
                    eventDate = eventDate,
                    memberName = memberName,
                    status = "absent",
                    checkInTime = null,
                    role = "MEMBER"
                )
            )
        }
    }

    //Custom: remember to change default time zone
    private val hkt = ZoneId.of("Asia/Hong_Kong")

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

        val nowIso = ZonedDateTime.now(hkt).toInstant().toString()
        val event = Event(
            name = request.name,
            createDate = LocalDate.now(hkt),
            eventDate = eventDate,
            startTime = startTime,
            endTime = endTime,
            createdAt = nowIso,
            registrationStartTime = regStartTime,
            onTimeCutoffTime = onTimeCutoff,
            lateCutoffTime = null
        )
        val saved = eventRepository.save(event)
        val eventId = saved.id!!.toInt()
        val eventDateLocal = saved.eventDate

        // Insert all members as absent in bni_anchor_attendances
        ensureAbsentRowsForAllMembers(eventId, eventDateLocal)

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
        val eventId = event.id!!.toInt()

        // Backfill any missing absent rows for older events
        ensureAbsentRowsForAllMembers(eventId, event.eventDate)

        val members = databaseMemberService.getAllMembers().map { it["name"] as String }
        val attendances = attendanceRepository.findByEventId(eventId)

        val checkedInNames = attendances.filter { it.checkInTime != null }.map { it.memberName }.toSet()
        val attendees = attendances
            .filter { it.checkInTime != null }
            .map { att ->
                AttendanceRecord(
                    memberName = att.memberName,
                    status = att.status,
                    checkInTime = att.checkInTime?.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                    role = att.role ?: "MEMBER"
                )
            }
            .sortedByDescending { it.checkInTime ?: "" }

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
            createdAt = ZonedDateTime.now(hkt).toString()
        )
    }

    fun hasEventThisWeek(): Boolean {
        val now = LocalDate.now(hkt)
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
        val eventDate = LocalDate.parse(request.eventDate)
        val event = eventRepository.findByEventDate(eventDate)
            ?: throw IllegalArgumentException("找不到活動日期 ${request.eventDate} 的活動")
        val eventId = event.id!!.toInt()

        val role = if (request.attendeeType.equals("member", ignoreCase = true)) "MEMBER" else "GUEST"
        val status = when (request.status) {
            "on-time", "late", "absent", "late_with_code" -> request.status
            else -> "on-time"
        }
        val checkInTime = try {
            Instant.parse(request.checkedInAt).atZone(hkt).toLocalTime()
        } catch (_: Exception) {
            LocalTime.now(hkt)
        }

        val existing = attendanceRepository.findByEventIdAndMemberName(eventId, request.attendeeName)
        if (existing != null) {
            if (existing.checkInTime != null) {
                throw IllegalArgumentException("${request.attendeeName} 已經簽到過了")
            }
            existing.status = status
            existing.checkInTime = checkInTime
            existing.role = role
            attendanceRepository.save(existing)
        } else {
            val att = Attendance(
                eventId = eventId,
                eventDate = eventDate,
                memberName = request.attendeeName,
                status = status,
                checkInTime = checkInTime,
                role = role
            )
            attendanceRepository.save(att)
        }

        // Also write to legacy attendance_logs for backward compatibility
        val attendanceLog = com.example.bnianchorcheckinbackend.entities.AttendanceLog(
            attendeeId = request.attendeeId ?: 0,
            attendeeType = request.attendeeType,
            attendeeName = request.attendeeName,
            eventDate = request.eventDate,
            checkedInAt = Instant.parse(request.checkedInAt),
            status = status
        )
        try {
            attendanceLogRepository.save(attendanceLog)
        } catch (_: Exception) { /* ignore duplicate in legacy table */ }
    }

    @Transactional
    fun clearAllEventsAndAttendance() {
        attendanceLogRepository.deleteAll()
        attendanceRepository.deleteAll()
        eventRepository.deleteAll()
    }
}
