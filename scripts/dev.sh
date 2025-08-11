#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "已创建 .env，请填写 DASHSCOPE_API_KEY 和 DB_PASSWORD 后重新运行。"
  exit 1
fi
set -a
source .env
set +a
: "${DASHSCOPE_API_KEY:?请在 .env 填写 DASHSCOPE_API_KEY}"
: "${DB_PASSWORD:?请在 .env 填写 DB_PASSWORD}"
for cmd in java mvn npm; do command -v "$cmd" >/dev/null || { echo "缺少命令：$cmd"; exit 1; }; done
if ! java -version 2>&1 | head -1 | grep -q '"21\.'; then
  echo "本项目使用 JDK 21，请设置 JAVA_HOME 并将其 bin 加入 PATH。"; exit 1
fi
if [[ "${SKIP_DATABASE_START:-false}" != true ]]; then
  docker compose up -d --wait postgres
fi
if [[ "${1:-}" == "--import" ]]; then export KNOWLEDGE_IMPORT=true; fi
mkdir -p logs
(cd AI-agent-frontend && npm ci --no-audit --no-fund)
backend_pid=''
cleanup() {
  if [[ -n "$backend_pid" ]]; then
    pkill -TERM -P "$backend_pid" 2>/dev/null || true
    kill "$backend_pid" 2>/dev/null || true
    wait "$backend_pid" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM
mvn -B spring-boot:run >logs/backend.log 2>&1 &
backend_pid=$!
echo "等待后端启动（日志：logs/backend.log）..."
ready=false
for ((i=0; i<120; i++)); do
  if ! kill -0 "$backend_pid" 2>/dev/null; then echo "后端启动失败，请查看 logs/backend.log"; exit 1; fi
  if curl -fsS "http://127.0.0.1:${SERVER_PORT:-9527}/api/v3/api-docs" >/dev/null 2>&1; then ready=true; break; fi
  sleep 1
done
if [[ "$ready" != true ]]; then echo "后端尚未就绪，请查看 logs/backend.log"; exit 1; fi
export API_PROXY_TARGET="http://127.0.0.1:${SERVER_PORT:-9527}"
echo "后端已就绪，启动前端。按 Ctrl+C 停止前后端；数据库数据会保留。"
cd AI-agent-frontend
npm run dev -- --host 127.0.0.1
