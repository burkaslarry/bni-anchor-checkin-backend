package com.example.bnianchorcheckinbackend

import java.time.LocalDateTime
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = MemberQRData::class, name = "member"),
    JsonSubTypes.Type(value = GuestQRData::class, name = "guest")
)
sealed class AttendanceQRData

data class MemberQRData(
    val name: String,
    val time: LocalDateTime,
    val type: String = "member",
    val membershipId: String
) : AttendanceQRData()

data class GuestQRData(
    val name: String,
    val domain: String, 
    val time: LocalDateTime,
    val type: String = "guest",
    val referrer: String
) : AttendanceQRData()

data class MemberAttendance(
    val eventName: String,
    val eventDate: String,
    val status: String
)

data class EventAttendance(
    val memberName: String,
    val membershipId: String?,
    val status: String
)

data class CheckInRequest(
    val name: String,
    val type: String,
    val currentTime: String,
    val domain: String = "",
    val role: String = "MEMBER", // MEMBER, GUEST, VIP, SPEAKER
    val tags: List<String> = emptyList(), // Interest tags
    val referrer: String? = null // Who invited this guest
)

data class EventRequest(
    val name: String,
    val date: String,
    val startTime: String = "07:00",
    val endTime: String = "09:00",
    val registrationStartTime: String = "06:30",
    val onTimeCutoff: String = "07:01"
)

data class EventData(
    val id: Int,
    val name: String,
    val date: String,
    val startTime: String,
    val endTime: String,
    val registrationStartTime: String,
    val onTimeCutoff: String,
    val createdAt: String
)

// Role types for attendees
enum class AttendeeRole {
    MEMBER,    // Regular member
    GUEST,     // General guest
    VIP,       // VIP guest (speaker, important visitor)
    SPEAKER    // Speaker/presenter
}

data class AttendanceRecord(
    val memberName: String,
    val status: String, // "on-time", "late", "absent"
    val checkInTime: String? = null,
    val role: String = "MEMBER", // MEMBER, GUEST, VIP, SPEAKER
    val tags: List<String> = emptyList(), // Interest tags for AI analysis
    val sessionId: String? = null // Session/topic attended (for AI)
)

data class ReportData(
    val eventId: Int,
    val eventName: String,
    val eventDate: String,
    val onTimeCutoff: String,
    val attendees: List<AttendanceRecord>,
    val absentees: List<AttendanceRecord>,
    // Statistics for dashboard
    val stats: ReportStats = ReportStats()
)

data class ReportStats(
    val totalAttendees: Int = 0,
    val onTimeCount: Int = 0,
    val lateCount: Int = 0,
    val absentCount: Int = 0,
    val guestCount: Int = 0,
    val vipCount: Int = 0,
    val vipArrivedCount: Int = 0,
    val speakerCount: Int = 0
)

// AI Insight related data classes (for future integration)
data class AIInsightRequest(
    val eventId: Int,
    val analysisType: String // "interest", "retention", "target_audience"
)

data class AIInsightResponse(
    val eventId: Int,
    val analysisType: String,
    val generatedAt: String,
    val insights: List<InsightItem>,
    val recommendations: List<String>
)

data class InsightItem(
    val title: String,
    val description: String,
    val confidence: Double,
    val dataPoints: Map<String, Any>
)

data class CheckInRecord(
    val name: String,
    val domain: String,
    val type: String,
    val timestamp: String,
    val receivedAt: String,
    val role: String = "MEMBER", // MEMBER, GUEST, VIP, SPEAKER
    val tags: List<String> = emptyList(),
    val referrer: String? = null
)
