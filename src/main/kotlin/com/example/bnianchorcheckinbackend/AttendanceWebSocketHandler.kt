package com.example.bnianchorcheckinbackend

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.CopyOnWriteArrayList

@Component
class AttendanceWebSocketHandler(private val objectMapper: ObjectMapper) : TextWebSocketHandler() {
    private val sessions = CopyOnWriteArrayList<WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessions.add(session)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        sessions.remove(session)
    }

    fun broadcast(message: Any) {
        val jsonMessage = objectMapper.writeValueAsString(message)
        sessions.forEach { session ->
            try {
                if (session.isOpen) {
                    session.sendMessage(TextMessage(jsonMessage))
                }
            } catch (e: Exception) {
                // Handle exception
            }
        }
    }
}

