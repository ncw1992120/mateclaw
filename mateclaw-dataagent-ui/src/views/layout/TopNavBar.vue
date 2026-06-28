<template>
  <header class="top-nav">
    <div class="nav-left">
      <div class="brand-area">
        <div class="logo">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="32" height="32">
            <defs>
              <linearGradient id="logoGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" style="stop-color:#f97316;stop-opacity:1" />
                <stop offset="100%" style="stop-color:#ea580c;stop-opacity:1" />
              </linearGradient>
            </defs>
            <rect width="100" height="100" rx="15" fill="url(#logoGrad)"/>
            <circle cx="50" cy="45" r="25" fill="none" stroke="white" stroke-width="4"/>
            <line x1="68" y1="62" x2="85" y2="79" stroke="white" stroke-width="6" stroke-linecap="round"/>
            <polyline points="38,52 45,45 52,48 60,38" fill="none" stroke="white" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
            <polygon points="60,38 57,42 60,41 58,48 62,46 65,43" fill="white"/>
          </svg>
        </div>
        <span class="brand-name">{{ t('nav.brandName') }}</span>
      </div>

      <nav class="nav-menu">
        <a
          v-for="item in navItems"
          :key="item.key"
          class="nav-item"
          :class="{ active: activeNav === item.key }"
          @click="handleNavClick(item.key)"
        >
          {{ t(item.label) }}
        </a>
      </nav>
    </div>

    <div class="nav-right">
      <button class="nav-btn icon-btn" :title="t('nav.notification')">🔔</button>

      <!-- 工作区切换器 -->
      <el-dropdown v-if="userStore.workspaces.length > 0" trigger="click" @command="handleWorkspaceSwitch">
        <div class="workspace-switcher">
          <span class="workspace-icon">🏢</span>
          <span class="workspace-name">{{ userStore.currentWorkspace?.name || '选择工作区' }}</span>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="ws in userStore.workspaces"
              :key="ws.id"
              :command="ws.id"
              :class="{ 'is-active': ws.id === userStore.currentWorkspaceId }"
            >
              {{ ws.name }}
              <span v-if="ws.memberRole" class="ws-role">{{ ws.memberRole }}</span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <!-- 用户菜单 -->
      <el-dropdown trigger="click" @command="handleUserCommand">
        <div class="user-avatar-wrapper">
          <div class="user-avatar">{{ avatarText }}</div>
          <span class="user-name">{{ userStore.nickname || userStore.username || '用户' }}</span>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item disabled>
              {{ userStore.username }}（{{ userStore.role }}）
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/useUserStore'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 用户头像文字（昵称或用户名首字） */
const avatarText = computed(() => {
  const name = userStore.nickname || userStore.username || '用'
  return name.charAt(0).toUpperCase()
})

/** 当前激活的导航项 */
const activeNav = computed(() => (route.query.nav as string) || 'smart-ask')

/** 导航项配置 */
const navItems = [
  { key: 'smart-ask', label: 'nav.smartAsk' },
]

/** 导航点击 */
function handleNavClick(key: string): void {
  router.push({ path: '/', query: { ...route.query, nav: key } })
}

/** 切换工作区 */
function handleWorkspaceSwitch(workspaceId: string | number): void {
  userStore.setCurrentWorkspace(workspaceId)
  ElMessage.success('已切换工作区')
  // 刷新当前页面数据
  window.location.reload()
}

/** 用户菜单命令处理 */
function handleUserCommand(command: string): void {
  if (command === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
/* ========== 顶部导航栏容器 ========== */
.top-nav {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: #ffffff;
  border-bottom: 1px solid #f0f1f3;
  flex-shrink: 0;
  z-index: 100;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
}

/* ========== 左侧：品牌 + 导航菜单 ========== */
.nav-left {
  display: flex;
  align-items: center;
  gap: 32px;
  min-width: 0;
}

.brand-area {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  user-select: none;
}

.logo {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
  transition: transform 0.2s, box-shadow 0.2s;
}

.brand-area:hover .logo {
  transform: scale(1.05);
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.15);
}

.brand-name {
  font-size: 15px;
  font-weight: 600;
  color: #1d2129;
  letter-spacing: 0.3px;
  line-height: 20px;
  white-space: nowrap;
}

/* ========== 导航菜单 ========== */
.nav-menu {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.nav-item {
  position: relative;
  padding: 8px 18px;
  font-size: 14px;
  font-weight: 500;
  line-height: 20px;
  color: #4e5969;
  cursor: pointer;
  border-radius: 6px;
  transition: color 0.2s ease, background 0.2s ease;
  white-space: nowrap;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
}

.nav-item:hover {
  color: #f05a23;
  background: rgba(240, 90, 35, 0.06);
}

.nav-item.active {
  color: #f05a23;
  font-weight: 600;
}

.nav-item.active::after {
  content: '';
  position: absolute;
  bottom: -17px;
  left: 50%;
  transform: translateX(-50%);
  width: 20px;
  height: 2px;
  background: #f05a23;
  border-radius: 1px;
}

/* ========== 右侧：图标按钮 + 头像 ========== */
.nav-right {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.icon-btn {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: none;
  background: transparent;
  font-size: 16px;
  color: #4e5969;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s ease, color 0.2s ease;
  padding: 0;
}

.icon-btn:hover {
  background: #f2f3f5;
  color: #1d2129;
}

.icon-btn:active {
  background: #e5e6eb;
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #f05a23 0%, #e75c01 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  cursor: pointer;
  transition: box-shadow 0.2s ease, transform 0.2s ease;
  user-select: none;
  flex-shrink: 0;
}

.user-avatar:hover {
  box-shadow: 0 2px 10px rgba(240, 90, 35, 0.3);
  transform: scale(1.05);
}

.user-avatar-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px 4px 4px;
  border-radius: 8px;
  transition: background 0.2s ease;
  margin-left: 4px;
}

.user-avatar-wrapper:hover {
  background: #f2f3f5;
}

.user-name {
  font-size: 13px;
  color: #1d2129;
  font-weight: 500;
  white-space: nowrap;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 工作区切换器 */
.workspace-switcher {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s ease;
  margin-right: 4px;
}

.workspace-switcher:hover {
  background: #f2f3f5;
}

.workspace-icon {
  font-size: 16px;
}

.workspace-name {
  font-size: 13px;
  color: #1d2129;
  font-weight: 500;
  white-space: nowrap;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ws-role {
  margin-left: 8px;
  font-size: 11px;
  color: #86909c;
}

:deep(.el-dropdown-menu .is-active) {
  color: var(--main-orange);
  font-weight: 600;
}

/* ========== 响应式适配 ========== */
@media (max-width: 1280px) {
  .nav-left {
    gap: 24px;
  }
  .nav-item {
    padding: 8px 14px;
  }
}

@media (max-width: 1024px) {
  .top-nav {
    padding: 0 20px;
  }
  .nav-left {
    gap: 20px;
  }
  .nav-item {
    padding: 8px 12px;
  }
}

@media (max-width: 768px) {
  .top-nav {
    padding: 0 16px;
  }
  .brand-name {
    display: none;
  }
  .nav-left {
    gap: 12px;
  }
  .icon-btn {
    width: 32px;
    height: 32px;
  }
  .user-avatar {
    width: 32px;
    height: 32px;
  }
}
</style>