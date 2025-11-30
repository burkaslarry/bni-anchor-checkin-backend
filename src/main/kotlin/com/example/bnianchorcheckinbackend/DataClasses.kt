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
    val domain: String = ""
)

data class EventRequest(
    val name: String,
    val date: String
)

data class CheckInRecord(
    val name: String,
    val domain: String,
    val type: String,
    val timestamp: String,
    val receivedAt: String
)
