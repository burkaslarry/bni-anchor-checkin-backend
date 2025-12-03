package com.example.bnianchorcheckinbackend

import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WebSocketConfig(private val attendanceWebSocketHandler: AttendanceWebSocketHandler) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(attendanceWebSocketHandler, "/ws/records").setAllowedOrigins("*")
        registry.addHandler(attendanceWebSocketHandler, "/ws/report").setAllowedOrigins("*")
    }
}

