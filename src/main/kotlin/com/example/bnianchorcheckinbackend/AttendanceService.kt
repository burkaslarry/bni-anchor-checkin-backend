package com.example.bnianchorcheckinbackend

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

@Service
class AttendanceService(
    private val csvService: CsvService,
    private val objectMapper: ObjectMapper
) {

    private val attendanceRecords = ConcurrentHashMap<String, MutableList<EventAttendance>>()
    private val memberAttendanceRecords = ConcurrentHashMap<String, MutableList<MemberAttendance>>()

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
                membershipId = null // Guests don't have membership IDs
                type = attendanceData.type
            }
        }

        val eventDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val status = "Present"
        val eventName = "BNI Anchor Meeting"

        // Record for event attendance
        attendanceRecords.computeIfAbsent(eventDate) { mutableListOf() }.add(
            EventAttendance(memberName = name, membershipId = membershipId, status = status)
        )

        // Record for member attendance
        memberAttendanceRecords.computeIfAbsent(name.lowercase()) { mutableListOf() }.add(
            MemberAttendance(eventName = eventName, eventDate = eventDate, status = status)
        )

        return "Attendance recorded successfully for $name (${type.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }})."
    }

    fun searchMemberAttendance(name: String): List<MemberAttendance> {
        return memberAttendanceRecords[name.lowercase()] ?: emptyList()
    }

    fun searchEventAttendance(date: String): List<EventAttendance> {
        // Assuming date is in ISO_DATE format (e.g., "YYYY-MM-DD")
        return attendanceRecords[date] ?: emptyList()
    }
}
