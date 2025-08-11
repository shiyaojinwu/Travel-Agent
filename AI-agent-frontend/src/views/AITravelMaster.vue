<template>
  <div class="chat-container">
    <h1>AI 旅游大师</h1>
    <div class="chat-history">
      <div v-for="msg in messages" :key="msg.id" :class="['message-wrapper', msg.sender]">
        <img v-if="msg.sender === 'user'" src="/back.png" alt="用户头像" class="avatar user-avatar">
        <img v-if="msg.sender === 'ai'" src="/src/assets/vue.svg" alt="AI 头像" class="avatar ai-avatar">
        <div class="message">
          <details v-if="msg.steps?.length"><summary>查看执行过程（{{ msg.steps.length }} 步）</summary><p v-for="(step, i) in msg.steps" :key="i">{{ step }}</p></details>
          {{ msg.content || (generating && msg.sender === 'ai' ? '正在生成…' : '') }}
        </div>
      </div>
    </div>
    <p v-if="notice" class="stream-notice" role="status">{{ notice }}</p>
    <div class="input-area">
      <input v-model="inputMessage" @keyup.enter="sendMessage" placeholder="请输入消息..." aria-label="消息内容" maxlength="8000" :disabled="generating">
      <button v-if="generating" @click="stopGeneration">停止生成</button>
      <button v-else @click="sendMessage" :disabled="!inputMessage.trim()">发送</button>
    </div>
  </div>
</template>

<script>
import { chatView } from '../lib/chatView.js';
export default chatView('AITravelMaster', 'chat/sse_emitter', '欢迎使用 AI 旅游大师，请告诉我目的地、日期和预算。');
</script>

<style scoped>
.chat-container {
  width: 100%;
  height: 100%;
  padding: 20px;
  box-sizing: border-box;
  background: rgba(255, 255, 255, 0.8);
  display: flex;
  flex-direction: column;
}

.chat-history {
  width: 100%;
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 10px;
  margin-bottom: 10px;
  flex-grow: 1;
  overflow-y: auto;
  background-color: rgba(255, 255, 255, 0.95);
}

.message-wrapper {
  display: flex;
  align-items: flex-start;
  margin: 10px;
}

.message-wrapper.user {
  flex-direction: row-reverse;
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  margin: 0 10px;
  align-self: flex-start;
}

.message {
  padding: 10px;
  border-radius: 4px;
  max-width: 60%;
}
.message-wrapper.user .message {
  background-color: #73a6ff;
  text-align: right;
}
.message-wrapper.user .message {
  background-color: #73a6ff;
  text-align: right;
}
.message-wrapper.ai .message {
  background-color: #ff9999;
  text-align: left;
}

.input-area {
  display: flex;
  gap: 10px;
}

.input-area input {
  flex: 1;
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 4px;
}

.input-area button {
  padding: 10px 20px;
  background-color: #42b983;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.3s;
}

.input-area button:hover {
  background-color: #33a06f;
}
</style>
<style scoped>
.stream-notice { color: #b45309; margin: 0.5rem 0; }
.chat-container { color: #183348; max-width: min(900px, calc(100vw - 48px)); }
.chat-container h1 { font-size: clamp(1.5rem, 4vw, 2.2rem); }
.message { color: #183348; white-space: pre-wrap; overflow-wrap: anywhere; text-align: left; max-width: 80%; }
.input-area input { min-width: 0; color: #183348; background: white; color-scheme: light; }
.message-wrapper.ai .message { background: #edf7f3; }
.message-wrapper.user .message { background: #dbeafe; }
@media (max-width: 600px) { .avatar { width: 28px; height: 28px; margin: 0 5px; } .message { max-width: 85%; } }
details { font-size: 0.85rem; opacity: 0.8; }
button:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
