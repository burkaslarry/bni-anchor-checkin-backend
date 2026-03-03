package com.example.bnianchorcheckinbackend

import com.example.bnianchorcheckinbackend.entities.Attendance
import com.example.bnianchorcheckinbackend.entities.Event
import com.example.bnianchorcheckinbackend.repositories.AttendanceRepository
import com.example.bnianchorcheckinbackend.repositories.EventRepository
import com.example.bnianchorcheckinbackend.repositories.MemberRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.*
import java.time.format.DateTimeFormatter

@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class EventDbService(
    private val eventRepository: EventRepository,
    private val attendanceRepository: AttendanceRepository,
    private val memberRepository: MemberRepository,
    private val databaseMemberService: DatabaseMemberService
) {
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

    private fun parseCheckInTimeToOffset(value: String?): OffsetDateTime? {
        if (value.isNullOrBlank()) return null
        val v = value.trim()
        return try {
            when {
                v.contains("T") -> OffsetDateTime.parse(v).withOffsetSameInstant(hkt.rules.getOffset(Instant.now()))
                    ?: Instant.parse(v).atZone(hkt).toOffsetDateTime()
                Regex("^\\d{2}:\\d{2}:\\d{2}$").matches(v) -> {
                    val lt = LocalTime.parse(v, DateTimeFormatter.ofPattern("HH:mm:ss"))
                    OffsetDateTime.of(LocalDate.now(hkt), lt, hkt.rules.getOffset(Instant.now()))
                }
                Regex("^\\d{2}:\\d{2}$").matches(v) -> {
                    val lt = LocalTime.parse(v, DateTimeFormatter.ofPattern("HH:mm"))
                    OffsetDateTime.of(LocalDate.now(hkt), lt, hkt.rules.getOffset(Instant.now()))
                }
                else -> null
            }
        } catch (_: Exception) {
            try {
                Instant.parse(v).atZone(hkt).toOffsetDateTime()
            } catch (_: Exception) {
                null
            }
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

    private fun resolveMemberId(name: String): Int? {
        return memberRepository.findByNameIgnoreCase(name).orElse(null)?.id?.toInt()
    }

    @Transactional
    fun createEvent(request: EventRequest): EventData {
        val eventDate = LocalDate.parse(request.date)
        val startTime = parseTime(request.startTime)
        val endTime = parseTime(request.endTime)
        val regStartTime = parseTime(request.registrationStartTime)
        val onTimeCutoff = parseTime(request.onTimeCutoff)

        val event = Event(
            name = request.name,
            createDate = LocalDate.now(hkt),
            eventDate = eventDate,
            startTime = startTime,
            endTime = endTime,
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
            registrationStartTime = saved.registrationStartTime.format(DateTimeFormatter.ofPattern("HH:mm")),
            onTimeCutoff = saved.onTimeCutoffTime.format(DateTimeFormatter.ofPattern("HH:mm")),
            createdAt = ZonedDateTime.now(hkt).toInstant().toString()
        )
    }

    fun getReportData(): ReportData? {
        val event = eventRepository.findTopByOrderByEventDateDesc() ?: return null
        val eventDateStr = event.eventDate.toString()
        val eventId = event.id!!.toInt()

        val allMembers = databaseMemberService.getAllMembers()
        val memberIdToName = allMembers.associate { (it["id"] as Int) to (it["name"] as String) }
        val memberNameToId = allMembers.associate { (it["name"] as String) to (it["id"] as Int) }
        val attendances = attendanceRepository.findByEventId(eventId)

        val checkedInMemberIds = attendances.map { it.memberId }.toSet()
        val attendees = attendances
            .map { att ->
                val memberName = memberIdToName[att.memberId] ?: "Unknown (ID=${att.memberId})"
                AttendanceRecord(
                    memberName = memberName,
                    status = att.status,
                    checkInTime = att.checkInTime.atZoneSameInstant(hkt).toLocalTime()
                        .format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                    role = "MEMBER"
                )
            }
            .sortedByDescending { it.checkInTime ?: "" }

        val absentees = allMembers
            .filter { (it["id"] as Int) !in checkedInMemberIds }
            .map { AttendanceRecord(memberName = it["name"] as String, status = "absent", role = "MEMBER") }
            .sortedBy { it.memberName }

        val stats = ReportStats(
            totalAttendees = attendees.size,
            onTimeCount = attendees.count { it.status == "on-time" },
            lateCount = attendees.count { it.status == "late" },
            absentCount = absentees.size,
            guestCount = 0,
            vipCount = 0,
            vipArrivedCount = 0,
            speakerCount = 0
        )

        return ReportData(
            eventId = eventId,
            eventName = event.name,
            eventDate = eventDateStr,
            onTimeCutoff = event.onTimeCutoffTime.format(DateTimeFormatter.ofPattern("HH:mm")),
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
            registrationStartTime = event.registrationStartTime.format(DateTimeFormatter.ofPattern("HH:mm")),
            onTimeCutoff = event.onTimeCutoffTime.format(DateTimeFormatter.ofPattern("HH:mm")),
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
        } catch (_: Exception) {
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
                registrationStartTime = event.registrationStartTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                onTimeCutoff = event.onTimeCutoffTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                createdAt = event.createDate.toString()
            )
        } catch (_: Exception) {
            null
        }
    }

    @Transactional
    fun logAttendance(request: AttendanceLogRequest) {
        val eventDate = try { LocalDate.parse(request.eventDate) } catch (_: Exception) { return }
        val event = eventRepository.findByEventDate(eventDate) ?: return
        val eventId = event.id!!.toInt()

        val memberId = resolveMemberId(request.attendeeName) ?: return
        val checkInTime = parseCheckInTimeToOffset(request.checkedInAt)
            ?: OffsetDateTime.now(hkt)

        val existing = attendanceRepository.findByEventIdAndMemberId(eventId, memberId)
        if (existing != null) {
            existing.status = normalizeStatus(request.status)
            existing.checkInTime = checkInTime
            attendanceRepository.save(existing)
        } else {
            attendanceRepository.save(
                Attendance(
                    memberId = memberId,
                    eventId = eventId,
                    checkInTime = checkInTime,
                    status = normalizeStatus(request.status)
                )
            )
        }
    }

    @Transactional
    fun clearAllEventsAndAttendance() {
        attendanceRepository.deleteAll()
        eventRepository.deleteAll()
    }

    @Transactional
    fun batchUpsertCurrentEventAttendancesForExport(
        reportData: ReportData?,
        records: List<CheckInRecord>
    ) {
        val event = eventRepository.findTopByOrderByEventDateDesc() ?: return
        val eventId = event.id!!.toInt()

        val allMembers = databaseMemberService.getAllMembers()
        val memberNameToId = allMembers.associate { (it["name"] as String) to (it["id"] as Int) }
        val byMemberId = attendanceRepository.findByEventId(eventId).associateBy { it.memberId }.toMutableMap()

        fun upsert(name: String, status: String, checkInTime: OffsetDateTime?) {
            val memberId = memberNameToId[name] ?: return
            val ts = checkInTime ?: OffsetDateTime.now(hkt)
            val existing = byMemberId[memberId]
            if (existing != null) {
                existing.status = normalizeStatus(status)
                existing.checkInTime = ts
            } else {
                byMemberId[memberId] = Attendance(
                    memberId = memberId,
                    eventId = eventId,
                    checkInTime = ts,
                    status = normalizeStatus(status)
                )
            }
        }

        if (reportData != null) {
            for (a in reportData.attendees) {
                upsert(a.memberName, a.status, parseCheckInTimeToOffset(a.checkInTime))
            }
        }

        for (r in records) {
            if (r.type.equals("guest", ignoreCase = true)) continue
            upsert(r.name, "on-time", parseCheckInTimeToOffset(r.timestamp))
        }

        attendanceRepository.saveAll(byMemberId.values.toList())
    }
}
