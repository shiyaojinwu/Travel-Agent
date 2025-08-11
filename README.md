# Travel Agent · 逸游

一个用 Java 和 Vue 实现的旅游 AI 练手项目，探索多轮问答、知识检索和工具调用。后端使用 Spring Boot、Spring AI Alibaba 与通义千问，知识库使用 PostgreSQL + PGVector，前端通过 SSE 展示回复。

## 已有能力

- **旅游问答**：基于同一浏览器会话保留近期上下文，检索内置 Markdown 旅行知识。
- **多轮检索**：结合历史消息将“那边怎么过去”等追问转为独立查询，再按知识集和版本检索。
- **工具调用**：模型决定是否搜索；工具结果回填后继续回答。直接回答、失败、主动结束和步数耗尽使用不同状态。
- **流式交互**：逐段显示回复，工具过程折叠展示；支持停止生成、离开页面清理、异常提示。
- **知识导入**：显式开启导入，同一片段使用稳定 ID；内容不变时跳过向量化，同一版本删掉的片段会同步移除。其他版本和外部数据不受影响。

这是本地学习演示项目。完整会话持久化、登录账号、结构化可编辑行程、地图、引用回查和固定问答评测集仍在规划中。当前普通聊天记忆在服务重启后丢失，智能体每次请求独立运行；PGVector 中的知识和 Docker 数据卷会持久保存。

## 技术栈与目录

Java 21 · Spring Boot 3.5.3 · Spring AI Alibaba 1.0.0-M6.1 · Spring AI 1.0.0-M6 · PostgreSQL / PGVector · Vue 3 · Vite

```text
src/main/java/com/sz/aiagent/
  controller/       HTTP / SSE 入口和参数校验
  app/              旅游对话入口
  agent/            推理、工具执行与运行状态
  service/          请求取消和会话归属
  config/           模型、跨域、执行线程池
  rag/              文档读取、增量导入、检索与查询改写
  tools/            工具实现与显式注册
src/main/resources/document/   内置旅行知识
src/test/                      无外部调用的回归测试及可选集成测试
AI-agent-frontend/             Vue 页面与流式连接管理
AI-Agent-MCP/                  可选的图片搜索 MCP 服务
config/                        MCP 配置示例
scripts/dev.sh                 本地启动入口
```

## 本地运行

准备 JDK 21、Maven 3.9+、Node.js 20.19+ / 22.12+、Docker Compose，并确保 `java -version` 指向 JDK 21。

```bash
cp .env.example .env
# 填写 DASHSCOPE_API_KEY、DB_PASSWORD；搜索功能可另填 SEARCH_API_KEY。
./scripts/dev.sh --import
```

首次用 `--import` 将内置文档导入 PGVector；后续运行 `./scripts/dev.sh` 即可。更新同一知识版本的文件后再次使用 `--import`，只同步变化的片段。若改用新的 `KNOWLEDGE_VERSION`，先导入该版本；查询会过滤到当前配置的版本。

脚本启动 PostgreSQL，安装前端依赖，启动后端和 Vite。前端通常是 `http://127.0.0.1:5173`，接口文档在 `http://127.0.0.1:9527/api/swagger-ui.html`，后端日志在 `logs/backend.log`。按 Ctrl+C 停止前后端，数据库保留；使用 `docker compose stop` 可停止数据库。

已有数据库时设置 `SKIP_DATABASE_START=true` 并填写 `DB_URL`。嵌入模型维度须与 `EMBEDDING_DIMENSIONS` 及现有向量表一致；更换不同维度的模型需要单独迁移向量表。`.env` 由启动脚本加载，直接运行 Maven 时应先自行导出环境变量。

前端默认请求同源 `/api`，开发时由 Vite 转发到后端。部署静态页面时也需要配置 `/api` 反向代理；仅把 `dist` 放到静态服务器不能提供后端 API。

## 可选 MCP

默认 `MCP_ENABLED=false`，启动不会执行外部 MCP 程序。若需要高德工具：

1. 将 `config/mcp-servers.example.json` 复制为已忽略的 `config/mcp-servers.local.json`。
2. 在 `.env` 填写 `AMAP_MAPS_API_KEY`，设置 `MCP_ENABLED=true`。示例通过子进程继承环境变量，不把密钥写入 JSON。
3. 按实际环境验证第三方 MCP 服务及其工具权限；启用后，模型可以调用它暴露的工具。

图片 MCP 模块单独构建：`mvn -f AI-Agent-MCP/pom.xml package`。通过 `PEXELS_API_KEY` 配置凭据，并按需将启动命令加入本地 MCP 配置。

## 测试

```bash
mvn test
cd AI-agent-frontend
npm ci
npm test
npm run build
```

默认后端测试模拟模型和工具，不调用收费模型、不启动 MCP，也不连接数据库。覆盖直接回答、工具后回答、异常、终止、步数边界、取消、未注册工具、多轮查询改写、知识版本过滤与片段标识。

原来的真实服务测试标记为 `integration`，配置外部服务后可手动执行 `mvn -Pintegration test`，可能消耗模型额度。离线测试与前端构建不能替代真实模型效果、外部 MCP、数据库迁移和网络断连的部署验证。

## 接口与边界

主要 SSE 接口：`GET /api/ai/chat/sse_emitter?message=...&chatId=...&runId=...`、`GET /api/ai/manus/chat?message=...&runId=...`。`chatId` 和 `runId` 为客户端生成的随机 UUID。浏览器携带会话 Cookie；取消接口 `DELETE /api/ai/runs/{runId}` 只作用于同一会话的请求。

事件包括 `delta`（分片）、`step`（工具结果）、`final`（最终答案）、`limit`（耗尽步数）、`failure`（失败）、`done`（结束）。内容事件是 JSON `{ "content": "..." }`。正常完成后客户端关闭连接，不自动重试模型请求。

取消会关闭订阅并中断 Agent 任务，阻止后续步骤；已经发出的外部请求能否立即停止取决于 SDK/工具对中断的支持。搜索工具有 15 秒超时，Agent 连接有 3 分钟总时限，执行线程池为 4 个线程、16 个排队位置。

默认仅监听 `127.0.0.1`，没有公开服务所需的账号认证和限流。终端、任意文件读写、网页抓取、下载、PDF 等历史工具保留为学习代码，未注册给模型。当前回答不提供完整的结构化引用回查，也不能当成实时票价或营业信息的保证。

代码中的凭据已改为环境变量；旧 Git 历史里的凭据不会因此消失，仍有效的旧密钥应在服务商处轮换。
