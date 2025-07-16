import { createRouter, createWebHistory } from 'vue-router';
import Home from '../views/Home.vue';
import AITravelMaster from '../views/AITravelMaster.vue';
import AISuperAgent from '../views/AISuperAgent.vue';

// 创建路由实例
const routes = [
  {
    path: '/',
    name: 'Home',
    component: Home
  },
  {
    path: '/ai-travel-master',
    name: 'AITravelMaster',
    component: AITravelMaster
  },
  {
    path: '/ai-super-agent',
    name: 'AISuperAgent',
    component: AISuperAgent
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

// 导出路由实例
export default router;