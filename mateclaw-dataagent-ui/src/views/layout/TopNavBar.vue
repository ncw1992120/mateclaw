<template>
  <header class="top-nav">
    <div class="nav-left">
      <div class="brand-area" role="button" tabindex="0" :title="t('nav.brandName')" @click="handleNavClick('smart-ask')" @keydown.enter="handleNavClick('smart-ask')">
        <div class="logo">
          <!-- 线性渐变标志：开口圆环 + 上升趋势线，无方形底 -->
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48" width="34" height="34" fill="none" aria-hidden="true">
            <defs>
              <linearGradient id="logoGrad" x1="6" y1="8" x2="42" y2="42" gradientUnits="userSpaceOnUse">
                <stop style="stop-color:var(--main-orange)" />
                <stop offset="1" style="stop-color:var(--dark-orange)" />
              </linearGradient>
            </defs>
            <circle cx="24" cy="26" r="15" stroke="url(#logoGrad)" stroke-width="6" stroke-linecap="round" stroke-dasharray="71 24" />
            <path d="M15 31l7-7 5 4 9-11" stroke="url(#logoGrad)" stroke-width="5.5" stroke-linecap="round" stroke-linejoin="round" />
            <circle cx="37" cy="15" r="3.6" fill="var(--dark-orange)" />
          </svg>
        </div>
        <span class="brand-name">{{ t('nav.brandName') }}</span>
      </div>
    </div>

    <nav ref="menuRef" class="nav-menu">
      <span class="nav-pill" :style="pillStyle" aria-hidden="true"></span>
      <a
        v-for="item in navItems"
        :key="item.key"
        class="nav-item"
        :class="{ active: activeNav === item.key }"
        tabindex="0"
        @click="handleNavClick(item.key)"
        @keydown.enter="handleNavClick(item.key)"
      >
        {{ t(item.label) }}
      </a>
    </nav>

    <div class="nav-right">
      <button class="nav-btn icon-btn notification-btn" :title="t('nav.notification')">
        <el-icon class="nav-icon"><Bell /></el-icon>
        <span class="notification-dot"></span>
      </button>

      <!-- 主题切换器 -->
      <el-dropdown trigger="click" popper-class="topnav-dropdown-popper" @command="handleThemeCommand">
        <button class="nav-btn icon-btn theme-btn" :title="t('theme.title')">
          <svg class="nav-icon-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="item in themeOptions"
              :key="item.key"
              :command="item.key"
              :class="{ 'is-active': themeStore.theme === item.key }"
            >
              <el-icon class="theme-option-icon"><component :is="item.icon" /></el-icon>
              {{ t(item.label) }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <!-- 用户头像（纯圆形，点击仍打开下拉菜单） -->
      <el-dropdown trigger="click" popper-class="topnav-dropdown-popper" @command="handleUserCommand">
        <div class="user-chip" :title="userStore.username">
          <div class="user-avatar"><span>{{ avatarText }}</span></div>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item disabled>
              <div class="dropdown-user-info">
                <span class="dropdown-user-name">{{ userStore.nickname || userStore.username }}</span>
                <span class="dropdown-user-account">@{{ userStore.username }}</span>
              </div>
            </el-dropdown-item>
            <el-dropdown-item v-if="canManageWorkspace" command="manage">
              <span class="dropdown-manage-item">
                <el-icon><FolderOpened /></el-icon>
                <span>{{ t('workspace.manage') }}</span>
              </span>
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              <span class="dropdown-logout-item">
                <el-icon><SwitchButton /></el-icon>
                <span>退出登录</span>
              </span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell, Sunny, Moon, View, MagicStick, FolderOpened, SwitchButton } from '@element-plus/icons-vue'
import { useThemeStore, type ThemeMode } from '@/stores/useThemeStore'
import { useUserStore } from '@/stores/useUserStore'
import { useChatStore } from '@/stores/useChatStore'
import { usePermission, PERMISSION } from '@/composables/usePermission'
import type { Component } from 'vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const themeStore = useThemeStore()
const userStore = useUserStore()
const chatStore = useChatStore()

/** 当前激活的导航项 */
const activeNav = computed(() => (route.query.nav as string) || 'smart-ask')

/** 菜单容器引用（高亮滑块定位的 offsetParent） */
const menuRef = ref<HTMLElement | null>(null)

/** 高亮滑块样式：测量激活项位置，切换时由 CSS transition 平滑滑动 */
const pillStyle = ref<{ left: string; top: string; width: string; height: string; opacity: number }>({
  left: '0px',
  top: '0px',
  width: '0px',
  height: '0px',
  opacity: 0,
})

