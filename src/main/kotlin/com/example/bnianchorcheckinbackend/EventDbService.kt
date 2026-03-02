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
        val allMemberNames = databaseMemberService.getAllMembers().map { it["name"] as String }.toSet()
        for (memberName in allMemberNames) {
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

    private fun normalizeStatus(status: String?): String {
        val s = (status ?: "").trim()
        return when (s) {
            "on-time", "late", "absent", "late_with_code" -> s
            "準時" -> "on-time"
            "遲到" -> "late"
            "缺席" -> "absent"
            "遲到(有代碼)" -> "late_with_code"
            else -> "absent"
        }
    }

    private fun parseCheckInTime(value: String?): LocalTime? {
        if (value.isNullOrBlank()) return null
        val v = value.trim()
        return try {
            when {
                v.contains("T") -> Instant.parse(v).atZone(hkt).toLocalTime()
                Regex("^\\d{2}:\\d{2}:\\d{2}$").matches(v) -> LocalTime.parse(v, DateTimeFormatter.ofPattern("HH:mm:ss"))
                Regex("^\\d{2}:\\d{2}$").matches(v) -> LocalTime.parse(v, DateTimeFormatter.ofPattern("HH:mm"))
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

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
    fun logAttendance(request: com.example.bnianchorcheckinbackend.AttendanceLogRequest) {
        val eventDate = try {
            LocalDate.parse(request.eventDate)
        } catch (e: Exception) {
            return
        }
        val event = eventRepository.findByEventDate(eventDate) ?: return
        val eventId = event.id!!.toInt()
        ensureAbsentRowsForAllMembers(eventId, eventDate)
        val role = when (request.attendeeType.lowercase()) {
            "guest" -> "GUEST"
            "vip" -> "VIP"
            "speaker" -> "SPEAKER"
            else -> "MEMBER"
        }
        val existing = attendanceRepository.findByEventIdAndMemberName(eventId, request.attendeeName)
        val checkInTime = parseCheckInTime(request.checkedInAt)
        if (existing != null) {
            existing.status = normalizeStatus(request.status)
            if (checkInTime != null) existing.checkInTime = checkInTime
            existing.role = role
            attendanceRepository.save(existing)
        } else {
            attendanceRepository.save(
                Attendance(
                    eventId = eventId,
                    eventDate = eventDate,
                    memberName = request.attendeeName,
                    status = normalizeStatus(request.status),
                    checkInTime = checkInTime,
                    role = role
                )
            )
        }
    }
 
    @Transactional
    fun clearAllEventsAndAttendance() {
        attendanceLogRepository.deleteAll()
        attendanceRepository.deleteAll()
        eventRepository.deleteAll()
    }

    /**
     * Called by /api/export: ensure current event attendance rows are persisted in DB in batch.
     * This guarantees the export step also upserts records into bni_anchor_attendances.
     */
    @Transactional
    fun batchUpsertCurrentEventAttendancesForExport(
        reportData: ReportData?,
        records: List<CheckInRecord>
    ) {
        val event = eventRepository.findTopByOrderByEventDateDesc() ?: return
        val eventId = event.id!!.toInt()
        val eventDate = event.eventDate

        // Always make sure all members exist as absent baseline rows.
        ensureAbsentRowsForAllMembers(eventId, eventDate)
        
        val byName = attendanceRepository.findByEventId(eventId).associateBy { it.memberName }.toMutableMap()

        fun upsert(name: String, status: String, checkInTime: LocalTime?, role: String) {
            val existing = byName[name]
            if (existing != null) {
                existing.status = normalizeStatus(status)
                if (checkInTime != null) {
                    existing.checkInTime = checkInTime
                }
                existing.role = role
            } else {
                byName[name] = Attendance(
                    eventId = eventId,
                    eventDate = eventDate,
                    memberName = name,
                    status = normalizeStatus(status),
                    checkInTime = checkInTime,
                    role = role
                )
            }
        }

        // Upsert from report data first (members + guests + absentees)
        if (reportData != null) {
            for (a in reportData.attendees) {
                val role = (a.role ?: "MEMBER").uppercase()
                upsert(a.memberName, a.status, parseCheckInTime(a.checkInTime), role)
            }
            for (a in reportData.absentees) {
                upsert(a.memberName, "absent", null, "MEMBER")
            }
        }

        // Upsert from in-memory records as fallback/additional source (especially guest rows)
        for (r in records) {
            val role = if (r.type.equals("guest", ignoreCase = true)) "GUEST" else "MEMBER"
            val status = if (role == "GUEST") "on-time" else "on-time"
            upsert(r.name, status, parseCheckInTime(r.timestamp), role)
        }

        attendanceRepository.saveAll(byName.values.toList())
    }
}
