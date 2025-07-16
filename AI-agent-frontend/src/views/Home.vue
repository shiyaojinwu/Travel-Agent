<template>
  <div class="home">
    <h1>应用切换</h1>
    <div class="app-links">
      <router-link to="/ai-travel-master" class="app-link">AI 旅游大师</router-link>
      <router-link to="/ai-super-agent" class="app-link">AI 超级智能体</router-link>
    </div>
  </div>
</template>

<script>
// 主页组件
export default {
  name: 'Home'
};
</script>

<style scoped>
.home {
  width: 100%;
  height: 100%;
  text-align: center;
  padding: 20px;
  background: rgba(255, 255, 255, 0.8);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  box-sizing: border-box;
}

.app-links {
  display: flex;
  gap: 20px;
  margin-top: 20px;
}

.app-link {
  padding: 10px 20px;
  background-color: #42b983;
  color: white;
  text-decoration: none;
  border-radius: 4px;
  transition: background-color 0.3s;
}

.app-link:hover {
  background-color: #33a06f;
}
</style>