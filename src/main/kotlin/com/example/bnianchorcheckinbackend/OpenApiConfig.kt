package com.example.bnianchorcheckinbackend

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.servers.Server
import org.springframework.context.annotation.Configuration

@Configuration
@OpenAPIDefinition(
    info = Info(
        title = "BNI Anchor Attendance API",
        version = "1.0",
        description = "REST API for recording member/guest attendance and querying historical records."
    ),
    servers = [
        Server(url = "http://localhost:8080", description = "Local")
    ]
)
class OpenApiConfig

