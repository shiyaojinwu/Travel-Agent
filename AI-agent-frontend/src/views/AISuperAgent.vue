<template>
  <div class="chat-container">
    <h1>AI 超级智能体</h1>
    <div class="chat-history">
      <div v-for="(msg, index) in messages" :key="index" :class="['message-wrapper', msg.sender]">
        <img v-if="msg.sender === 'user'" src="/public/back.png" alt="用户头像" class="avatar user-avatar">
        <img v-if="msg.sender === 'ai'" src="/src/assets/vue.svg" alt="AI 头像" class="avatar ai-avatar">
        <div class="message">
          {{ msg.content }}
        </div>
      </div>
    </div>
    <div class="input-area">
      <input v-model="inputMessage" @keyup.enter="sendMessage" placeholder="请输入消息...">
      <button @click="sendMessage">发送</button>
    </div>
  </div>
</template>

<script>

// AI 超级智能体页面组件
export default {
  name: 'AISuperAgent',
  data() {
    return {
      inputMessage: '',
      messages: [],
      currentAiResponseIndex: -1,
      eventSource: null
    }
  },
  methods: {
    // 发送消息
    sendMessage() {
      if (!this.inputMessage.trim()) return;
      
      this.messages.push({sender: 'user', content: this.inputMessage});
      this.currentAiResponseIndex = this.messages.length;
      this.messages.push({sender: 'ai', content: ''});
      
      // 关闭之前的 EventSource
      if (this.eventSource) {
        this.eventSource.close();
      }
      
      this.eventSource = new EventSource(`http://8.138.124.114:9527/api/ai/manus/chat?message=${encodeURIComponent(this.inputMessage)}`);
      
      this.eventSource.onmessage = (event) => {
        if (event.data) {
          this.messages[this.currentAiResponseIndex].content += event.data;
        }
      };
      
      this.eventSource.onerror = (error) => {
        console.error('Error receiving SSE data:', error);
        this.eventSource.close();
      };
      
      this.inputMessage = '';
    }
  },
  mounted() {
    // 页面加载时显示欢迎消息
    this.messages.push({sender: 'ai', content: '欢迎使用 AI 超级智能体，请问您有什么问题？'});
  },
  beforeDestroy() {
    // 组件销毁前关闭 EventSource
    if (this.eventSource) {
      this.eventSource.close();
    }
  }
};
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

.message.user {
  background-color: #73a6ff;
  text-align: left;
}

.message.ai {
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