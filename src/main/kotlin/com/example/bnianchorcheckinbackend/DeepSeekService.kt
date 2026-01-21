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
        val model: String = "deepseek-chat",
        val messages: List<Message>,
        val temperature: Double = 0.7,
        val max_tokens: Int = 1000
    )
    
    data class Message(
        val role: String,
        val content: String
    )
    
    data class DeepSeekResponse(
        val choices: List<Choice>
    )
    
    data class Choice(
        val message: Message
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
}
