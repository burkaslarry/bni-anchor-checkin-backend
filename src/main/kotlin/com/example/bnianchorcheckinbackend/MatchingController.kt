package com.example.bnianchorcheckinbackend

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/matching")
@CrossOrigin(origins = ["*"])
class MatchingController(
    private val deepSeekService: DeepSeekService
) {
    
    data class MemberMatchResponse(
        val matches: String,
        val provider: String = "deepseek"
    )
    
    /**
     * 使用 DeepSeek AI 進行會員配對
     * POST /api/matching/members
     */
    @PostMapping("/members")
    fun matchMembers(
        @RequestBody request: DeepSeekService.MemberMatchRequest
    ): ResponseEntity<MemberMatchResponse> {
        return try {
            val result = deepSeekService.matchMembersWithAI(request)
            ResponseEntity.ok(MemberMatchResponse(matches = result, provider = "deepseek"))
        } catch (e: Exception) {
            ResponseEntity.internalServerError()
                .body(MemberMatchResponse(
                    matches = """{"error": "${e.message}"}""",
                    provider = "error"
                ))
        }
    }
    
    /**
     * 健康檢查 endpoint
     * GET /api/matching/health
     */
    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf(
            "status" to "ok",
            "service" to "matching",
            "timestamp" to System.currentTimeMillis().toString()
        ))
    }
}
