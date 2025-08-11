# Travel Agent · 逸游

一个用 Java 和 Vue 实现的旅行 AI 练手项目，探索多轮对话、知识检索和工具调用。可以保存旅行信息和聊天记录，刷新页面或重启服务后，在同一浏览器里继续修改计划。

## 现在可以做什么

- **旅行工作台**：历史会话、旅行信息编辑、知识库咨询与 Agent 工具规划，支持停止生成、复制建议和断线恢复。
- **会话持久化**：PostgreSQL 保存完整消息、运行状态、流式事件、摘要、模型用量与工具执行记录。Flyway 自动管理业务表迁移。
- **请求防重**：发送消息通过 POST 创建任务，SSE 只读取已存在的任务。相同会话与 requestId 对应同一任务，即使任务已经结束也不会重复执行。
- **多轮 RAG**：结合历史消解指代，再按知识集和版本检索。页面可展开查看本轮检索片段与来源；模型按编号引用，引用准确性仍需评测。
- **上下文管理**：预留输出空间和安全余量，保留近期完整对话轮次，增量摘要早期轮次。摘要带版本和覆盖消息序号；完整原文一直保存在数据库中。用户维护的旅行信息独立保存。
- **提示词管理**：规划、改写与摘要提示词放在 `resources/prompts`，随 Git 管理。任务记录模型名、提示词内容哈希、知识版本与工具名单。
- **调用控制**：每个浏览器同一时间一个任务、每小时请求限制、单任务 Token 预算、总时限、步数和工具次数上限。默认只允许已配置的网页搜索；MCP 工具需显式列入白名单。
- **用量与过程**：回答、改写、摘要和查询向量化分开记账，记录耗时、流式首段耗时、成功或失败。服务商没有提供用量时明确标为估算。

这是单实例本地学习项目。目前仍没有登录与跨设备同步、可拖拽的结构化行程、地图、工具执行审批和完整评测平台。Agent 模式显示步骤与最终答案；知识库咨询支持答案流式输出。

## 技术栈与目录

Java 21 · Spring Boot 3.5.3 · Spring AI Alibaba 1.0.0-M6.1 · Spring AI 1.0.0-M6 · PostgreSQL / PGVector · Flyway · Vue 3 · Vite

```text
src/main/java/com/sz/aiagent/
  conversation/
    api/              HTTP、浏览器身份、SSE 回放与错误响应
    application/      任务调度、模型执行、提示词读取
    domain/           预算规则、运行配置与业务错误
    infrastructure/   JDBC 持久化
  rag/                文档读取、增量导入、历史检索组件
  tools/              工具实现与显式注册
  config/             模型、跨域、有限容量线程池
  controller/         旧教学 API（默认关闭）
  app/, agent/        旧教学示例与独立 Agent 循环
src/main/resources/
  db/migration/       业务数据库迁移
  prompts/            planner / rewrite / summary 提示词
  document/           内置旅行知识
AI-agent-frontend/    Vue 工作台、API 请求和 SSE 订阅
AI-Agent-MCP/         可选图片搜索 MCP 服务
scripts/dev.sh       本地启动入口
```

## 本地运行

准备 JDK 21、Maven 3.9+、Node.js 20.19+ / 22.12+、Docker Compose。

```bash
cp .env.example .env
# 填写 DASHSCOPE_API_KEY、DB_PASSWORD；网页搜索可另填 SEARCH_API_KEY。
./scripts/dev.sh --import
```

首次使用 `--import` 导入内置知识；后续运行 `./scripts/dev.sh`。前端为 `http://127.0.0.1:5173`，接口文档为 `http://127.0.0.1:9527/api/swagger-ui.html`，后端日志在 `logs/backend.log`。Ctrl+C 停止前后端，数据库卷保留；`docker compose stop` 停止数据库。

已有数据库可设置 `SKIP_DATABASE_START=true` 并填写 `DB_URL`。业务表使用 `ta_` 前缀，Flyway 历史表为 `ta_schema_history`；已有向量表的数据库从基线 0 开始新增业务表，不清空知识。更新前应备份数据库，迁移不应替代备份。

前端默认通过 Vite 代理 `/api`。静态部署需要同源 API 反向代理，SSE 路径关闭代理缓冲。更换开发端口时同步调整 `CORS_ORIGINS`；固定使用 `127.0.0.1` 或 `localhost`，二者的 Cookie 不共享。通过 HTTPS 直接访问后端时身份 Cookie 设置 Secure；代理部署应正确处理可信转发头。

## 保存与恢复

1. 新建旅行，或直接发送第一条消息。
2. 在「旅行信息」填写日期、预算和偏好；这些内容独立于摘要保存。
3. 继续提问，例如“第二天下雨，预算降到 1500，帮我调整”。本轮明确修改优先于旧信息；需要长期保存的更正可同步更新旅行信息。
4. 刷新页面会恢复历史并继续订阅当前任务。发送响应丢失时，点击「重试原请求」使用原 requestId，不重复创建模型任务。

浏览器使用 180 天的随机 HttpOnly Cookie，数据库只保存其 SHA-256 摘要。所有读写检查归属。清除 Cookie 或换浏览器后无法找回原会话；这不是账号认证系统。单实例重启将未完成任务标为 `INTERRUPTED`，保留收到的内容，不自动重新付费生成。旧版本内存里的消息无法迁移。

