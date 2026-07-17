<template>
  <header class="top-nav">
    <div class="nav-left">
      <div class="brand-area">
        <div class="logo">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="32" height="32">
            <defs>
              <linearGradient id="logoGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" style="stop-color:var(--main-orange);stop-opacity:1" />
                <stop offset="100%" style="stop-color:var(--dark-orange);stop-opacity:1" />
              </linearGradient>
            </defs>
            <rect width="100" height="100" rx="15" fill="url(#logoGrad)"/>
            <circle cx="50" cy="45" r="25" fill="none" stroke="white" stroke-width="4"/>
            <line x1="68" y1="62" x2="85" y2="79" stroke="white" stroke-width="6" stroke-linecap="round"/>
            <polyline points="38,52 45,45 52,48 60,38" fill="none" stroke="white" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
            <polygon points="60,38 57,42 60,41 58,48 62,46 65,43" fill="white"/>
          </svg>
        </div>
        <span class="brand-name brand-name-gradient">{{ t('nav.brandName') }}</span>
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
      <button class="nav-btn icon-btn notification-btn" :title="t('nav.notification')">
        <el-icon class="nav-icon"><Bell /></el-icon>
        <span class="notification-dot"></span>
      </button>

      <!-- 主题切换器 -->
      <el-dropdown trigger="click" @command="handleThemeCommand">
        <button class="nav-btn icon-btn theme-btn" :title="t('theme.title')">
          <el-icon class="nav-icon"><Brush /></el-icon>
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
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell, Brush, Sunny, Moon, View, MagicStick } from '@element-plus/icons-vue'
import { useThemeStore, type ThemeMode } from '@/stores/useThemeStore'
import type { Component } from 'vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const themeStore = useThemeStore()

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

/** 主题选项配置 */
const themeOptions: { key: ThemeMode; label: string; icon: Component }[] = [
  { key: 'light', label: 'theme.light', icon: Sunny },
  { key: 'warm', label: 'theme.warm', icon: MagicStick },
  { key: 'eye-care', label: 'theme.eyeCare', icon: View },
  { key: 'dark', label: 'theme.dark', icon: Moon },
  { key: 'system', label: 'theme.system', icon: Brush },
]

/** 主题菜单命令处理 */
function handleThemeCommand(command: ThemeMode): void {
  themeStore.setTheme(command)
  ElMessage.success(t('theme.switchSuccess'))
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
  background: var(--theme-surface);
  border-bottom: 1px solid var(--theme-border);
  flex-shrink: 0;
  z-index: 100;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
  transition: background 0.25s ease, border-color 0.25s ease;
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
  letter-spacing: 0.3px;
  line-height: 20px;
  white-space: nowrap;
}

.brand-name-gradient {
  background: linear-gradient(90deg, var(--main-orange) 0%, var(--dark-orange) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  font-weight: 700;
  font-size: 17px;
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
  color: var(--theme-text-secondary);
  cursor: pointer;
  border-radius: 6px;
  transition: color 0.2s ease, background 0.2s ease;
  white-space: nowrap;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
}

.nav-item:hover {
  color: var(--main-orange);
  background: color-mix(in srgb, var(--main-orange) 6%, transparent);
}

.nav-item.active {
  color: var(--main-orange);
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
  background: var(--main-orange);
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
  border-radius: 10px;
  border: none;
  background: transparent;
  color: var(--theme-text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  padding: 0;
}

.icon-btn:hover {
  background: color-mix(in srgb, var(--main-orange) 8%, transparent);
  color: var(--main-orange);
}

.icon-btn:active {
  background: color-mix(in srgb, var(--main-orange) 12%, transparent);
}

.nav-icon {
  font-size: 18px;
}

.notification-btn {
  position: relative;
}

.notification-dot {
  position: absolute;
  top: 7px;
  right: 7px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #ef4444;
  border: 1.5px solid var(--theme-surface);
  box-shadow: 0 0 0 1px rgba(239, 68, 68, 0.2);
}

:deep(.el-dropdown-menu .is-active) {
  color: var(--main-orange);
  font-weight: 600;
}

.theme-option-icon {
  margin-right: 6px;
  font-size: 16px;
  vertical-align: middle;
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
}
</style>