/** 测量激活导航项并移动高亮滑块 */
function updateNavPill(): void {
  const menu = menuRef.value
  if (!menu) return
  const active = menu.querySelector<HTMLElement>('.nav-item.active')
  if (!active) {
    pillStyle.value.opacity = 0
    return
  }
  pillStyle.value = {
    left: `${active.offsetLeft}px`,
    top: `${active.offsetTop}px`,
    width: `${active.offsetWidth}px`,
    height: `${active.offsetHeight}px`,
    opacity: 1,
  }
}

watch(activeNav, () => {
  nextTick(updateNavPill)
})

onMounted(() => {
  updateNavPill()
  window.addEventListener('resize', updateNavPill)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateNavPill)
})

/** 导航项配置 */
const navItems = [
  { key: 'smart-ask', label: 'nav.subQa' },
  { key: 'insight', label: 'nav.subInterpret' },
  { key: 'report', label: 'nav.subReport' },
  { key: 'config', label: 'nav.subSkill' },
  { key: 'help', label: 'nav.help' },
]

/** 导航点击 */
function handleNavClick(key: string): void {
  router.push({ path: '/', query: { ...route.query, nav: key } })
}

/** 主题选项配置 */
const themeOptions: { key: ThemeMode; label: string; icon: Component }[] = [
  { key: 'light', label: 'theme.light', icon: Sunny },
  { key: 'warm', label: 'theme.warm', icon: MagicStick },
  { key: 'eye-care', label: 'theme.eyeCare', icon: View },
  { key: 'dark', label: 'theme.dark', icon: Moon },
  { key: 'system', label: 'theme.system', icon: Sunny },
]

/** 主题菜单命令处理 */
function handleThemeCommand(command: ThemeMode): void {
  themeStore.setTheme(command)
  ElMessage.success(t('theme.switchSuccess'))
}

/** 用户头像首字母 */
const avatarText = computed(() => {
  const name = userStore.nickname || userStore.username || '用'
  return name.charAt(0).toUpperCase()
})

/** 是否可管理工作区 */
const { hasPermission } = usePermission()
const canManageWorkspace = computed(() => hasPermission(PERMISSION.WORKSPACE_MANAGE))

/** 用户菜单命令处理 */
function handleUserCommand(command: string): void {
  if (command === 'manage') {
    if (!canManageWorkspace.value) {
      ElMessage.warning(t('workspace.manageNoPermission'))
      return
    }
    localStorage.setItem('mc-config-center-active-tab', 'workspace')
    localStorage.setItem('mc-workspace-active-sub-menu', 'workspaceManage')
    window.dispatchEvent(new CustomEvent('navigate-to-workspace-manage'))
    router.push({ path: '/', query: { ...route.query, nav: 'config' } })
    return
  }
  if (command === 'logout') {
    chatStore.resetForWorkspaceSwitch()
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
/* ========== 顶部导航栏容器 ========== */
.top-nav {
  height: 56px;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 0 24px;
  /* 与内容区同底色（参考设计稿：顶栏无独立背景，导航元素浮于页面底色上） */
  background: transparent;
  border-bottom: 1px solid transparent;
  flex-shrink: 0;
  z-index: 100;
}

/* ========== 左侧：品牌 ========== */
.nav-left {
  display: flex;
  align-items: center;
  justify-self: start;
  min-width: 0;
}

.brand-area {
  display: flex;
  align-items: center;
  gap: 9px;
  flex-shrink: 0;
  user-select: none;
  cursor: pointer;
  padding: 4px 6px;
  margin: -4px -6px;
  border-radius: 10px;
}

.logo {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  transition: transform 0.2s ease;
}

.brand-area:hover .logo {
  transform: scale(1.06);
}

.brand-name {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.6px;
  line-height: 20px;
  white-space: nowrap;
  color: var(--theme-text);
}

/* ========== 导航菜单（居中，分段胶囊式） ========== */
/* 菜单容器：elevated 底色（蓝色主题下为白底，暗色自动适配）浮起胶囊 */
.nav-menu {
  position: relative;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  border-radius: 999px;
  background: var(--theme-surface-elevated);
  border: 1px solid var(--theme-border);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  justify-self: center;
  min-width: 0;
}

/* 高亮滑块：在导航项间平滑滑动，主题色底 + 悬浮投影（随主题 accent 换色）；
   四向位移统一同一缓动曲线，避免宽高与位置不同步 */
.nav-pill {
  position: absolute;
  border-radius: 999px;
  background: var(--main-orange);
  box-shadow:
    0 4px 12px color-mix(in srgb, var(--main-orange) 40%, transparent),
    0 2px 4px rgba(0, 0, 0, 0.1);
  pointer-events: none;
  transition:
    left 0.32s cubic-bezier(0.35, 0.9, 0.3, 1),
    width 0.32s cubic-bezier(0.35, 0.9, 0.3, 1),
    top 0.32s cubic-bezier(0.35, 0.9, 0.3, 1),
    height 0.32s cubic-bezier(0.35, 0.9, 0.3, 1),
    opacity 0.2s ease;
}

.nav-item {
  position: relative;
  z-index: 1;
  padding: 8px 20px;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 400;
  line-height: 20px;
  color: var(--theme-text-secondary);
  cursor: pointer;
  transition: color 0.25s ease, background 0.2s ease;
  white-space: nowrap;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  letter-spacing: 0.3px;
}

.nav-item:hover:not(.active) {
  color: var(--theme-text);
  background: color-mix(in srgb, var(--theme-text-muted) 8%, transparent);
}

/* 激活项：文字白色浮于滑块之上；字号与默认态一致，
   仅加粗区分，避免激活瞬间文字放大带动滑块宽度抖动 */
.nav-item.active {
  color: #fff;
  font-weight: 600;
}

/* ========== 右侧：图标按钮 + 头像 ========== */
.nav-right {
  display: flex;
  align-items: center;
  gap: 4px;
  justify-self: end;
  flex-shrink: 0;
}

.icon-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  /* 常驻浅色底，hover 加深 */
  background: color-mix(in srgb, var(--theme-text-muted) 8%, transparent);
  color: var(--theme-text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  padding: 0;
}

.icon-btn:hover {
  background: color-mix(in srgb, var(--theme-text-muted) 16%, transparent);
  color: var(--theme-text);
}

.icon-btn:active {
  background: color-mix(in srgb, var(--theme-text-muted) 22%, transparent);
  color: var(--theme-text);
}

.nav-icon {
  font-size: 18px;
}

.nav-icon-svg {
  width: 18px;
  height: 18px;
}

.notification-btn {
  position: relative;
}

.notification-dot {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #ef4444;
  border: 1.5px solid var(--theme-bg);
  box-shadow: 0 0 0 1px rgba(239, 68, 68, 0.2);
}

.theme-option-icon {
  margin-right: 6px;
  font-size: 16px;
  vertical-align: middle;
}

/* ========== 用户头像（纯圆头像，hover 浅底） ========== */
.user-chip {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--theme-text-muted) 8%, transparent);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2px;
  transition: background 0.2s ease;
}

