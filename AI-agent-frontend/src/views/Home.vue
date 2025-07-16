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
  width: 80vw;
  height: 80vh;
  text-align: center;
  padding: 20px;
  background: url('/back.png') center/cover no-repeat;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  box-sizing: border-box;
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
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