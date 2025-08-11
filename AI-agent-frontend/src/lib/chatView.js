import { markRaw } from 'vue';
import { openChatStream } from './chatStream.js';

export function chatView(name, endpoint, welcome) {
  return {
    name,
    data: () => ({
      inputMessage: '', messages: [{ id: crypto.randomUUID(), sender: 'ai', content: welcome }],
      chatId: crypto.randomUUID(), activeRunId: null, activeStream: null, generating: false, notice: ''
    }),
    methods: {
      sendMessage() {
        const message = this.inputMessage.trim();
        if (!message || this.generating) return;
        this.messages.push({ id: crypto.randomUUID(), sender: 'user', content: message });
        this.messages.push({ id: crypto.randomUUID(), sender: 'ai', content: '', steps: [] });
        const reply = this.messages[this.messages.length - 1]; // Stable reactive object, never a shared index.
        this.generating = true;
        this.notice = '';
        this.inputMessage = '';
        const base = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '');
        const runId = crypto.randomUUID();
        this.activeRunId = runId;
        const query = new URLSearchParams({ message, chatId: this.chatId, runId });
        const finish = () => { this.generating = false; this.activeStream = null; this.activeRunId = null; };
        try {
          this.activeStream = markRaw(openChatStream(`${base}/ai/${endpoint}?${query}`, {
            onDelta: text => { reply.content += text; },
            onStep: text => { reply.steps.push(text); },
            onFinal: text => { reply.content = text; },
            onNotice: text => { this.notice = text; },
            onDone: finish,
            onError: text => { this.notice = text; finish(); }
          }));
        } catch {
          this.notice = '无法建立连接，请检查服务地址。';
          finish();
        }
      },
      stopGeneration() {
        if (this.activeRunId) {
          const base = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '');
          fetch(`${base}/ai/runs/${encodeURIComponent(this.activeRunId)}`, {
            method: 'DELETE', credentials: 'include', keepalive: true
          }).catch(() => {});
          this.activeRunId = null;
        }
        this.activeStream?.close();
        this.activeStream = null;
        this.generating = false;
        this.notice = '已请求停止生成，已保留当前内容。';
      }
    },
    beforeUnmount() { this.stopGeneration(); }
  };
}
