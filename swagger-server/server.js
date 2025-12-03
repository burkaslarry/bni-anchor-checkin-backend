const express = require('express');
const cors = require('cors');
const path = require('path');

const app = express();
const PORT = 8090;
const BACKEND_API = 'http://localhost:8080';

// Enable CORS
app.use(cors());

// Serve static files
app.use(express.static('public'));

// Serve the main Swagger UI page
app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// Proxy the OpenAPI spec from backend
app.get('/api-docs', async (req, res) => {
  try {
    const response = await fetch(`${BACKEND_API}/v3/api-docs`);
    const data = await response.json();
    res.json(data);
  } catch (error) {
    console.error('Error fetching API docs:', error);
    res.status(500).json({ error: 'Failed to fetch API documentation' });
  }
});

// Health check endpoint
app.get('/health', (req, res) => {
  res.json({ status: 'ok', message: 'Swagger UI server is running' });
});

app.listen(PORT, () => {
  console.log(`✅ Swagger UI server running on http://localhost:${PORT}`);
  console.log(`📚 Backend API: ${BACKEND_API}`);
  console.log(`🔗 Open browser: http://localhost:${PORT}`);
});

