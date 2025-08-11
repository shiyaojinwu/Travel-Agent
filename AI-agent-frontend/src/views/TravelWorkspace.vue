<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from "vue";
import { request, observeRun, terminalReason } from "../lib/travelApi.js";
const conversations = ref([]),
  current = ref(null),
  messages = ref([]),
  input = ref(""),
  mode = ref("CHAT");
const title = ref(""),
  constraints = ref(""),
  editing = ref(false),
  notice = ref(""),
  loading = ref(false);
const active = ref(null),
  steps = ref([]),
  sources = ref([]),
  usage = ref([]),
  historyEl = ref(null),
  disconnected = ref(false);
const metrics = computed(() =>
  usage.value.reduce(
    (a, x) => ({
      total: a.total + x.input_tokens + x.output_tokens,
      estimated: a.estimated || x.estimated,
    }),
    { total: 0, estimated: false },
  ),
);
let connection,
  epoch = 0;
const key = "travel-pending-request";
const pending = ref(null);
try {
  pending.value = JSON.parse(sessionStorage.getItem(key) || "null");
} catch {
  sessionStorage.removeItem(key);
}
const busy = computed(() => loading.value || !!active.value);
const choices = [
  "深圳周末两日游，人均预算 1500 元",
  "带父母去成都，节奏轻松一点",
  "第二天下雨，帮我调整室内安排",
];
function scroll() {
  nextTick(() => {
    if (historyEl.value)
      historyEl.value.scrollTop = historyEl.value.scrollHeight;
  });
}
async function list() {
  conversations.value = await request("/conversations");
}
function close() {
  connection?.close();
  connection = null;
}
function savePending(value) {
  pending.value = value;
  if (value) sessionStorage.setItem(key, JSON.stringify(value));
  else sessionStorage.removeItem(key);
}
async function diagnostics(runId, token) {
  try {
    const data = await request(`/runs/${runId}`);
    if (token !== epoch) return;
    usage.value = data.usage;
    steps.value = data.events
      .filter((x) => x.type === "step")
      .map((x) => x.data.content);
    sources.value =
      data.events.find((x) => x.type === "sources")?.data.items || [];
  } catch {
    /* Conversation is still usable if diagnostics are temporarily unavailable. */
  }
}
async function select(id) {
  const token = ++epoch;
  close();
  loading.value = true;
  disconnected.value = false;
  notice.value = "";
  steps.value = [];
  sources.value = [];
  usage.value = [];
  active.value = null;
  try {
    const data = await request(`/conversations/${id}`);
    if (token !== epoch) return;
    current.value = data.conversation;
    title.value = current.value.title;
    constraints.value = current.value.constraints;
    messages.value = data.messages.map((x) => ({ ...x }));
    editing.value = false;
    localStorage.setItem("travel-last-conversation", id);
    const run = data.runs.at(-1);
    if (run) {
      if (["QUEUED", "RUNNING"].includes(run.status)) {
        active.value = run;
        subscribe(run, token);
      } else {
        notice.value = terminalReason(run.errorCode);
        diagnostics(run.id, token);
      }
    }
    if (pending.value?.conversationId === id)
      notice.value =
        "有一次发送尚未确认。点击重试原请求，系统会检查是否已经执行。";
    scroll();
  } catch (error) {
    if (token === epoch) notice.value = error.message;
  } finally {
    if (token === epoch) loading.value = false;
  }
}
function subscribe(run, token) {
  let reply = messages.value.find(
    (x) => x.runId === run.id && x.role === "assistant",
  );
  if (!reply) {
    messages.value.push({
      seq: run.id,
      runId: run.id,
      role: "assistant",
      content: "",
      status: "RUNNING",
    });
    reply = messages.value.at(-1);
  }
  reply.content = "";
  steps.value = [];
  sources.value = [];
  connection = observeRun(run.id, 0, {
    event(type, data) {
      if (token !== epoch) return;
      if (type === "delta") reply.content += data.content;
      if (type === "final") reply.content = data.content;
      if (type === "step") steps.value.push(data.content);
      if (type === "sources") sources.value = data.items;
      if (type === "notice") notice.value = data.content;
      if (type === "done") {
        active.value = null;
        reply.status = data.state;
        notice.value = terminalReason(data.errorCode);
        diagnostics(run.id, token);
        list().catch(() => {});
      }
      scroll();
    },
    connected() {
      if (token === epoch) {
        disconnected.value = false;
        if (notice.value === "连接恢复中…") notice.value = "";
      }
    },
    reconnecting() {
      if (token === epoch) notice.value = "连接恢复中…";
    },
    error(text) {
      if (token === epoch) {
        disconnected.value = true;
        notice.value = text;
      }
    },
  });
}
async function create() {
  if (busy.value) return;
  loading.value = true;
  notice.value = "";
  try {
    const item = await request("/conversations", {
      method: "POST",
      body: JSON.stringify({
        title: "新的旅行",
        mode: mode.value,
        constraints: "",
      }),
    });
    await list();
    await select(item.id);
  } catch (e) {
    notice.value = e.message;
  } finally {
    loading.value = false;
  }
}
async function send(retry = false) {
  if (busy.value || (!retry && !input.value.trim())) return;
  if (pending.value && !retry) {
    notice.value = "请先重试尚未确认的请求，避免重复发送。";
    return;
  }
  loading.value = true;
  notice.value = "";
  try {
    if (!current.value) {
      const item = await request("/conversations", {
        method: "POST",
        body: JSON.stringify({
          title: input.value.trim().slice(0, 40),
          mode: mode.value,
          constraints: "",
        }),
      });
      current.value = item;
      await list();
    }
    const payload = retry
      ? pending.value
      : {
          conversationId: current.value.id,
          message: input.value.trim(),
          requestId: crypto.randomUUID(),
        };
    if (!payload) return;
    savePending(payload);
    await request(`/conversations/${payload.conversationId}/messages`, {
      method: "POST",
      body: JSON.stringify(payload),
    });
    savePending(null);
    input.value = "";
    await select(payload.conversationId);
    await list();
  } catch (error) {
    notice.value = error.message;
    // A definite validation/ownership rejection did not create a paid request.
    if ([400, 403, 404, 409, 429].includes(error.status)) savePending(null);
  } finally {
    loading.value = false;
  }
}
async function stop() {
  const id = active.value?.id;
  if (!id) return;
  try {
    await request(`/runs/${id}/cancel`, { method: "POST" });
    await select(current.value.id);
  } catch (e) {
    notice.value = e.message;
  }
}
async function save() {
  if (busy.value) return;
  loading.value = true;
  try {
    current.value = await request(`/conversations/${current.value.id}`, {
      method: "PATCH",
      body: JSON.stringify({
        title: title.value,
        constraints: constraints.value,
      }),
    });
    editing.value = false;
    await list();
    notice.value = "旅行信息已保存，下次继续聊天时会带入。";
  } catch (e) {
    notice.value = e.message;
  } finally {
    loading.value = false;
  }
}
async function copy(text) {
  try {
    await navigator.clipboard.writeText(text);
    notice.value = "已复制旅行建议。";
  } catch {
    notice.value = "复制未成功，可以直接选中文字复制。";
  }
}
onMounted(async () => {
  try {
    await list();
    const id =
      pending.value?.conversationId ||
      localStorage.getItem("travel-last-conversation");
    if (conversations.value.some((x) => x.id === id)) await select(id);
  } catch (e) {
    notice.value = "暂时连不上服务。请确认后端和数据库已启动，再点击重新连接。";
    disconnected.value = true;
  }
});
onBeforeUnmount(() => {
  epoch++;
  close();
});
async function reconnect() {
  if (current.value) await select(current.value.id);
  else {
    try {
      await list();
      disconnected.value = false;
      notice.value = "";
    } catch (e) {
      notice.value = e.message;
    }
  }
}
</script>

