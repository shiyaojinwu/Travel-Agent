export const apiBase = (import.meta.env?.VITE_API_BASE_URL || "/api").replace(
  /\/$/,
  "",
);
export async function request(
  path,
  options = {},
  fetchImpl = globalThis.fetch,
) {
  const response = await fetchImpl(`${apiBase}/v1${path}`, {
    credentials: "include",
    ...options,
    headers: {
      "Content-Type": "application/json",
      "X-Travel-Client": "web",
      ...options.headers,
    },
  });
  let body;
  try {
    body = await response.json();
  } catch {
    body = {};
  }
  if (!response.ok)
    throw Object.assign(
      new Error(body.message || "服务暂时不可用，请稍后重试。"),
      { code: body.code, status: response.status },
    );
  return body;
}
/** Only observes a persisted run; reconnecting never creates a model request. */
export function observeRun(
  runId,
  after,
  handlers,
  EventSourceImpl = globalThis.EventSource,
) {
  const source = new EventSourceImpl(
    `${apiBase}/v1/runs/${encodeURIComponent(runId)}/events?after=${after}`,
    { withCredentials: true },
  );
  let closed = false,
    last = after,
    errors = 0;
  const close = () => {
    closed = true;
    source.close();
  };
  for (const type of ["delta", "step", "final", "notice", "sources", "done"]) {
    source.addEventListener(type, (event) => {
      if (closed) return;
      const seq = Number(event.lastEventId || 0);
      if (seq && seq <= last && type !== "done") return;
      try {
        const data = JSON.parse(event.data);
        if (seq) last = Math.max(last, seq);
        handlers.event(type, data, last);
        if (type === "done") close();
      } catch {
        close();
        handlers.error("服务返回数据异常，请重新打开此会话恢复。");
      }
    });
  }
  source.onopen = () => {
    errors = 0;
    handlers.connected?.();
  };
  source.onerror = () => {
    if (closed) return;
    errors++;
    if (source.readyState === 2 || errors >= 5) {
      close();
      handlers.error("连接中断。点击恢复连接，已保存的任务不会重复执行。");
    } else handlers.reconnecting?.();
  };
  return { close };
}
export const terminalReason = (code) =>
  ({
    CANCELLED: "已停止，收到的内容已保存。",
    SERVER_RESTART: "服务重启，本次生成已中断；历史消息已保留。",
    SERVER_SHUTDOWN: "服务关闭，本次生成已中断。",
    TOKEN_BUDGET: "达到本次 Token 预算，请缩小问题范围。",
    CONTEXT_BUDGET: "内容超出上下文预算，请缩短消息或新建会话。",
    STEP_LIMIT: "达到执行步数上限。",
    TOOL_BUDGET: "达到工具调用上限。",
    DEADLINE_EXCEEDED: "任务超时，已停止生成。",
    SERVER_BUSY: "服务繁忙，请稍后重试。",
    PROVIDER_ERROR: "模型或检索服务调用失败，请检查服务配置。",
    TOOL_DENIED: "工具未获授权，已停止执行。",
    SUMMARY_BUDGET: "早期记忆压缩超出预算，原始对话已保留。",
  })[code] || (code ? `生成未完成（${code}）。` : "");
