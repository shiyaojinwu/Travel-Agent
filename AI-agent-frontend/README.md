# AI 旅游前端项目

这是一个基于 Vue 3 和 Vite 构建的前端项目，包含 AI 旅游大师和 AI 超级智能体两个核心应用，支持与后端进行 SSE 流式对话。

## 项目功能
- **AI 旅游大师**：提供旅游相关的智能问答服务。
- **AI 超级智能体**：提供通用的智能问答服务。
- **SSE 流式对话**：支持与后端进行实时流式对话。

## 项目结构
```plaintext
AI-agent-frontend/
├── .gitignore
├── README.md
├── index.html
├── package-lock.json
├── package.json
├── public/
│   └── vite.svg
├── src/
│   ├── App.vue
│   ├── assets/
│   │   └── vue.svg
│   ├── components/
│   │   └── HelloWorld.vue
│   ├── main.js
│   ├── router/
│   │   └── index.js
│   ├── style.css
│   └── views/
│       ├── AISuperAgent.vue
│       ├── AITravelMaster.vue
│       └── Home.vue
└── vite.config.js
```

## 技术栈
- **框架**：Vue 3
- **路由**：Vue Router 4
- **构建工具**：Vite
- **HTTP 请求**：SSE（Server-Sent Events）

## 运行步骤
1. 安装依赖
```bash
npm install
```
2. 启动开发服务器
```bash
npm run dev
```
3. 打开浏览器访问 `http://localhost:5173/`

## 文档参考
- [Vue 3 文档](https://v3.vuejs.org/)
- [Vue Router 4 文档](https://router.vuejs.org/)
- [Vite 文档](https://vitejs.dev/)
