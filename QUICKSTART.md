# BNI Anchor 簽到系統後端 - 快速入門指南

## 📋 簡介

呢個係 BNI Anchor 簽到 PWA 應用程式嘅後端 API 伺服器。用 Kotlin 同 Spring Boot 寫成，提供會員管理、簽到記錄同出席追蹤嘅 REST API。

## 🚀 快速開始

### 事前準備
- Java 17 或以上版本
- Gradle（已包含 wrapper）

### 喺本機運行

```bash
# Clone 並進入後端目錄
cd bni-anchor-checkin-backend

# 啟動伺服器
./gradlew bootRun
```

伺服器會喺 **http://localhost:8080** 啟動

### Docker 部署

```bash
# 用 Docker 建置同運行
docker build -t bni-checkin-backend .
docker run -p 8080:8080 bni-checkin-backend
```

## 📡 API 端點

### 會員

| 方法 | 端點 | 說明 |
|------|------|------|
| GET | `/api/members` | 攞所有會員名單 |

### 簽到

| 方法 | 端點 | 說明 |
|------|------|------|
| POST | `/api/checkin` | 記錄簽到 |
| GET | `/api/records` | 攞所有簽到記錄 |
| DELETE | `/api/records` | 清除所有記錄 |
| DELETE | `/api/records/{index}` | 刪除指定記錄 |

### 匯出

| 方法 | 端點 | 說明 |
|------|------|------|
| GET | `/api/export` | 匯出記錄做 CSV |

## 📝 API 範例

### 會員簽到

```bash
curl -X POST http://localhost:8080/api/checkin \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Larry Lo",
    "type": "member",
    "currentTime": "2025-11-30T10:00:00Z"
  }'
```

回應：
```json
{"status":"success","message":"Check-in successful"}
```

### 來賓簽到

```bash
curl -X POST http://localhost:8080/api/checkin \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Doe",
    "type": "guest",
    "currentTime": "2025-11-30T10:00:00Z"
  }'
```

### 攞所有記錄

```bash
curl http://localhost:8080/api/records
```

回應：
```json
{
  "records": [
    {
      "name": "Larry Lo",
      "type": "member",
      "timestamp": "2025-11-30T10:00:00Z",
      "receivedAt": "2025-11-30T18:00:00.123456"
    }
  ]
}
```

### 刪除記錄

```bash
curl -X DELETE http://localhost:8080/api/records/0
```

### 清除所有記錄

```bash
curl -X DELETE http://localhost:8080/api/records
```

### 匯出做 CSV

```bash
curl http://localhost:8080/api/export -o attendance.csv
```

## 🔒 功能特點

- **防止重複簽到**：同一個人唔可以簽到兩次
- **即時 WebSocket**：向連接嘅客戶端廣播更新
- **CSV 匯出**：下載出席記錄做 CSV 檔案
- **會員驗證**：會員資料由 `members.csv` 載入

## 📁 專案結構

```
src/main/kotlin/com/example/bnianchorcheckinbackend/
├── BniAnchorCheckinBackendApplication.kt  # 主應用程式
├── AttendanceController.kt                 # REST 端點
├── AttendanceService.kt                    # 業務邏輯
├── CsvService.kt                           # CSV 會員載入
├── DataClasses.kt                          # 數據模型
├── WebSocketConfig.kt                      # WebSocket 設定
└── CorsConfig.kt                           # CORS 設定

src/main/resources/
└── members.csv                             # 會員資料
```

## 🌐 部署

### 部署到 Render.com

1. 去 https://dashboard.render.com
2. 撳 **"New +"** → **"Web Service"**
3. 連接你嘅 GitHub 倉庫
4. 選擇 **bni-anchor-checkin-backend**
5. 設定：
   - **Name**: bni-anchor-checkin-backend
   - **Region**: Singapore
   - **Branch**: main
   - **Runtime**: Docker
   - **Plan**: Free
6. 撳 **"Create Web Service"**
7. 等 3-5 分鐘部署完成

### 環境變數

| 變數 | 說明 | 預設值 |
|------|------|--------|
| `SERVER_PORT` | 伺服器端口 | 8080 |
| `JAVA_OPTS` | JVM 選項 | -Xmx256m |

## 🧪 測試 API

部署完成後，試下呢啲 API：

```bash
# 攞會員名單
curl https://你嘅-render-url.onrender.com/api/members

# 簽到
curl -X POST https://你嘅-render-url.onrender.com/api/checkin \
  -H "Content-Type: application/json" \
  -d '{"name": "Larry Lo", "type": "member", "currentTime": "2025-11-30T10:00:00Z"}'
```

## ❓ 常見問題

### Q: 點解會員名單係空嘅？
A: 確保 `members.csv` 檔案正確放喺 `src/main/resources/` 目錄入面。

### Q: 點解簽到失敗顯示「已經簽到過」？
A: 系統防止同一個人重複簽到。如果要重置，用 `DELETE /api/records` 清除所有記錄。

### Q: 點樣睇 API 文檔？
A: 去 http://localhost:8080/swagger-ui.html（本機運行時）