<template>
  <div class="workspace">
    <aside class="sidebar">
      <a href="/" class="brand"
        ><span class="brand-mark">逸</span
        ><span>逸游 <small>TRAVEL AGENT</small></span></a
      >
      <div class="new-trip">
        <label for="mode">开始一段旅行</label
        ><select id="mode" v-model="mode" :disabled="busy" aria-label="会话模式">
          <option value="CHAT">旅行咨询 · 知识库</option>
          <option value="AGENT">工具规划 · Agent</option></select
        ><button class="primary" @click="create" :disabled="busy">
          ＋ 新建旅行
        </button>
      </div>
      <div class="history-heading">
        我的旅行 <span>{{ conversations.length }}</span>
      </div>
      <nav aria-label="历史会话">
        <button
          v-for="item in conversations"
          :key="item.id"
          :class="['trip', { selected: current?.id === item.id }]"
          :disabled="loading"
          @click="select(item.id)"
        >
          <span>{{ item.title }}</span
          ><small
            >{{ item.mode === "AGENT" ? "工具规划" : "旅行咨询" }} ·
            {{ new Date(item.updatedAt).toLocaleDateString("zh-CN") }}</small
          >
        </button>
        <p v-if="!conversations.length" class="muted empty-history">
          你的旅行会保存在这里
        </p>
      </nav>
      <p class="sidebar-foot">
        同一浏览器可继续访问<br />清除 Cookie 后无法找回这些会话
      </p>
    </aside>
    <main class="main-panel">
      <header>
        <div>
          <p class="eyebrow">A LITTLE PLAN, A BIG WORLD</p>
          <h1>{{ current?.title || "下一站，想去哪里？" }}</h1>
        </div>
        <button
          v-if="current"
          class="quiet"
          :disabled="busy"
          @click="editing = !editing"
        >
          {{ editing ? "收起" : "旅行信息" }}
        </button>
      </header>
      <form v-if="editing" class="trip-editor" @submit.prevent="save">
        <label>旅行名称<input v-model="title" maxlength="120" required /></label
        ><label
          >日期、预算与偏好<textarea
            v-model="constraints"
            maxlength="2000"
            rows="3"
            placeholder="例如：深圳，周六到周日，两人，人均 1500 元。不自驾，少走路。"
          />
        </label>
        <p class="muted">
          这些信息会一直保留；聊天中提出的新修改优先。生成结束后可在这里更新。
        </p>
        <button class="primary" :disabled="busy">保存旅行信息</button>
      </form>
      <div ref="historyEl" class="messages" aria-label="旅行对话">
        <div v-if="!messages.length" class="welcome">
          <span class="compass">✧</span>
          <h2>把想法变成一段旅程</h2>
          <p>告诉我目的地、时间和预算。<br />保存计划，下次回来接着聊。</p>
          <div class="suggestions">
            <button
              v-for="choice in choices"
              :key="choice"
              @click="input = choice"
            >
              {{ choice }} <span>↗</span>
            </button>
          </div>
        </div>
        <article
          v-for="message in messages"
          :key="message.seq"
          :class="['bubble', message.role]"
        >
          <div class="speaker">
            {{ message.role === "user" ? "你" : "逸游" }}
            <span
              v-if="
                message.status &&
                ['CANCELLED', 'FAILED', 'INTERRUPTED'].includes(message.status)
              "
              >· 未完成</span
            >
          </div>
          <p>
            {{
              message.content ||
              (active ? "正在整理旅行建议…" : "本次没有生成内容")
            }}
          </p>
          <button
            v-if="message.role === 'assistant' && message.content"
            class="copy"
            @click="copy(message.content)"
          >
            复制建议
          </button>
        </article>
        <details v-if="steps.length" class="process">
          <summary>
            {{ active ? steps.at(-1) : "查看本次执行过程" }} ·
            {{ steps.length }} 项
          </summary>
          <ol>
            <li v-for="(step, i) in steps" :key="i">{{ step }}</li>
          </ol>
        </details>
        <details v-if="sources.length" class="process">
          <summary>参考资料 · {{ sources.length }} 条</summary>
          <div v-for="source in sources" :key="source.id" class="source">
            <b>[{{ source.id }}] {{ source.source }}</b
            ><small>知识版本 {{ source.version }}</small>
            <p>{{ source.excerpt }}</p>
          </div>
        </details>
        <details v-if="usage.length" class="process">
          <summary>
            本次用量 · {{ metrics.total.toLocaleString() }} Token{{
              metrics.estimated ? "（含估算）" : ""
            }}
          </summary>
          <p class="muted">
            含回答、改写、摘要和检索。估算不是服务商账单；失败调用会保守预留输出用量。
          </p>
          <table>
            <thead>
              <tr>
                <th>环节</th>
                <th>输入 / 输出</th>
                <th>耗时</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, i) in usage" :key="i">
                <td>{{ row.purpose }}</td>
                <td>
                  {{ row.input_tokens }} / {{ row.output_tokens }}
                  {{ row.estimated ? "≈" : "" }}
                </td>
                <td>{{ (row.latency_ms / 1000).toFixed(1) }} s</td>
              </tr>
            </tbody>
          </table>
        </details>
      </div>
      <footer class="composer">
        <div v-if="notice" class="notice" role="status">
          {{ notice }}
          <button v-if="disconnected" @click="reconnect">恢复连接</button>
        </div>
        <div v-if="pending" class="notice">
          有一次发送尚未确认。<button :disabled="busy" @click="send(true)">
            重试原请求
          </button>
        </div>
        <form @submit.prevent="send(false)">
          <textarea
            v-model="input"
            maxlength="8000"
            rows="2"
            :disabled="busy || !!pending"
            placeholder="例如：第二天下雨，预算降到 1500，帮我调整一下…"
            aria-label="旅行消息"
            @keydown.enter.exact.prevent="!$event.isComposing && send(false)"
          /><button v-if="active" type="button" class="stop" @click="stop">
            停止</button
          ><button
            v-else
            class="primary"
            :disabled="loading || !input.trim() || !!pending"
          >
            {{ loading ? "保存中…" : "发送 ↗" }}
          </button>
        </form>
        <div class="composer-hint">
          <span
            >{{ current?.mode === "AGENT" ? "工具规划" : "旅行咨询" }} · Enter
            发送，Shift + Enter 换行</span
          ><span>实时信息请以官方发布为准</span>
        </div>
      </footer>
    </main>
  </div>
</template>
