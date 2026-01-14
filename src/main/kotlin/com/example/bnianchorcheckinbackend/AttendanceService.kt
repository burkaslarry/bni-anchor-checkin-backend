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
        
        // Parse client's current time for accurate check-in time
        val clientTime = try {
            // Parse ISO format with timezone (e.g., "2025-12-03T13:09:09.000Z" or "2025-12-03T13:09:09+08:00")
            java.time.ZonedDateTime.parse(request.currentTime).toLocalDateTime()
        } catch (e: Exception) {
            try {
                // Try parsing as LocalDateTime
                LocalDateTime.parse(request.currentTime.replace("Z", ""))
            } catch (e2: Exception) {
                // Fallback to server time
                now
            }
        }
        
        // Determine role based on type and request
        val role = when {
            request.type.equals("member", ignoreCase = true) -> "MEMBER"
            request.role.uppercase() in listOf("VIP", "SPEAKER") -> request.role.uppercase()
            else -> "GUEST"
        }
        
        val record = CheckInRecord(
            name = request.name,
            type = request.type.lowercase(),
            domain = domain,
            timestamp = request.currentTime,
            receivedAt = now.toString(),
            role = role,
            tags = request.tags,
            referrer = request.referrer
        )

        allRecords.add(record)
        
        // Update attendance for report page - use client time
        if (request.type.equals("member", ignoreCase = true)) {
            updateAttendance(request.name, clientTime, "MEMBER")
        } else {
            // Also track guests/VIPs in attendance for report
            updateGuestAttendance(request.name, clientTime, role, request.tags)
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
        
        val allRecords = attendanceMap.values.toList()
        
        val attendees = allRecords
            .filter { it.status == "on-time" || it.status == "late" }
            .sortedByDescending { it.checkInTime }
            
        val absentees = allRecords
            .filter { it.status == "absent" }
            .sortedBy { it.memberName }
        
        // Calculate statistics
        val stats = ReportStats(
            totalAttendees = attendees.size,
            onTimeCount = attendees.count { it.status == "on-time" },
            lateCount = attendees.count { it.status == "late" },
            absentCount = absentees.size,
            guestCount = allRecords.count { it.role == "GUEST" },
            vipCount = allRecords.count { it.role == "VIP" },
            vipArrivedCount = attendees.count { it.role == "VIP" },
            speakerCount = allRecords.count { it.role == "SPEAKER" }
        )
            
        return ReportData(
            eventId = currentEvent.id,
            eventName = currentEvent.name,
            eventDate = currentEvent.date,
            onTimeCutoff = currentEvent.onTimeCutoff,
            attendees = attendees,
            absentees = absentees,
            stats = stats
        )
    }
    
    fun updateAttendance(memberName: String, checkInTime: LocalDateTime, role: String = "MEMBER", tags: List<String> = emptyList()): AttendanceRecord? {
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
            checkInTime = checkInTime.toLocalTime().format(timeFormatter),
            role = role,
            tags = tags
        )
        
        attendanceMap[memberName] = record
        
        // Broadcast attendance update for report page
        webSocketHandler.broadcast(mapOf(
            "type" to "attendance_updated",
            "data" to record
        ))
        
        return record
    }
    
    fun updateGuestAttendance(guestName: String, checkInTime: LocalDateTime, role: String, tags: List<String> = emptyList()): AttendanceRecord? {
        val currentEvent = getCurrentEvent() ?: return null
        val attendanceMap = eventAttendanceMap[currentEvent.id] ?: return null
        
        // Determine status based on on-time cutoff
        val onTimeCutoff = LocalTime.parse(currentEvent.onTimeCutoff)
        val checkInLocalTime = checkInTime.toLocalTime()
        val status = if (checkInLocalTime.isBefore(onTimeCutoff)) "on-time" else "late"
        
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        val record = AttendanceRecord(
            memberName = guestName,
            status = status,
            checkInTime = checkInTime.toLocalTime().format(timeFormatter),
            role = role,
            tags = tags
        )
        
        // Use a unique key for guests to avoid collision with members
        val key = "guest_${guestName}_${role}"
        attendanceMap[key] = record
        
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
        aiInsightsCache.clear()
        
        // Broadcast clear event
        webSocketHandler.broadcast(mapOf(
            "type" to "all_cleared"
        ))
    }
    
    // ===== AI Insights Methods (Phase 2 - For Future AI Integration) =====
    
    // Cache for generated insights
    private val aiInsightsCache = ConcurrentHashMap<Int, MutableList<AIInsightResponse>>()
    
    fun generateInsights(request: AIInsightRequest): AIInsightResponse {
        val event = events.find { it.id == request.eventId }
        val attendanceMap = eventAttendanceMap[request.eventId]
        
        // Generate stub insights based on analysis type
        val insights = when (request.analysisType) {
            "interest" -> generateInterestInsights(attendanceMap)
            "retention" -> generateRetentionInsights(attendanceMap)
            "target_audience" -> generateTargetAudienceInsights(attendanceMap)
            else -> emptyList()
        }
        
        val recommendations = when (request.analysisType) {
            "interest" -> listOf(
                "根據出席數據分析，建議下次活動主題聚焦於高互動話題",
                "VIP嘉賓傾向參與專業技術分享場次"
            )
            "retention" -> listOf(
                "出席率高於80%的會員可作為核心推廣對象",
                "建議針對連續缺席的會員進行回訪關懷"
            )
            "target_audience" -> listOf(
                "高潛力回流客群已標記，建議優先發送邀請",
                "新訪客轉換率分析顯示專業領域分享效果最佳"
            )
            else -> emptyList()
        }
        
        val response = AIInsightResponse(
            eventId = request.eventId,
            analysisType = request.analysisType,
            generatedAt = LocalDateTime.now().toString(),
            insights = insights,
            recommendations = recommendations
        )
        
        // Cache the response
        aiInsightsCache.computeIfAbsent(request.eventId) { mutableListOf() }.add(response)
        
        return response
    }
    
    private fun generateInterestInsights(attendanceMap: ConcurrentHashMap<String, AttendanceRecord>?): List<InsightItem> {
        if (attendanceMap == null) return emptyList()
        
        val records = attendanceMap.values.toList()
        val totalCount = records.size
        val attendedCount = records.count { it.status != "absent" }
        
        return listOf(
            InsightItem(
                title = "訪客喜好分析",
                description = "根據出席數據，${(attendedCount * 100 / maxOf(totalCount, 1))}% 的參與者完成簽到",
                confidence = 0.85,
                dataPoints = mapOf(
                    "total_registered" to totalCount,
                    "attended" to attendedCount,
                    "attendance_rate" to (attendedCount.toDouble() / maxOf(totalCount, 1))
                )
            ),
            InsightItem(
                title = "嘉賓參與度",
                description = "VIP及嘉賓的參與行為分析",
                confidence = 0.78,
                dataPoints = mapOf(
                    "vip_count" to records.count { it.role == "VIP" },
                    "guest_count" to records.count { it.role == "GUEST" },
                    "speaker_count" to records.count { it.role == "SPEAKER" }
                )
            )
        )
    }
    
    private fun generateRetentionInsights(attendanceMap: ConcurrentHashMap<String, AttendanceRecord>?): List<InsightItem> {
        if (attendanceMap == null) return emptyList()
        
        val records = attendanceMap.values.toList()
        val onTimeRate = records.count { it.status == "on-time" }.toDouble() / maxOf(records.size, 1)
        
        return listOf(
            InsightItem(
                title = "社群留存建議",
                description = "基於出席時間分析，建議優化活動時段安排",
                confidence = 0.82,
                dataPoints = mapOf(
                    "on_time_rate" to onTimeRate,
                    "suggested_start_time" to "07:00",
                    "optimal_duration_minutes" to 120
                )
            )
        )
    }
    
    private fun generateTargetAudienceInsights(attendanceMap: ConcurrentHashMap<String, AttendanceRecord>?): List<InsightItem> {
        if (attendanceMap == null) return emptyList()
        
        val records = attendanceMap.values.toList()
        val highEngagement = records.filter { it.status == "on-time" }
        
        return listOf(
            InsightItem(
                title = "智能推廣名單",
                description = "已識別 ${highEngagement.size} 位高參與度用戶",
                confidence = 0.90,
                dataPoints = mapOf(
                    "high_engagement_count" to highEngagement.size,
                    "target_names" to highEngagement.take(10).map { it.memberName }
                )
            )
        )
    }
    
    fun getEventInsights(eventId: Int): List<AIInsightResponse> {
        return aiInsightsCache[eventId] ?: emptyList()
    }
    
    fun exportAIReadyData(eventId: Int): Map<String, Any>? {
        val event = events.find { it.id == eventId } ?: return null
        val attendanceMap = eventAttendanceMap[eventId] ?: return null
        
        val records = attendanceMap.values.map { record ->
            mapOf(
                "name" to record.memberName,
                "status" to record.status,
                "checkInTime" to (record.checkInTime ?: ""),
                "role" to record.role,
                "tags" to record.tags
            )
        }
        
        return mapOf(
            "eventId" to event.id,
            "eventName" to event.name,
            "eventDate" to event.date,
            "exportedAt" to LocalDateTime.now().toString(),
            "attendanceRecords" to records,
            "summary" to mapOf(
                "total" to records.size,
                "attended" to records.count { it["status"] != "absent" },
                "onTime" to records.count { it["status"] == "on-time" },
                "late" to records.count { it["status"] == "late" },
                "absent" to records.count { it["status"] == "absent" },
                "vip" to records.count { it["role"] == "VIP" },
                "guests" to records.count { it["role"] == "GUEST" }
            )
        )
    }
}
