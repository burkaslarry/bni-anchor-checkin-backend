package com.example.bnianchorcheckinbackend

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Service
class DeepSeekService(
    private val objectMapper: ObjectMapper
) {
    
    @Value("\${deepseek.api.key:}")
    private lateinit var apiKey: String
    
    @Value("\${deepseek.api.url:https://api.deepseek.com/v1/chat/completions}")
    private lateinit var apiUrl: String
    
    private val httpClient = HttpClient.newBuilder().build()
    
    data class DeepSeekRequest(
        val model: String = "deepseek-chat",  // Use deepseek-chat for better JSON output
        val messages: List<Message>,
        val temperature: Double? = 0.7,
        val max_tokens: Int = 2000,
        val response_format: ResponseFormat? = null
    )
    
    // Request for deepseek-reasoner (no temperature support)
    data class DeepSeekReasonerRequest(
        val model: String = "deepseek-reasoner",
        val messages: List<Message>,
        val max_tokens: Int = 4000
    )
    
    data class ResponseFormat(
        val type: String = "json_object"
    )
    
    data class Message(
        val role: String,
        val content: String
    )
    
    // Response for deepseek-reasoner includes reasoning_content
    data class ReasonerMessage(
        val role: String,
        val content: String?,
        val reasoning_content: String?
    )
    
    data class DeepSeekResponse(
        val choices: List<Choice>
    )
    
    data class DeepSeekReasonerResponse(
        val choices: List<ReasonerChoice>
    )
    
    data class Choice(
        val message: Message
    )
    
    data class ReasonerChoice(
        val message: ReasonerMessage
    )
    
    fun generateInsight(prompt: String): String {
        if (apiKey.isBlank()) {
            return "DeepSeek API key not configured. Please set deepseek.api.key in application.properties"
        }
        
        return try {
            val request = DeepSeekRequest(
                messages = listOf(
                    Message(
                        role = "system",
                        content = "You are a BNI networking event analyst. Provide insights based on attendance data."
                    ),
                    Message(
                        role = "user",
                        content = prompt
                    )
                )
            )
            
            val requestBody = objectMapper.writeValueAsString(request)
            
            val httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $apiKey")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()
            
            val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val deepSeekResponse = objectMapper.readValue(response.body(), DeepSeekResponse::class.java)
                deepSeekResponse.choices.firstOrNull()?.message?.content 
                    ?: "No response from DeepSeek API"
            } else {
                "DeepSeek API error: ${response.statusCode()} - ${response.body()}"
            }
        } catch (e: Exception) {
            "Error calling DeepSeek API: ${e.message}"
        }
    }
    
    fun analyzeGuestMatch(
        guestName: String,
        guestProfession: String,
        membersProfessions: List<String>
    ): String {
        val prompt = """
        Guest Information:
        - Name: $guestName
        - Profession: $guestProfession
        
        Available Members' Professions:
        ${membersProfessions.joinToString("\n") { "- $it" }}
        
        Task: Analyze which members would benefit most from networking with this guest. 
        Provide 3-5 specific reasons why certain professions would synergize well.
        Format: Brief, actionable insights in Traditional Chinese.
        """.trimIndent()
        
        return generateInsight(prompt)
    }
    
    fun generateRetentionStrategy(
        attendanceRate: Double,
        lateRate: Double,
        absentMembers: List<String>
    ): String {
        val prompt = """
        BNI Chapter Statistics:
        - Overall Attendance Rate: ${String.format("%.1f", attendanceRate * 100)}%
        - Late Arrival Rate: ${String.format("%.1f", lateRate * 100)}%
        - Frequently Absent Members: ${absentMembers.take(5).joinToString(", ")}
        
        Task: Provide 3-5 actionable retention strategies to improve attendance and engagement.
        Focus on practical, BNI-specific recommendations.
        Format: Brief bullet points in Traditional Chinese.
        """.trimIndent()
        
        return generateInsight(prompt)
    }
    
    data class MemberMatchRequest(
        val guestName: String,
        val guestProfession: String,
        val guestTargetProfession: String?,
        val guestBottlenecks: List<String>,
        val guestRemarks: String?,
        val members: List<MemberInfo>
    )
    
    data class MemberInfo(
        val name: String,
        val profession: String
    )
    
    /**
     * 使用 DeepSeek API 進行會員匹配
     * 返回 JSON 格式的匹配結果
     * 
     * 使用 deepseek-chat 模型以獲得更好的 JSON 格式輸出
     */
    fun matchMembersWithAI(request: MemberMatchRequest): String {
        println("🤖 [DeepSeekService] Starting matchMembersWithAI")
        println("📊 [DeepSeekService] Guest: ${request.guestName} (${request.guestProfession})")
        println("📊 [DeepSeekService] Members count: ${request.members.size}")
        
        if (apiKey.isBlank()) {
            println("❌ [DeepSeekService] API key is blank!")
            return """{"error": "DeepSeek API key not configured"}"""
        }
        
        println("✅ [DeepSeekService] API key configured: ${apiKey.take(10)}...")
        
        val memberList = request.members.joinToString("\n") { "- ${it.name} (${it.profession})" }
        
        val prompt = buildMatchPrompt(
            guestName = request.guestName,
            guestProfession = request.guestProfession,
            guestTargetProfession = request.guestTargetProfession,
            guestBottlenecks = request.guestBottlenecks,
            guestRemarks = request.guestRemarks,
            memberList = memberList
        )
        
        println("📝 [DeepSeekService] Prompt length: ${prompt.length} chars")
        
        return try {
            // Use deepseek-chat for better JSON output (deepseek-reasoner doesn't support temperature/json_format)
            val deepSeekRequest = DeepSeekRequest(
                model = "deepseek-chat",
                messages = listOf(
                    Message(
                        role = "system",
                        content = "你是一個專業的商業配對顧問。請務必只返回有效的 JSON 陣列格式，不要包含任何其他說明文字。"
                    ),
                    Message(
                        role = "user",
                        content = prompt
                    )
                ),
                temperature = 0.7,
                max_tokens = 2000,
                response_format = ResponseFormat(type = "json_object")
            )
            
            val requestBody = objectMapper.writeValueAsString(deepSeekRequest)
            println("📤 [DeepSeekService] Sending request to: $apiUrl")
            println("📤 [DeepSeekService] Using model: deepseek-chat")
            
            val httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $apiKey")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()
            
            val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
            println("📥 [DeepSeekService] Response status: ${response.statusCode()}")
            
            if (response.statusCode() == 200) {
                val responseBody = response.body()
                println("✅ [DeepSeekService] Response received (${responseBody.length} chars)")
                println("📄 [DeepSeekService] Raw response: ${responseBody.take(500)}...")
                
                val deepSeekResponse = objectMapper.readValue(responseBody, DeepSeekResponse::class.java)
                val content = deepSeekResponse.choices.firstOrNull()?.message?.content 
                    ?: run {
                        println("❌ [DeepSeekService] No content in response")
                        return """{"error": "No response from DeepSeek API"}"""
                    }
                
                println("📄 [DeepSeekService] Content preview: ${content.take(300)}...")
                
                // Extract JSON array from response (handle both direct array and wrapped object)
                val jsonArrayMatch = Regex("""\[[\s\S]*\]""").find(content)
                if (jsonArrayMatch != null) {
                    println("✅ [DeepSeekService] JSON array extracted successfully")
                    jsonArrayMatch.value
                } else {
                    // Try to parse as JSON object with "matches" key
                    try {
                        val jsonObject = objectMapper.readTree(content)
                        if (jsonObject.has("matches")) {
                            println("✅ [DeepSeekService] JSON extracted from 'matches' key")
                            jsonObject.get("matches").toString()
                        } else {
                            println("⚠️ [DeepSeekService] No JSON array found, returning full content")
                            content
                        }
                    } catch (e: Exception) {
                        println("⚠️ [DeepSeekService] JSON parsing failed, returning raw content")
                        content
                    }
                }
            } else {
                val errorBody = response.body()
                println("❌ [DeepSeekService] API error ${response.statusCode()}: $errorBody")
                """{"error": "DeepSeek API error: ${response.statusCode()}", "details": "$errorBody"}"""
            }
        } catch (e: Exception) {
            println("❌ [DeepSeekService] Exception: ${e.javaClass.simpleName} - ${e.message}")
            e.printStackTrace()
            """{"error": "Error calling DeepSeek API: ${e.message}"}"""
        }
    }
    
    private fun buildMatchPrompt(
        guestName: String,
        guestProfession: String,
        guestTargetProfession: String?,
        guestBottlenecks: List<String>,
        guestRemarks: String?,
        memberList: String
    ): String {
        return """
You are an elite strategic networking consultant for a business event. Your mission is to identify HIGH-VALUE connections that lead to immediate referrals and long-term partnerships.

【來賓檔案 Guest Profile】
姓名: $guestName
職業: $guestProfession
${if (guestTargetProfession != null) "目標對接: $guestTargetProfession" else ""}
瓶頸/需求: ${if (guestBottlenecks.isNotEmpty()) guestBottlenecks.joinToString(", ") else "未指定"}
${if (guestRemarks != null) "價值交換: $guestRemarks" else ""}

【可配對會員列表 Available Members】
$memberList

【核心配對原則 Core Matching Principles】
1. **價值互補 (Value Complementarity)**: 優先推薦能解決來賓「瓶頸」的專業人士
2. **目標對接 (Target Alignment)**: 如果來賓有明確的「目標職業」，尋找該領域或能引薦該領域的會員
3. **資源交換 (Resource Exchange)**: 關注備註中的「價值提供」，推薦能產生雙向價值的人脈
4. **行業互補 (Industry Synergy)**: 尋找上下游產業鏈、異業合作機會

【配對策略 Matching Strategy】
- **High Match**: 會員能直接解決來賓瓶頸，或其職業正是來賓的目標對接對象
- **Medium Match**: 會員能提供相關協助，或行業高度相關
- **Low Match**: 會員可提供一般人脈拓展，但無直接業務契合點

【輸出要求 Output Format】
請推薦最適合與來賓配對的會員（最多10位），並說明配對原因。必須返回以下 JSON 格式：

{"matches": [
  {
    "memberName": "會員姓名",
    "matchStrength": "High",
    "reason": "[會員姓名] ([職業]) 能直接解決來賓的 [具體瓶頸]，或在 [目標領域] 有豐富資源，可提供精準引薦和業務合作機會。"
  }
]}

**重要提醒**: 
- 只推薦真正有價值的配對（不一定要推薦全部會員）
- 每個 reason 必須具體說明該會員能提供什麼價值給來賓
- 按照配對價值排序（最佳在前）
- matchStrength 只能是 "High"、"Medium" 或 "Low"
- 必須返回有效的 JSON 格式，包含 "matches" 陣列
        """.trimIndent()
    }
    
    /**
     * 簡化版配對 - 用於來賓簽到後的快速配對
     * 只返回會員名稱和專業領域，不需要理由
     */
    fun quickMatchForCheckin(guestName: String, guestProfession: String, members: List<MemberInfo>): String {
        println("🤖 [DeepSeekService] Starting quickMatchForCheckin")
        println("📊 [DeepSeekService] Guest: $guestName ($guestProfession)")
        
        if (apiKey.isBlank()) {
            return """{"error": "DeepSeek API key not configured"}"""
        }
        
        val memberList = members.joinToString("\n") { "- ${it.name} (${it.profession})" }
        
        val prompt = """
你是 BNI 商會的配對顧問。根據來賓的專業領域，找出最適合交流的會員。

【來賓】$guestName - $guestProfession

【會員列表】
$memberList

請找出最適合與此來賓交流的會員（最多5位），返回 JSON 格式：
{"matches": [{"memberName": "會員姓名", "profession": "專業領域", "matchStrength": "High"}]}

matchStrength: High（直接相關）、Medium（間接相關）、Low（可拓展人脈）
        """.trimIndent()
        
        return try {
            val deepSeekRequest = DeepSeekRequest(
                model = "deepseek-chat",
                messages = listOf(
                    Message(role = "system", content = "你是商業配對顧問，只返回 JSON 格式。"),
                    Message(role = "user", content = prompt)
                ),
                temperature = 0.5,
                max_tokens = 1000,
                response_format = ResponseFormat(type = "json_object")
            )
            
            val requestBody = objectMapper.writeValueAsString(deepSeekRequest)
            
            val httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $apiKey")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()
            
            val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val deepSeekResponse = objectMapper.readValue(response.body(), DeepSeekResponse::class.java)
                deepSeekResponse.choices.firstOrNull()?.message?.content ?: """{"matches": []}"""
            } else {
                """{"error": "API error: ${response.statusCode()}"}"""
            }
        } catch (e: Exception) {
            println("❌ [DeepSeekService] quickMatch error: ${e.message}")
            """{"error": "${e.message}"}"""
        }
    }
}
