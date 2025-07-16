<template>
  <div class="chat-container">
    <h1>AI 旅游大师</h1>
    <div class="chat-history">
      <div v-for="(msg, index) in messages" :key="index" :class="['message', msg.sender]">
        {{ msg.content }}
      </div>
    </div>
    <div class="input-area">
      <input v-model="inputMessage" @keyup.enter="sendMessage" placeholder="请输入消息...">
      <button @click="sendMessage">发送</button>
    </div>
  </div>
</template>

<script>
import axios from 'axios';

// AI 旅游大师页面组件
export default {
  name: 'AITravelMaster',
  data() {
    return {
      inputMessage: '',
      messages: [],
      currentAiResponseIndex: -1
    }
  },
  methods: {
    // 发送消息
    sendMessage() {
      if (!this.inputMessage.trim()) return;
      
      this.messages.push({
        sender: 'user',
        content: this.inputMessage
      });
      
      this.currentAiResponseIndex = this.messages.length;
      this.messages.push({
        sender: 'ai',
        content: ''
      });
      
      const url = 'http://localhost:9527/api/ai/love_app/chat/sse_emitter';
      const eventSource = new EventSource(`${url}?message=${encodeURIComponent(this.inputMessage)}&chatId=${this.generateChatId()}`);
      
      eventSource.onmessage = (event) => {
        if (event.data) {
          this.messages[this.currentAiResponseIndex].content += event.data;
        }
      };
      
      eventSource.onerror = () => {
        eventSource.close();
      };
      
      this.inputMessage = '';
    },
    // 生成聊天室 ID
    generateChatId() {
      return 'chat-' + Date.now();
    }
  },
  mounted() {
    // 页面加载时显示欢迎消息
    this.messages.push({
      sender: 'ai',
      content: '欢迎使用 AI 旅游大师，请问您有什么旅游需求？'
    });
  }
};
</script>

<style scoped>
.chat-container {
  width: min(80vw, 80vh);
  height: min(80vw, 80vh);
  padding: 20px;
  background: url('/back.png') center/cover no-repeat;
  box-sizing: border-box;
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

@media (min-width: 100vh), (min-height: 100vw) {
  .chat-container {
    width: 50vw;
    height: 50vw;
  }
}

.chat-history {
  width: 100%;
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 10px;
  margin-bottom: 10px;
  min-height: 400px;
  max-height: 600px;
  overflow-y: auto;
  background-color: rgba(255, 255, 255, 0.8);
}

.message {
  margin: 10px;
  padding: 10px;
  border-radius: 4px;
  width: 100%;
}

.message.user {
  background-color: #dcf8c6;
  margin-left: auto;
}

.message.ai {
  background-color: #ece5dd;
  margin-right: auto;
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