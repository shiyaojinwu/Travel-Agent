# 逸游旅行工作台

Vue 3 + Vite。统一入口提供历史会话、旅行信息编辑、知识库咨询和 Agent 规划。

```bash
npm ci
npm run dev
```

默认 `/api` 代理到 `http://127.0.0.1:9527`；可用 `API_PROXY_TARGET` 调整。更换前端端口时同步调整后端 `CORS_ORIGINS`。所有写操作通过 `/api/v1`，SSE 仅订阅已有任务，断线恢复不会创建新模型请求。

`npm test` 验证流式连接去重、取消、恢复、终态和请求约定；`npm run build` 生成 `dist`。部署时还需要后端与同源 `/api` 反向代理。运行说明、存储边界和配置参见根目录 README。
