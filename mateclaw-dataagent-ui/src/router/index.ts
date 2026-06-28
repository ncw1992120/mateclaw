import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/views/layout/MainLayout.vue'
import LoginView from '@/views/LoginView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { public: true },
    },
    {
      path: '/',
      component: MainLayout,
    },
  ],
})

/**
 * 全局前置守卫
 * 未登录时重定向到登录页；已登录访问登录页时重定向到首页
 */
router.beforeEach((to) => {
  const token = localStorage.getItem('token')

  if (to.meta.public) {
    // 已登录用户访问登录页 → 跳转首页
    if (token && to.name === 'login') {
      return { path: '/' }
    }
    return true
  }

  // 受保护页面未登录 → 跳转登录页
  if (!token) {
    return { path: '/login' }
  }

  return true
})

export default router
