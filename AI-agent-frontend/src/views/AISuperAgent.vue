<template>
  <div class="chat-container">
    <h1>AI 超级智能体</h1>
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
      
      this.eventSource = new EventSource(`http://localhost:9527/api/ai/manus/chat?message=${encodeURIComponent(this.inputMessage)}`);
      
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