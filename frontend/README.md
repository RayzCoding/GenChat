# GenChat Frontend

Web-connected chat frontend integrated with backend `AgentController` and `SessionController`.

## Local development

```bash
# 1. Start the backend (project root, port 8080)
./gradlew bootRun

# 2. Install dependencies and start the frontend
cd frontend
rm -rf node_modules package-lock.json   # If esbuild fails, clean before reinstall
npm install
npm run dev
```

Open http://localhost:5173 in your browser. Vite proxies `/agent/*` to `http://localhost:8080`.

## Production build

```bash
cd frontend
npm install
npm run build
```

Build output is in `frontend/dist/` and can be deployed to Nginx or any static host.

Set the API base URL for production:

```bash
VITE_API_BASE_URL=https://api.example.com npm run build
```

## Main APIs

| Endpoint | Description |
|----------|-------------|
| `GET /agent/chat/stream` | SSE streaming chat |
| `GET /agent/stop` | Stop generation |
| `GET /agent/sessions` | Session list |
| `GET /agent/sessions/search?q=` | Search history |
| `GET /agent/sessions/{conversationId}` | Session detail |

## Internationalization

Supports `zh-CN` / `en-US`. Language preference is stored in `localStorage` (key: `genchat-lang`).
