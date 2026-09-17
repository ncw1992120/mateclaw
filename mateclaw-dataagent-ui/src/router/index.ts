import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/views/layout/MainLayout.vue'
import LoginView from '@/views/LoginView.vue'
import DatasetListView from '@/views/dataset/DatasetListView.vue'
import DatasetEditRoute from '@/views/dataset/DatasetEditRoute.vue'
import InsightDashboardEditorView from '@/views/insight/InsightDashboardEditorView.vue'

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
      // 洞察·仪表盘·卡片属性配置（正式入口）
      path: '/insight/dashboard/editor',
      name: 'insight-dashboard-editor',
      component: InsightDashboardEditorView,
      props: (route) => ({ dashboardId: String(route.query.dashboardId ?? '') }),
      meta: { title: '洞察仪表盘' },
    },
    {
      // 洞察·卡片属性配置 免登录预览（仅供交互体验验证，与正式入口同一组件）
      path: '/insight/dashboard/editor-preview',
      name: 'insight-dashboard-editor-preview',
      component: InsightDashboardEditorView,
      props: (route) => ({ dashboardId: String(route.query.dashboardId ?? '') }),
      meta: { public: true, title: '洞察仪表盘预览' },
    },
    {
      // 洞察·组合卡片（卡片容器）前端交互原型（免登录，仅供交互体验验证）
      path: '/insight/card-container-prototype',
      name: 'insight-card-container-prototype',
      component: () => import('@/views/insight/CardContainerPrototype.vue'),
      meta: { public: true, title: '组合卡片原型' },
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