.user-chip:hover {
  background: color-mix(in srgb, var(--theme-text-muted) 16%, transparent);
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--very-light-orange);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--main-orange);
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}

.dropdown-user-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.4;
}

.dropdown-user-name {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--theme-text);
  line-height: 1.4;
}

.dropdown-user-account {
  display: block;
  font-size: 11px;
  color: var(--theme-text-muted);
  line-height: 1.3;
}

.dropdown-manage-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.dropdown-logout-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #e53e3e;
}

/* ========== 键盘可达性：统一主题色 focus 环 ========== */
.brand-area:focus-visible,
.nav-item:focus-visible,
.icon-btn:focus-visible,
.user-chip:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--main-orange) 55%, transparent);
  outline-offset: 2px;
}

/* ========== 响应式适配 ========== */
@media (max-width: 1280px) {
  .nav-item {
    padding: 8px 16px;
  }
}

@media (max-width: 1024px) {
  .top-nav {
    padding: 0 20px;
  }
  .nav-item {
    padding: 8px 13px;
  }
}

@media (max-width: 768px) {
  .top-nav {
    padding: 0 16px;
  }
  .brand-name {
    display: none;
  }
  .icon-btn {
    width: 32px;
    height: 32px;
  }
}
</style>

<!-- el-dropdown popper 会 teleport 到 body，scoped 样式无法命中，必须用全局样式 -->
<style>
.topnav-dropdown-popper {
  border-radius: 10px;
  border: 1px solid var(--theme-border);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  padding: 4px;
}

.topnav-dropdown-popper .el-dropdown-menu {
  border: none;
  box-shadow: none;
  padding: 0;
}

.topnav-dropdown-popper .el-dropdown-menu__item:not(.is-disabled):hover,
.topnav-dropdown-popper .el-dropdown-menu__item:not(.is-disabled):focus {
  background: var(--theme-surface-hover);
  color: var(--main-orange);
}

.topnav-dropdown-popper .el-dropdown-menu__item.is-active {
  color: var(--main-orange);
  font-weight: 600;
}

/* 退出登录保持红色 */
.topnav-dropdown-popper .el-dropdown-menu__item:not(.is-disabled):hover .dropdown-logout-item {
  color: #e53e3e;
}
</style>