# BNI Anchor Swagger UI Server

A dedicated Express.js server that serves Swagger UI for the BNI Anchor Attendance API.

## Setup

1. Install dependencies:
```bash
cd swagger-server
npm install
```

2. Start the server:
```bash
npm start
```

The Swagger UI will be available at: **http://localhost:8090**

## Development

To run with automatic reload on file changes:
```bash
npm run dev
```

## How it Works

- **Port**: 8090
- **Backend API**: http://localhost:8080 (must be running)
- The Swagger UI fetches the OpenAPI spec from the backend API
- CORS is enabled for cross-origin requests

## Requirements

- Node.js 14+
- npm or yarn
- Backend API running on http://localhost:8080

