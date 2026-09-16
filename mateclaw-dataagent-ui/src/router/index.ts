import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/views/layout/MainLayout.vue'
import LoginView from '@/views/LoginView.vue'
import DatasetListView from '@/views/dataset/DatasetListView.vue'
import DatasetEditRoute from '@/views/dataset/DatasetEditRoute.vue'

const router = createRouter({
  // 与 vite base（/dataagent/api/）保持一致：路由路径在部署基座下解析
  history: createWebHistory(import.meta.env.BASE_URL),
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
    {
      path: '/datasets',
      name: 'dataset-list',
      component: DatasetListView,
    },
    {
      path: '/datasets/new',
      name: 'dataset-create',
      component: DatasetEditRoute,
      props: { mode: 'config' },
    },
    {
      path: '/datasets/:id/edit',
      name: 'dataset-edit',
      component: DatasetEditRoute,
      props: (route) => ({ datasetId: route.params.id, mode: 'preview' }),
    },
    {
      // 洞察·仪表盘·卡片属性配置 原型（已对接 dataagent 后端，独立于真实编辑器，便于验证原型交互）
      path: '/insight-prototype',
      name: 'insight-prototype',
      component: () => import('@/views/insight/prototype/InsightPrototypeDashboard.vue'),
      meta: { public: true, title: '洞察原型' },
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
