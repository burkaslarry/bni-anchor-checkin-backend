# BNI Anchor Check-in Backend

A Kotlin Spring Boot REST API for managing attendance check-ins at BNI Anchor meetings. The application supports recording member and guest attendance via QR code payloads, and provides endpoints to query attendance history.

## Features

- **QR Code Attendance Recording**: Record attendance for members and guests using QR code payloads
- **Member Validation**: Validates member information against a locally loaded CSV database
- **Attendance History**: Query member and event attendance records
- **Swagger/OpenAPI Documentation**: Interactive API documentation available at `/swagger-ui.html`
- **REST API**: Fully RESTful API with proper HTTP status codes and error handling

## Project Structure

```
.
├── build.gradle.kts                 # Gradle build configuration
├── settings.gradle                  # Gradle settings
├── gradle/                          # Gradle wrapper files
├── src/
│   ├── main/
│   │   ├── kotlin/com/example/bnianchorcheckinbackend/
│   │   │   ├── BniAnchorCheckinBackendApplication.kt  # Main Spring Boot application
│   │   │   ├── DataClasses.kt                         # Data models (MemberQRData, GuestQRData, etc.)
│   │   │   ├── CsvService.kt                          # CSV data loading service
│   │   │   ├── AttendanceService.kt                   # Business logic for attendance recording
│   │   │   ├── AttendanceController.kt                # REST API endpoints
│   │   │   └── OpenApiConfig.kt                       # Swagger/OpenAPI configuration
│   │   └── resources/
│   │       └── members.csv                            # Member and guest data (pipe-delimited)
```

## Prerequisites

- Java 17 or higher
- Gradle 8.14.3 or higher (included via wrapper)

## Running the Application

1. **Build the project**:
   ```bash
   ./gradlew build
   ```

2. **Run the application**:
   ```bash
   ./gradlew bootRun
   ```

   The application will start on `http://localhost:8080`

## API Endpoints

### 1. Record Attendance (POST)

**Endpoint**: `POST /api/attendance/scan`

**Description**: Record attendance using a QR code payload containing member or guest information.

**Request Body**:
```json
{
  "qrPayload": "{\"name\":\"Jessica Cheung\",\"time\":\"2025-11-16T10:30:00\",\"type\":\"member\",\"membershipId\":\"ANCHOR-001\"}"
}
```

**Response (Success - 200)**:
```json
{
  "message": "Attendance recorded successfully for Jessica Cheung (Member)."
}
```

**Response (Error - 400)**:
```json
{
  "message": "Invalid member or membership ID."
}
```

---

### 2. Search Member Attendance (GET)

**Endpoint**: `GET /api/attendance/member?name=<member_name>`

**Description**: Retrieve attendance history for a specific member.

**Example**: `GET /api/attendance/member?name=Jessica%20Cheung`

**Response (200)**:
```json
[
  {
    "eventName": "BNI Anchor Meeting",
    "eventDate": "2025-11-16",
    "status": "Present"
  }
]
```

---

### 3. Search Event Attendance (GET)

**Endpoint**: `GET /api/attendance/event?date=<event_date>`

**Description**: Get the attendance roster for a given event date (ISO date format: YYYY-MM-DD).

**Example**: `GET /api/attendance/event?date=2025-11-16`

**Response (200)**:
```json
[
  {
    "memberName": "Jessica Cheung",
    "membershipId": "ANCHOR-001",
    "status": "Present"
  },
  {
    "memberName": "Karin Yeung",
    "membershipId": null,
    "status": "Present"
  }
]
```

---

## QR Code Payload Format

The application supports two types of QR code payloads:

### Member QR Code
```json
{
  "name": "Jessica Cheung",
  "time": "2025-11-16T10:30:00",
  "type": "member",
  "membershipId": "ANCHOR-001"
}
```

### Guest QR Code
```json
{
  "name": "Karin Yeung",
  "time": "2025-11-16T10:35:00",
  "type": "guest",
  "referrer": "Larry Lo"
}
```

## Member Data (CSV Format)

Members are loaded from `src/main/resources/members.csv`. The file uses pipe-delimited format:

```
Name | Domain | Type | Membership | Referrer
Jessica Cheung | Personal Services | Member | ANCHOR-001 |
Karin Yeung | Guest | Guest | | Larry Lo
```

### Column Descriptions:
- **Name**: Person's name
- **Domain**: Business domain or service
- **Type**: Either "Member" or "Guest"
- **Membership**: Membership ID (format: ANCHOR-{number}, e.g., ANCHOR-001). Only for members.
- **Referrer**: Name of the member who referred this guest. Only for guests.

## API Documentation

### Option 1: Swagger UI on Backend (Port 8080)
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

### Option 2: Dedicated Swagger UI Server (Port 8090)

A separate Express.js server provides a cleaner Swagger UI experience on port 8090:

1. **Install and start the Swagger UI server**:
   ```bash
   cd swagger-server
   npm install
   npm start
   ```

2. **Access the UI**:
   - **Swagger UI**: http://localhost:8090
   - **Health Check**: http://localhost:8090/health

The Swagger UI server proxies the OpenAPI spec from the backend API and provides an enhanced interface.

## Development Notes

### Technology Stack
- **Language**: Kotlin 1.9.20
- **Framework**: Spring Boot 3.4.0
- **Build Tool**: Gradle 8.14.3
- **Database**: In-memory (ConcurrentHashMap)
- **Documentation**: Springdoc OpenAPI 2.6.0

### Key Components

1. **DataClasses.kt**: Defines sealed class `AttendanceQRData` with JSON polymorphic deserialization
2. **CsvService.kt**: Loads member data from CSV file on application startup
3. **AttendanceService.kt**: Core business logic for recording and querying attendance
4. **AttendanceController.kt**: REST endpoints with Swagger annotations

## Testing the API

### Example: Record a Member Attendance
```bash
curl -X POST 'http://localhost:8080/api/attendance/scan' \
  -H 'Content-Type: application/json' \
  -d '{
    "qrPayload": "{\"name\":\"Jessica Cheung\",\"time\":\"2025-11-16T10:30:00\",\"type\":\"member\",\"membershipId\":\"ANCHOR-001\"}"
  }'
```

### Example: Query Member Attendance
```bash
curl 'http://localhost:8080/api/attendance/member?name=Jessica%20Cheung'
```

### Example: Query Event Attendance
```bash
curl 'http://localhost:8080/api/attendance/event?date=2025-11-16'
```

## Error Handling

The API returns appropriate HTTP status codes:
- **200 OK**: Successful request
- **400 Bad Request**: Invalid input (e.g., invalid member ID, invalid referrer)

Error responses include a message field explaining the issue.

## Future Enhancements

- Persistent database storage (e.g., PostgreSQL, MongoDB)
- User authentication and authorization
- Event management endpoints
- CSV export of attendance records
- Real-time notifications
- Mobile app integration

## License

MIT License

## Contact

For questions or support, please contact the BNI Anchor team.

