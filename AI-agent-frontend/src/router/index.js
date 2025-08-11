import { createRouter, createWebHistory } from "vue-router";
import TravelWorkspace from "../views/TravelWorkspace.vue";
export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", component: TravelWorkspace },
    { path: "/ai-travel-master", redirect: "/" },
    { path: "/ai-super-agent", redirect: "/" },
  ],
});
