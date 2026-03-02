package com.example.bnianchorcheckinbackend

import com.example.bnianchorcheckinbackend.entities.MemberStanding
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class UpdateMemberRequest(
    val profession: String? = null,
    val standing: String? = null
)

data class UpdateGuestRequest(
    val profession: String? = null,
    val referrer: String? = null,
    val eventDate: String? = null
)

@RestController
@Tag(name = "Member Management", description = "Endpoints for managing member records")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["spring.datasource.url"])
class MemberManagementController(
    private val databaseMemberService: DatabaseMemberService
) {

    @PutMapping("/api/members/{name}")
    @Operation(summary = "Update member information")
    fun updateMember(
        @PathVariable name: String,
        @RequestBody request: UpdateMemberRequest
    ): ResponseEntity<Map<String, Any>> {
        val standing = try {
            request.standing?.let { MemberStanding.valueOf(it.uppercase()) }
        } catch (e: Exception) {
            return ResponseEntity.badRequest().body(mapOf(
                "status" to "error",
                "message" to "Invalid standing value. Must be GREEN, YELLOW, RED, or BLACK"
            ))
        }

        val updatedMember = try {
            databaseMemberService.updateMember(name, request.profession, standing)
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(mapOf(
                "status" to "error",
                "message" to "資料庫暫時無法連線，無法更新會員資料。"
            ))
        }
        return if (updatedMember != null) {
            ResponseEntity.ok(mapOf(
                "status" to "success",
                "message" to "Member updated successfully",
                "member" to mapOf(
                    "name" to updatedMember.name,
                    "profession" to (updatedMember.profession ?: ""),
                    "standing" to updatedMember.standing.name
                )
            ))
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf(
                "status" to "error",
                "message" to "Member not found"
            ))
        }
    }

    @DeleteMapping("/api/members/{name}")
    @Operation(summary = "Delete a member")
    fun deleteMember(@PathVariable name: String): ResponseEntity<Map<String, String>> {
        val deleted = try {
            databaseMemberService.deleteMember(name)
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(mapOf("status" to "error", "message" to "資料庫暫時無法連線，無法刪除會員。"))
        }
        return if (deleted) {
            ResponseEntity.ok(mapOf("status" to "success", "message" to "Member deleted successfully"))
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("status" to "error", "message" to "Member not found"))
        }
    }

    @PutMapping("/api/guests/{name}")
    @Operation(summary = "Update guest information")
    fun updateGuest(
        @PathVariable name: String,
        @RequestBody request: UpdateGuestRequest
    ): ResponseEntity<Map<String, Any>> {
        val updatedGuest = try {
            databaseMemberService.updateGuest(name, request.profession, request.referrer, request.eventDate)
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(mapOf(
                "status" to "error",
                "message" to "資料庫暫時無法連線，無法更新嘉賓資料。"
            ))
        }
        return if (updatedGuest != null) {
            ResponseEntity.ok(mapOf(
                "status" to "success",
                "message" to "Guest updated successfully",
                "guest" to mapOf(
                    "name" to updatedGuest.name,
                    "profession" to updatedGuest.profession,
                    "referrer" to (updatedGuest.referrer ?: ""),
                    "eventDate" to (updatedGuest.eventDate ?: "")
                )
            ))
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf(
                "status" to "error",
                "message" to "Guest not found"
            ))
        }
    }

    @DeleteMapping("/api/guests/{name}")
    @Operation(summary = "Delete a guest")
    fun deleteGuest(@PathVariable name: String): ResponseEntity<Map<String, String>> {
        val deleted = try {
            databaseMemberService.deleteGuest(name)
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(mapOf("status" to "error", "message" to "資料庫暫時無法連線，無法刪除嘉賓。"))
        }
        return if (deleted) {
            ResponseEntity.ok(mapOf("status" to "success", "message" to "Guest deleted successfully"))
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("status" to "error", "message" to "Guest not found"))
        }
    }
}
