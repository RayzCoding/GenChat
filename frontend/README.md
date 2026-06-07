# GenChat Frontend

联网对话问答前端，对接后端 `AgentController` 与 `SessionController`。

## 本地开发

```bash
# 1. 启动后端（项目根目录，端口 8080）
./gradlew bootRun

# 2. 安装依赖并启动前端
cd frontend
rm -rf node_modules package-lock.json   # 若 esbuild 报错，先清理再安装
npm install
npm run dev
```

浏览器访问 http://localhost:5173 ，Vite 会将 `/agent/*` 代理到 `http://localhost:8080`。

## 生产构建

```bash
cd frontend
npm install
npm run build
```

构建产物在 `frontend/dist/`，可独立部署到 Nginx 等静态服务器。

生产环境通过环境变量指定 API 地址：

```bash
VITE_API_BASE_URL=https://api.example.com npm run build
```

## 主要 API

| 接口 | 说明 |
|------|------|
| `GET /agent/chat/stream` | SSE 流式对话 |
| `GET /agent/stop` | 停止生成 |
| `GET /agent/sessions` | 会话列表 |
| `GET /agent/sessions/search?q=` | 搜索历史 |
| `GET /agent/sessions/{conversationId}` | 会话详情 |

## 国际化

支持 `zh-CN` / `en-US`，语言偏好保存在 `localStorage`（key: `genchat-lang`）。
