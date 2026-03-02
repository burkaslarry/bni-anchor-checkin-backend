package com.example.bnianchorcheckinbackend

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.Mockito.`when`

/**
 * Unit test for "Late Status" logic: if a user checks in after on_time_cutoff_time,
 * the status must be 'late'.
 */
@ExtendWith(MockitoExtension::class)
class AttendanceServiceLateStatusTest {

    @Mock
    lateinit var csvService: CsvService

    @Mock
    lateinit var webSocketHandler: AttendanceWebSocketHandler

    @Mock
    lateinit var deepSeekService: DeepSeekService

    private lateinit var attendanceService: AttendanceService

    @BeforeEach
    fun setUp() {
        attendanceService = AttendanceService(
            csvService = csvService,
            objectMapper = ObjectMapper(),
            webSocketHandler = webSocketHandler,
            deepSeekService = deepSeekService
        )
        // Member list must include "Larry Lo" so he can check in (used by createEvent for absent list)
        `when`(csvService.getAllMembers()).thenReturn(listOf("Larry Lo", "Other Member"))
    }

    @Test
    fun `check-in after on_time_cutoff results in late status`() {
        // Create event with on-time cutoff at 07:05
        attendanceService.createEvent(
            EventRequest(
                name = "BNI Anchor Meeting",
                date = "2026-03-02",
                startTime = "07:00",
                endTime = "09:00",
                registrationStartTime = "06:30",
                onTimeCutoff = "07:05"
            )
        )

        // Check in at 07:06 (after 07:05) - should be LATE
        attendanceService.recordCheckIn(
            CheckInRequest(
                name = "Larry Lo",
                type = "member",
                currentTime = "2026-03-02T07:06:00+08:00",
                domain = "客戶服務系統"
            )
        )

        val report = attendanceService.getReportData()
        require(report != null) { "Report should not be null after check-in" }
        val larryRecord = report.attendees.find { it.memberName == "Larry Lo" }
        require(larryRecord != null) { "Larry Lo should appear in attendees" }
        assertEquals("late", larryRecord.status, "Check-in after 07:05 should be marked late")
    }

    @Test
    fun `check-in before on_time_cutoff results in on-time status`() {
        attendanceService.createEvent(
            EventRequest(
                name = "BNI Anchor Meeting",
                date = "2026-03-02",
                startTime = "07:00",
                endTime = "09:00",
                registrationStartTime = "06:30",
                onTimeCutoff = "07:05"
            )
        )

        // Check in at 07:00 (before 07:05) - should be ON-TIME
        attendanceService.recordCheckIn(
            CheckInRequest(
                name = "Larry Lo",
                type = "member",
                currentTime = "2026-03-02T07:00:00+08:00",
                domain = "客戶服務系統"
            )
        )

        val report = attendanceService.getReportData()
        require(report != null) { "Report should not be null after check-in" }
        val larryRecord = report.attendees.find { it.memberName == "Larry Lo" }
        require(larryRecord != null) { "Larry Lo should appear in attendees" }
        assertEquals("on-time", larryRecord.status, "Check-in before 07:05 should be on-time")
    }
}
