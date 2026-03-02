package com.example.bnianchorcheckinbackend

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Bulk Import", description = "Endpoints for bulk importing members and guests")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["spring.datasource.url"])
class BulkImportController(
    private val bulkImportService: BulkImportService
) {

    private val log = org.slf4j.LoggerFactory.getLogger(BulkImportController::class.java)

    @PostMapping("/api/bulk-import")
    @Operation(summary = "Bulk import members or guests from CSV data")
    fun bulkImport(@RequestBody request: BulkImportRequest): ResponseEntity<ImportResult> {
        return try {
            ResponseEntity.ok(bulkImportService.bulkImport(request))
        } catch (e: Exception) {
            log.error("Bulk import failed: {}", e.message)
            ResponseEntity.ok(ImportResult(
                total = request.records.size, inserted = 0, updated = 0, failed = request.records.size,
                errors = listOf("資料庫暫時無法連線，無法儲存匯入資料。請稍後重試。")
            ))
        }
    }

    @PostMapping("/api/bulk-import/members")
    @Operation(summary = "Bulk import members only")
    fun bulkImportMembers(@RequestBody records: List<ImportRecord>): ResponseEntity<ImportResult> {
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

    @PostMapping("/api/bulk-import/guests")
    @Operation(summary = "Bulk import guests only")
    fun bulkImportGuests(@RequestBody records: List<ImportRecord>): ResponseEntity<ImportResult> {
        return try {
            ResponseEntity.ok(bulkImportService.bulkImportGuests(records))
        } catch (e: Exception) {
            log.error("Bulk import guests failed: {}", e.message)
            ResponseEntity.ok(ImportResult(
                total = records.size, inserted = 0, updated = 0, failed = records.size,
                errors = listOf("資料庫暫時無法連線，無法儲存匯入資料。")
            ))
        }
    }
}
