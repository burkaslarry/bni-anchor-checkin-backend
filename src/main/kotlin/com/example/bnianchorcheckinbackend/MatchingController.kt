package com.example.bnianchorcheckinbackend

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST controller for guest–member matching via DeepSeek AI. No auth. Members from DB or CSV fallback.
 * Endpoints: POST /api/matching/members (full match), POST /api/matching/quick, POST /api/matching/batch, GET /api/matching/health.
 * Side effects: external DeepSeek API calls; no DB write.
 */
@RestController
@RequestMapping("/api/matching")
@CrossOrigin(origins = ["*"])
class MatchingController(
    private val deepSeekService: DeepSeekService,
    private val csvService: CsvService,
    @Autowired(required = false) private val databaseMemberService: DatabaseMemberService?
) {
    
    data class MemberMatchResponse(
        val matches: String,
        val provider: String = "deepseek"
    )
    
    /**
     * Full member match for one guest (target, bottlenecks, remarks). POST /api/matching/members.
     * Side effect: DeepSeek API call. Returns JSON string in "matches" (array) or error JSON.
     * @return 200 { "matches": "...", "provider": "deepseek" } | 500 { "matches": "{\"error\":\"...\"}", "provider": "error" }
     */
    @PostMapping("/members")
    fun matchMembers(
        @RequestBody request: DeepSeekService.MemberMatchRequest
    ): ResponseEntity<MemberMatchResponse> {
        return try {
            println("📥 [MatchingController] Received match request for: ${request.guestName}")
            val result = deepSeekService.matchMembersWithAI(request)
            println("📤 [MatchingController] Returning result: ${result.take(200)}...")
            ResponseEntity.ok(MemberMatchResponse(matches = result, provider = "deepseek"))
        } catch (e: Exception) {
            println("❌ [MatchingController] Error: ${e.message}")
            ResponseEntity.internalServerError()
                .body(MemberMatchResponse(
                    matches = """{"error": "${e.message}"}""",
                    provider = "error"
                ))
        }
    }
    
    /** Request for quick match: guestName, guestProfession. */
    data class QuickMatchRequest(
        val guestName: String,
        val guestProfession: String
    )
    
    /** Members from DB (if [DatabaseMemberService] present) or CSV. Side effect: DB or CSV read. */
    private fun getMembersForMatching(): List<DeepSeekService.MemberInfo> {
        return if (databaseMemberService != null) {
            try {
                databaseMemberService.getAllMembers().map { m ->
                    DeepSeekService.MemberInfo(
                        m["name"] as String,
                        m["domain"] as? String ?: ""
                    )
                }
            } catch (e: Exception) {
                csvService.getMembers().map { DeepSeekService.MemberInfo(it.name, it.domain) }
            }
        } else {
            csvService.getMembers().map { DeepSeekService.MemberInfo(it.name, it.domain) }
        }
    }

    /**
     * Quick match at check-in (guest name + profession only). POST /api/matching/quick. Side effect: DeepSeek API.
     * @return 200 { "matches", "provider" } | 500
     */
    @PostMapping("/quick")
    fun quickMatch(
        @RequestBody request: QuickMatchRequest
    ): ResponseEntity<MemberMatchResponse> {
        return try {
            println("📥 [MatchingController] Quick match for: ${request.guestName} (${request.guestProfession})")
            val members = getMembersForMatching()
            val result = deepSeekService.quickMatchForCheckin(
                request.guestName, 
                request.guestProfession, 
                members
            )
            ResponseEntity.ok(MemberMatchResponse(matches = result, provider = "deepseek"))
        } catch (e: Exception) {
            println("❌ [MatchingController] Quick match error: ${e.message}")
            ResponseEntity.internalServerError()
                .body(MemberMatchResponse(
                    matches = """{"error": "${e.message}"}""",
                    provider = "error"
                ))
        }
    }
    
    /** Single guest for batch: name, profession, optional remarks. */
    data class BatchGuestInfo(
        val name: String,
        val profession: String,
        val remarks: String? = null
    )
    
    data class BatchMatchRequest(
        val guests: List<BatchGuestInfo>
    )
    
    data class BatchMatchResult(
        val guestName: String,
        val guestProfession: String,
        val matchedMembers: List<MatchedMember>
    )
    
    data class MatchedMember(
        val memberName: String,
        val profession: String,
        val matchStrength: String,
        val reason: String
    )
    
    data class BatchMatchResponse(
        val results: List<BatchMatchResult>,
        val provider: String = "deepseek"
    )
    
    /**
     * Batch match multiple guests (parallel). POST /api/matching/batch. Side effect: DeepSeek API per guest.
     * @return 200 BatchMatchResponse | 500
     */
    @PostMapping("/batch")
    fun batchMatch(
        @RequestBody request: BatchMatchRequest
    ): ResponseEntity<BatchMatchResponse> {
        return try {
            println("📥 [MatchingController] Batch match for ${request.guests.size} guests (parallel processing)")
            
            val members = getMembersForMatching()
            
            // Process guests in parallel using Java parallel streams
            // Limit concurrency to avoid API rate limits
            val results = request.guests.parallelStream().map { guest ->
                println("🔄 Processing: ${guest.name}")
                
                val matchRequest = DeepSeekService.MemberMatchRequest(
                    guestName = guest.name,
                    guestProfession = guest.profession,
                    guestTargetProfession = null,
                    guestBottlenecks = emptyList(),
                    guestRemarks = guest.remarks,
                    members = members
                )
                
                val matchResult = deepSeekService.matchMembersWithAI(matchRequest)
                
                // Parse result
                val matchedMembers = try {
                    val jsonNode = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readTree(matchResult)
                    val matchesArray = if (jsonNode.has("matches")) jsonNode.get("matches") else jsonNode
                    
                    matchesArray.map { match ->
                        MatchedMember(
                            memberName = match.get("memberName")?.asText() ?: "",
                            profession = members.find { it.name == match.get("memberName")?.asText() }?.profession ?: "",
                            matchStrength = match.get("matchStrength")?.asText() ?: "Low",
                            reason = match.get("reason")?.asText() ?: ""
                        )
                    }
                } catch (e: Exception) {
                    println("⚠️ Parse error for ${guest.name}: ${e.message}")
                    emptyList<MatchedMember>()
                }
                
                BatchMatchResult(
                    guestName = guest.name,
                    guestProfession = guest.profession,
                    matchedMembers = matchedMembers
                )
            }.toList()
            
            println("✅ [MatchingController] Batch match completed for ${results.size} guests")
            ResponseEntity.ok(BatchMatchResponse(results = results))
        } catch (e: Exception) {
            println("❌ [MatchingController] Batch match error: ${e.message}")
            ResponseEntity.internalServerError()
                .body(BatchMatchResponse(results = emptyList(), provider = "error"))
        }
    }
    
    /** Health check. GET /api/matching/health. No side effects. @return 200 { "status", "service", "timestamp" } */
    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf(
            "status" to "ok",
            "service" to "matching",
            "timestamp" to System.currentTimeMillis().toString()
        ))
    }
}