## 配置与预算

| 环境变量 | 默认值 | 作用 |
| --- | --- | --- |
| `CONTEXT_TOKENS` | 16000 | 模型窗口预算，须不超过所选模型支持范围 |
| `OUTPUT_TOKENS` | 1600 | 单次主要回复输出上限 |
| `RUN_TOKEN_BUDGET` | 50000 | 整个任务累计输入和输出预算 |
| `RUN_TIMEOUT_SECONDS` | 180 | 任务总时限，包含排队 |
| `REQUESTS_PER_HOUR` | 40 | 当前浏览器每小时新任务上限 |
| `MCP_ALLOWED_TOOLS` | 空 | 允许调用的已审核只读 MCP 工具名，逗号分隔 |
| `KNOWLEDGE_VERSION` | v1 | 检索知识版本 |
| `RAG_QUERY_REWRITE` | true | 是否结合上文改写检索问题 |

更细的历史、摘要、检索和工具结果预算位于 `travel.runtime`。预算估算使用保守的 UTF-8 字节数，不等同于模型 Tokenizer；中文长消息可能提前触发限制。工具请求和响应成对保留，超出总预算则停止，不截断成孤立工具消息。摘要失败不推进覆盖序号，失败或取消的回答不作为完整对话注入。

用量优先采用服务商返回值。流式查询向量化目前仅能估算；失败或取消的模型调用按预留输出上限计入估算。取消会阻止后续模型与工具步骤，已发出的请求是否立即停止取决于 SDK；服务商可能继续计费。Token 限制不是人民币账单硬限额，搜索服务费用和后台知识导入费用也尚未合并记账。

知识导入根据内容哈希跳过未变化的片段。**更换嵌入模型或切分规则时应重新建立索引**；改变维度还需迁移向量表。同名知识版本当前允许重新导入，尚不是不可变发布快照。

## 可选 MCP 与权限

`MCP_ENABLED=false` 默认关闭外部 MCP 进程。启用时复制 `config/mcp-servers.example.json` 为已忽略的 `config/mcp-servers.local.json`，填写相应凭据，再配置 `MCP_ALLOWED_TOOLS`。未列入名单的工具不会提供给模型，模型请求未知工具会被拒绝并记入执行记录。

目前只用于自己审核过的只读查询工具，**没有写操作审批流程、完整 JSON Schema 安全网关或子进程沙箱**。当前 SDK 的 stdio 子进程会继承父进程环境；启用第三方程序前需自行提供隔离启动器，只传递所需变量，并固定服务器包版本。工具描述里的“只读”标记不能代替权限审查。

终端、任意文件读写、抓取、下载、PDF 等历史示例没有注册给模型。图片 MCP 模块通过 `PEXELS_API_KEY` 配置，单独构建：`mvn -f AI-Agent-MCP/pom.xml package`。

## API

所有路径以 `/api/v1` 为前缀。写请求使用 JSON，并携带 `X-Travel-Client: web` 和身份 Cookie。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET / POST | `/conversations` | 列表 / 创建旅行 |
| GET / PATCH | `/conversations/{id}` | 读取历史 / 更新旅行信息 |
| POST | `/conversations/{id}/messages` | `{message, requestId}` 创建或返回同一任务 |
| GET | `/runs/{id}` | 状态、用量、工具记录和前 300 条诊断事件 |
| GET | `/runs/{id}/events?after=0` | SSE 订阅或回放，支持 `Last-Event-ID` |
| POST | `/runs/{id}/cancel` | 停止当前浏览器自己的任务 |

事件为 `delta`、`step`、`sources`、`notice`、`final`、`done`；事件序号在数据库中递增。终态为 `FINISHED`、`FAILED`、`CANCELLED`、`INTERRUPTED`。任务 ID 同时关联模型与工具记录，错误响应包含 code、message、retryable 和 traceId。旧 `/api/ai/*` 默认关闭，仅通过 `travel.legacy-api-enabled=true` 恢复教学示例，旧入口不具备新运行时的持久化与预算保障。

## 验证

```bash
mvn test
mvn -f AI-Agent-MCP/pom.xml package
cd AI-agent-frontend
npm ci
npm test
npm run build
```

默认使用 H2 PostgreSQL 兼容模式验证业务表和事务，模型与向量检索使用替身，不调用收费服务。CI 的 `ConversationPersistenceTest` 另外连接 PostgreSQL 16，验证迁移、并发请求防重、终态保护和用量入库。测试覆盖真实 Controller → Service → Engine → JDBC 流程、SSE 续订、跨浏览器隔离、摘要覆盖、预算拒绝与取消后禁止执行工具。

原有真实服务测试标记为 `integration`，配置外部服务后可执行 `mvn -Pintegration test`，会调用真实服务并可能产生费用。离线测试不能证明大模型回答效果、摘要事实保留率或外部 MCP 可用性；这些仍需固定评测集与真实环境回归。

后续重点：结构化行程编辑、MCP 隔离与执行审批、知识发布快照、引用支持率与摘要事实保留评测、可观测与实际费用汇总。当前默认监听本机，不应直接当作具备账号、全局反滥用和多实例协调的公共服务部署。
