# BNI Anchor Check-in Backend - Quick Start Guide

## 🚀 Get Started in 2 Minutes

### Prerequisites
- Java 17+
- Node.js 14+ (for Swagger UI server)

### Step 1: Start the Backend API (Port 8080)

```bash
# From the project root
./gradlew bootRun
```

You'll see output like:
```
Started BniAnchorCheckinBackendApplication in X seconds
```

✅ Backend is running at: **http://localhost:8080**

### Step 2: Start the Swagger UI Server (Port 8090)

In a new terminal:

```bash
cd swagger-server
npm install  # Only needed on first run
npm start
```

You'll see:
```
✅ Swagger UI server running on http://localhost:8090
📚 Backend API: http://localhost:8080
🔗 Open browser: http://localhost:8090
```

✅ Swagger UI is running at: **http://localhost:8090**

## 📚 Access the API Documentation

Open your browser and go to: **http://localhost:8090**

You'll see an interactive interface where you can:
- Explore all API endpoints
- View request/response schemas
- Test endpoints directly from the browser
- Generate code examples

## 🧪 Quick API Test

### Test 1: Record Member Attendance

```bash
curl -X POST 'http://localhost:8080/api/attendance/scan' \
  -H 'Content-Type: application/json' \
  -d '{
    "qrPayload": "{\"name\":\"Jessica Cheung\",\"time\":\"2025-11-16T10:30:00\",\"type\":\"member\",\"membershipId\":\"ANCHOR-001\"}"
  }'
```

Expected response:
```json
{
  "message": "Attendance recorded successfully for Jessica Cheung (Member)."
}
```

### Test 2: Query Member Attendance

```bash
curl 'http://localhost:8080/api/attendance/member?name=Jessica%20Cheung'
```

Expected response:
```json
[
  {
    "eventName": "BNI Anchor Meeting",
    "eventDate": "2025-11-16",
    "status": "Present"
  }
]
```

### Test 3: Query Event Attendance

```bash
curl 'http://localhost:8080/api/attendance/event?date=2025-11-16'
```

Expected response:
```json
[
  {
    "memberName": "Jessica Cheung",
    "membershipId": "ANCHOR-001",
    "status": "Present"
  }
]
```

## 📋 Available Members (from CSV)

All 51 BNI Anchor members are pre-loaded. Here are some examples:

| Name | Membership | Domain |
|------|-----------|--------|
| Jessica Cheung | ANCHOR-001 | Personal Services |
| Tam O Yan | ANCHOR-002 | Insurance |
| Cyrus Koo | ANCHOR-003 | Insurance |
| Larry Lo | ANCHOR-007 | Customer Service System |
| ... | ... | ... |
| Locus Lam | ANCHOR-051 | Senior Sports Training |

Plus 1 guest: Karin Yeung (referred by Larry Lo)

## 🔗 API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/attendance/scan` | Record attendance via QR code |
| GET | `/api/attendance/member?name=<name>` | Get member's attendance history |
| GET | `/api/attendance/event?date=<date>` | Get event attendance roster |

## 🛠️ Troubleshooting

### Backend won't start
- Make sure Java 17+ is installed: `java -version`
- Check if port 8080 is already in use: `lsof -i :8080`

### Swagger UI won't load
- Ensure Node.js is installed: `node -v`
- Ensure backend is running on http://localhost:8080
- Check if port 8090 is already in use: `lsof -i :8090`

### Can't connect to backend from Swagger UI
- Both services must be running
- Check firewall settings
- Verify URLs in swagger-server/server.js

## 📖 Full Documentation

See [README.md](README.md) for comprehensive documentation including:
- Project structure
- Data models
- CSV format specifications
- Error handling
- Future enhancements

## 🎯 Next Steps

1. ✅ Both services are running
2. ✅ Swagger UI is accessible
3. Test the API endpoints
4. Integrate with your frontend application
5. Deploy to production

Happy check-ins! 🎉

