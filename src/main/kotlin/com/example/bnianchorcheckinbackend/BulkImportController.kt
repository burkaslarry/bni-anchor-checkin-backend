package com.example.bnianchorcheckinbackend

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Bulk Import", description = "Endpoints for bulk importing members and guests")
class BulkImportController(
    @org.springframework.beans.factory.annotation.Autowired(required = false) private val bulkImportService: BulkImportService?,
    private val guestService: GuestService
) {

    private val log = org.slf4j.LoggerFactory.getLogger(BulkImportController::class.java)

    private fun isDbConnectionError(errors: List<String>): Boolean {
        if (errors.isEmpty()) return false
        val msg = errors.joinToString(" ").lowercase()
        return "connection" in msg || "jdbc" in msg || "unable to acquire" in msg ||
            "無法連線" in msg || "timeout" in msg || "no route" in msg
    }

    @PostMapping("/api/bulk-import")
    @Operation(summary = "Bulk import members or guests from CSV data")
    fun bulkImport(@RequestBody request: BulkImportRequest): ResponseEntity<ImportResult> {
        if (bulkImportService == null) {
            return if (request.type.lowercase() == "guest") {
                val fallback = guestService.addBulkImportedGuests(request.records)
                ResponseEntity.ok(fallback)
            } else {
                ResponseEntity.ok(ImportResult(
                    total = request.records.size, inserted = 0, updated = 0, failed = request.records.size,
                    errors = listOf("匯入會員需要資料庫連線，請設定 spring.datasource.url")
                ))
            }
        }
        return try {
            val result = bulkImportService.bulkImport(request)
            if (result.failed > 0 && request.type.lowercase() == "guest" && isDbConnectionError(result.errors)) {
                log.warn("DB unavailable for guest import, falling back to in-memory store")
                val fallback = guestService.addBulkImportedGuests(request.records)
                return ResponseEntity.ok(fallback)
            }
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            log.error("Bulk import failed: {}", e.message, e)
            if (request.type.lowercase() == "guest") {
                try {
                    val fallback = guestService.addBulkImportedGuests(request.records)
                    return ResponseEntity.ok(fallback)
                } catch (e2: Exception) {
                    log.error("Guest fallback also failed: {}", e2.message)
                }
            }
            ResponseEntity.ok(ImportResult(
                total = request.records.size, inserted = 0, updated = 0, failed = request.records.size,
                errors = listOf("資料庫暫時無法連線，無法儲存匯入資料。請稍後重試。")
            ))
        }
    }

    @PostMapping("/api/bulk-import/members", "/api/bulk-import-members")
    @Operation(summary = "Bulk import members only")
    fun bulkImportMembers(@RequestBody records: List<ImportRecord>): ResponseEntity<ImportResult> {
        if (bulkImportService == null) {
            return ResponseEntity.ok(ImportResult(
                total = records.size, inserted = 0, updated = 0, failed = records.size,
                errors = listOf("匯入會員需要資料庫連線")
            ))
        }
        return try {
            ResponseEntity.ok(bulkImportService.bulkImportMembers(records))
        } catch (e: Exception) {
            log.error("Bulk import members failed: {}", e.message)
            ResponseEntity.ok(ImportResult(
                total = records.size, inserted = 0, updated = 0, failed = records.size,
                errors = listOf("資料庫暫時無法連線，無法儲存匯入資料。")
            ))
        }
    }

    @PostMapping("/api/bulk-import/guests", "/api/bulk-import-guest")
    @Operation(summary = "Bulk import guests only")
    fun bulkImportGuests(@RequestBody records: List<ImportRecord>): ResponseEntity<ImportResult> {
        return try {
            if (bulkImportService != null) {
                ResponseEntity.ok(bulkImportService.bulkImportGuests(records))
            } else {
                ResponseEntity.ok(guestService.addBulkImportedGuests(records))
            }
        } catch (e: Exception) {
            log.error("Bulk import guests failed: {}", e.message)
            return try {
                ResponseEntity.ok(guestService.addBulkImportedGuests(records))
            } catch (e2: Exception) {
                ResponseEntity.ok(ImportResult(
                    total = records.size, inserted = 0, updated = 0, failed = records.size,
                    errors = listOf("資料庫暫時無法連線，無法儲存匯入資料。")
                ))
            }
        }
    }
}
