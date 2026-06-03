<template>
  <header class="top-nav">
    <div class="nav-left">
      <div class="brand-area">
        <div class="logo">Q</div>
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
      <el-input
        v-model="searchText"
        :placeholder="t('workbench.searchPlaceholder')"
        size="small"
        class="search-input"
      >
        <template #prefix>
          <span class="search-icon">🔍</span>
        </template>
      </el-input>

      <template v-if="activeNav === 'smart-ask'">
        <div class="nav-divider"></div>

        <button class="nav-btn btn-outline" @click="handleExportReport">
          {{ t('workbench.exportReport') }}
        </button>

        <button class="nav-btn btn-primary" @click="handleMySkills">
          {{ t('workbench.mySkills') }} ▾
        </button>

        <button
          class="nav-btn btn-immersion"
          :class="{ active: immersionMode }"
          @click="toggleImmersion"
        >
          {{ t('workbench.immersionMode') }}
        </button>
      </template>

      <div class="nav-divider" v-if="activeNav !== 'smart-ask'"></div>

      <button class="nav-btn icon-btn" :title="t('nav.appCenter')">⊞</button>
      <button class="nav-btn icon-btn" :title="t('nav.notification')">🔔</button>
      <button class="nav-btn icon-btn" :title="t('nav.settings')">⚙</button>
      <div class="user-avatar" :title="t('nav.user')">👤</div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

/** 搜索文本 */
const searchText = ref('')

/** 当前激活的导航项 */
const activeNav = computed(() => (route.query.nav as string) || 'smart-ask')

/** 沉浸模式状态 */
const immersionMode = ref(false)

/** 导航项配置 */
const navItems = [
  { key: 'smart-ask', label: 'nav.smartAsk' },
  { key: 'dashboard', label: 'nav.myDashboard' },
  { key: 'portal', label: 'nav.enterprisePortal' },
  { key: 'workspace', label: 'nav.workspace' },
  { key: 'open-platform', label: 'nav.openPlatform' },
  { key: 'template-market', label: 'nav.templateMarket' },
]

/** 导航点击 */
function handleNavClick(key: string): void {
  router.push({ path: '/', query: { ...route.query, nav: key } })
}

/** 导出报告 */
function handleExportReport(): void {
}

/** 我的技能 */
function handleMySkills(): void {
}

/** 切换沉浸模式 */
function toggleImmersion(): void {
  immersionMode.value = !immersionMode.value
}
</script>

<style scoped>
.top-nav {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid #e8ecf2;
  flex-shrink: 0;
  z-index: 100;
}

.nav-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.brand-area {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-right: 24px;
}

.logo {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: linear-gradient(135deg, #f05a23, #ff7b3d);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-name {
  font-size: 16px;
  font-weight: 700;
  color: #1d2129;
  letter-spacing: 0.5px;
}

.nav-menu {
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav-item {
  padding: 6px 16px;
  font-size: 14px;
  color: #4e5969;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s;
  white-space: nowrap;
  text-decoration: none;
  line-height: 20px;
}

.nav-item:hover {
  color: #f05a23;
  background: rgba(240, 90, 35, 0.06);
}

.nav-item.active {
  color: #f05a23;
  font-weight: 600;
  position: relative;
}

.nav-item.active::after {
  content: '';
  position: absolute;
  bottom: -13px;
  left: 50%;
  transform: translateX(-50%);
  width: 20px;
  height: 2px;
  background: #f05a23;
  border-radius: 1px;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.nav-divider {
  width: 1px;
  height: 24px;
  background: #e5e6eb;
  flex-shrink: 0;
}

.search-input {
  width: 200px;
}

.search-input :deep(.el-input__wrapper) {
  border-radius: 18px;
  box-shadow: 0 0 0 1px #e5e6eb inset;
}

.search-icon {
  font-size: 12px;
}

.nav-btn {
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-outline {
  height: 32px;
  border-radius: 16px;
  border: 1px solid #e5e6eb;
  background: #f2f3f5;
  padding: 0 14px;
  font-size: 12px;
  color: #4e5969;
}

.btn-outline:hover {
  border-color: #f05a23;
  color: #f05a23;
  background: rgba(240, 90, 35, 0.06);
}

.btn-primary {
  height: 32px;
  border-radius: 16px;
  border: none;
  background: #f05a23;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  padding: 0 14px;
}

.btn-primary:hover {
  background: #e75c01;
}

.btn-immersion {
  padding: 4px 12px;
  border-radius: 12px;
  border: 1px solid #e5e6eb;
  background: #f2f3f5;
  color: #86909c;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.btn-immersion:hover,
.btn-immersion.active {
  background: #f05a23;
  color: #fff;
  border-color: #f05a23;
}

.icon-btn {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  border: none;
  background: transparent;
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.icon-btn:hover {
  background: #f2f3f5;
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #e8edff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.2s;
}

.user-avatar:hover {
  background: #d9e3ff;
}
</style>