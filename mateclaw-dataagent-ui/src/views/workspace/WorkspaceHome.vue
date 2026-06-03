<template>
  <div class="workspace-home">
    <!-- 左侧导航栏 -->
    <aside class="workspace-sidebar">
      <!-- 用户信息区 -->
      <div class="user-section">
        <div class="user-avatar">👤</div>
        <div class="user-info">
          <span class="user-name">{{ userInfo.name }}</span>
          <span class="user-role">{{ t('workspaceHome.workspaceMember') }}</span>
        </div>
      </div>

      <!-- 快捷操作按钮 -->
      <div class="quick-actions">
        <button class="action-btn primary" @click="handleFromTemplateCreate">
          {{ t('workspaceHome.fromTemplateCreate') }}
        </button>
        <div class="dropdown-wrapper" @mouseenter="showCreateMenu = true" @mouseleave="showCreateMenu = false">
          <button class="action-btn primary" :class="{ active: showCreateMenu }">
            {{ t('workspaceHome.createReport') }} ▲
          </button>
          <div v-show="showCreateMenu" class="create-dropdown">
            <h4 class="dropdown-title">{{ t('workspaceHome.quickCreate') }}</h4>
            <div class="dropdown-row large-items">
              <a class="dropdown-item large-item" @click="handleCreateAction('dashboard')">
                <span class="item-icon">📊</span>
                <span>{{ t('workspaceHome.dashboard') }}</span>
              </a>
              <a class="dropdown-item large-item" @click="handleCreateAction('dataScreen')">
                <span class="item-icon">📺</span>
                <span>{{ t('workspaceHome.dataScreen') }}</span>
              </a>
            </div>
            <div class="dropdown-row normal-items">
              <a class="dropdown-item" @click="handleCreateAction('datasource')">
                <span class="item-icon">📦</span>
                <span>{{ t('workspaceHome.datasource') }}</span>
              </a>
              <a class="dropdown-item" @click="handleCreateAction('dataset')">
                <span class="item-icon">📑</span>
                <span>{{ t('workspaceHome.dataset') }}</span>
              </a>
              <a class="dropdown-item" @click="handleCreateAction('localFile')">
                <span class="item-icon">📁</span>
                <span>{{ t('workspaceHome.localFile') }}</span>
              </a>
              <a class="dropdown-item" @click="handleCreateAction('dataEntry')">
                <span class="item-icon">📝</span>
                <span>{{ t('workspaceHome.dataEntry') }}</span>
              </a>
            </div>
            <div class="dropdown-row normal-items">
              <a class="dropdown-item" @click="handleCreateAction('adHocAnalysis')">
                <span class="item-icon">📈</span>
                <span>{{ t('workspaceHome.adHocAnalysis') }}</span>
              </a>
              <a class="dropdown-item" @click="handleCreateAction('dataPortal')">
                <span class="item-icon">🚪</span>
                <span>{{ t('workspaceHome.dataPortal') }}</span>
              </a>
              <a class="dropdown-item" @click="handleCreateAction('spreadsheet')">
                <span class="item-icon">📋</span>
                <span>{{ t('workspaceHome.spreadsheet') }}</span>
              </a>
              <a class="dropdown-item" @click="handleCreateAction('selfService')">
                <span class="item-icon">🔍</span>
                <span>{{ t('workspaceHome.selfService') }}</span>
              </a>
            </div>
          </div>
        </div>
      </div>

      <!-- 快捷创建网格（常驻显示） -->
      <div class="quick-grid-section">
        <h5 class="grid-label">{{ t('workspaceHome.quickCreate') }}</h5>
        <div class="quick-grid">
          <a class="quick-grid-item featured" @click="handleCreateAction('dashboard')">
            <span class="qgi-icon">📊</span>
            <span class="qgi-text">{{ t('workspaceHome.dashboard') }}</span>
          </a>
          <a class="quick-grid-item featured" @click="handleCreateAction('dataScreen')">
            <span class="qgi-icon">📺</span>
            <span class="qgi-text">{{ t('workspaceHome.dataScreen') }}</span>
          </a>
          <a class="quick-grid-item featured" @click="handleCreateAction('datasource')">
            <span class="qgi-icon">📦</span>
            <span class="qgi-text">{{ t('workspaceHome.datasource') }}</span>
          </a>
          <a class="quick-grid-item" @click="handleCreateAction('dataset')">
            <span class="qgi-icon">📑</span>
            <span class="qgi-text">{{ t('workspaceHome.dataset') }}</span>
          </a>
          <a class="quick-grid-item" @click="handleCreateAction('localFile')">
            <span class="qgi-icon">📁</span>
            <span class="qgi-text">{{ t('workspaceHome.localFile') }}</span>
          </a>
          <a class="quick-grid-item" @click="handleCreateAction('dataEntry')">
            <span class="qgi-icon">📝</span>
            <span class="qgi-text">{{ t('workspaceHome.dataEntry') }}</span>
          </a>
          <a class="quick-grid-item" @click="handleCreateAction('adHocAnalysis')">
            <span class="qgi-icon">📈</span>
            <span class="qgi-text">{{ t('workspaceHome.adHocAnalysis') }}</span>
          </a>
          <a class="quick-grid-item" @click="handleCreateAction('dataPortal')">
            <span class="qgi-icon">🚪</span>
            <span class="qgi-text">{{ t('workspaceHome.dataPortal') }}</span>
          </a>
          <a class="quick-grid-item" @click="handleCreateAction('spreadsheet')">
            <span class="qgi-icon">📋</span>
            <span class="qgi-text">{{ t('workspaceHome.spreadsheet') }}</span>
          </a>
          <a class="quick-grid-item" @click="handleCreateAction('selfService')">
            <span class="qgi-icon">🔍</span>
            <span class="qgi-text">{{ t('workspaceHome.selfService') }}</span>
          </a>
        </div>
      </div>

      <!-- 工作空间列表 -->
      <div class="workspace-list-header" @click="toggleWorkspaceList">
        <span class="list-icon">📂</span>
        <span>{{ t('workspaceHome.myWorkspace') }}</span>
        <span class="list-arrow" :class="{ open: workspaceListOpen }">▸</span>
      </div>

      <div v-show="workspaceListOpen" class="workspace-items">
        <a
          v-for="space in workspaces"
          :key="space.id"
          class="workspace-item"
          :class="{ active: currentSpaceId === space.id }"
          @click="selectWorkspace(space.id)"
        >
          <span class="wi-icon">{{ space.icon }}</span>
          <span class="wi-name">{{ space.name }}</span>
          <span class="wi-count">{{ space.count }}</span>
        </a>
      </div>

      <!-- 最近工作空间 -->
      <div class="recent-section">
        <h5 class="section-label">
          <span class="sl-icon">⏱️</span>
          {{ t('workspaceHome.recentWorkspace') }}
        </h5>
        <a
          v-for="recent in recentWorkspaces"
          :key="recent.id"
          class="recent-item"
          :class="{ active: currentSpaceId === recent.id }"
          @click="selectWorkspace(recent.id)"
        >
          <span class="ri-icon">{{ recent.icon }}</span>
          <span class="ri-name">{{ recent.name }}</span>
          <span class="ri-meta">{{ recent.owner }} · {{ recent.count }}</span>
        </a>
      </div>

      <!-- 创建工作空间 -->
      <div class="create-workspace" @click="handleCreateWorkspace">
        <span class="cw-icon">➕</span>
        <span>{{ t('workspaceHome.createWorkspace') }}</span>
      </div>

      <!-- 底部链接 -->
      <div class="sidebar-footer">
        <a class="footer-link" @click="handleBackToWorkbench">
          {{ t('workspaceHome.backToWorkbench') }}
        </a>
      </div>
    </aside>

    <!-- 主内容区 -->
    <main class="workspace-main">
      <!-- 头部标题和筛选 -->
      <header class="main-header">
        <h1 class="page-title">{{ t('workspaceHome.recommendedTemplates') }}</h1>
        <div class="header-actions">
          <div class="filter-tabs">
            <button
              v-for="tab in filterTabs"
              :key="tab.key"
              class="filter-tab"
              :class="{ active: activeFilter === tab.key }"
              @click="activeFilter = tab.key"
            >
              {{ t(tab.label) }}
            </button>
          </div>
          <button class="view-all-btn">
            {{ t('workspaceHome.viewAllCases') }} →
          </button>
        </div>
      </header>

      <!-- 模板卡片网格 -->
      <section class="template-grid">
        <div
          v-for="template in templates"
          :key="template.id"
          class="template-card"
          @click="handleTemplateClick(template)"
        >
          <div class="card-image">
            <div class="card-image-placeholder">
              <span class="placeholder-icon">🖼️</span>
              <p class="placeholder-line1">{{ t('workspaceHome.imageGenerating') }}</p>
              <p class="placeholder-line2">{{ t('workspaceHome.imageRefreshHint') }}</p>
            </div>
            <span v-if="template.badge" class="card-badge">{{ template.badge }}</span>
          </div>
          <div class="card-body">
            <h3 class="card-title">{{ template.title }}</h3>
            <div class="card-stats">
              <span class="stat-item">
                <span class="stat-dot"></span>
                👁️ {{ template.views }}
              </span>
              <span class="stat-item">
                <span class="stat-dot"></span>
                📦 {{ template.size }}
              </span>
            </div>
            <p class="card-author">{{ t('workspaceHome.authorPrefix') }}{{ template.authorCount }}</p>
          </div>
        </div>
      </section>

      <!-- 内容标签页区域 -->
      <section class="content-area">
        <div class="content-tabs">
          <button
            v-for="tab in contentTabs"
            :key="tab.key"
            class="content-tab"
            :class="{ active: activeContentTab === tab.key }"
            @click="activeContentTab = tab.key"
          >
            {{ t(tab.label) }}
          </button>
        </div>
        <div class="content-toolbar">
          <input
            class="toolbar-search"
            :placeholder="t('workbench.searchPlaceholder')"
          />
          <span class="toolbar-hints">
            {{ t('workspaceHome.searchHints') }}
          </span>
          <select class="toolbar-sort">
            <option>{{ t('workspaceHome.allTypes') }}</option>
          </select>
        </div>
      </section>

      <!-- 空状态 -->
      <div class="empty-state">
        <div class="empty-visual">
          <div class="empty-box">
            <span class="box-icon">📭</span>
          </div>
        </div>
        <p class="empty-text">{{ t('workspaceHome.noRecentContent') }}</p>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useI18n } from 'vue-i18n'

const emit = defineEmits<{
  (e: 'navigate', page: string): void
}>()

const { t } = useI18n()

/** 用户信息 */
const userInfo = reactive({
  name: '15521219063',
})

/** 工作空间列表是否展开 */
const workspaceListOpen = ref(true)

/** 创建报表下拉菜单是否显示 */
const showCreateMenu = ref(false)

/** 当前选中的空间 ID */
const currentSpaceId = ref(1)

/** 当前筛选标签 */
const activeFilter = ref('featured')

/** 当前内容标签 */
const activeContentTab = ref('recent')

/** 筛选标签配置 */
const filterTabs = [
  { key: 'featured', label: 'workspaceHome.featured' },
  { key: 'mine', label: 'workspaceHome.mine' },
  { key: 'all', label: 'workspaceHome.allIndustryCases' },
]

/** 内容标签配置 */
const contentTabs = [
  { key: 'recent', label: 'workspaceHome.recentEdited' },
  { key: 'created', label: 'workspaceHome.myCreated' },
  { key: 'favorite', label: 'workspaceHome.myFavorite' },
]

/** 工作空间数据 */
const workspaces = ref([
  { id: 1, name: t('workspaceHome.demoWorkspace'), icon: '📊', count: 1 },
])

/** 最近工作空间 */
const recentWorkspaces = ref([
  {
    id: 2,
    name: t('workspaceHome.exampleWorkspace'),
    icon: '📈',
    owner: t('workspaceHome.zhangsan'),
    count: 1,
  },
])

/** 推荐模板数据 */
const templates = ref([
  {
    id: 1,
    title: 'Quick BI 可视化分析',
    views: '3.0W',
    size: '7.5K',
    authorCount: '01',
    badge: 'NEW',
  },
  {
    id: 2,
    title: '电商与优化案例库',
    views: '1.5W',
    size: '5.7K',
    authorCount: '03',
  },
  {
    id: 3,
    title: '移动端OKR监控',
    views: '6.3K',
    size: '1.1K',
    authorCount: '',
  },
  {
    id: 4,
    title: '移动端营销分析',
    views: '8.4K',
    size: '1.1K',
    authorCount: '',
  },
  {
    id: 5,
    title: '全域供应链监控人屏',
    views: '1.0W',
    size: '1.1K',
    authorCount: '04',
  },
  {
    id: 6,
    title: '多层级经营复盘驾驶舱',
    views: '1.7W',
    size: '1.0K',
    authorCount: '05',
  },
])

/** 切换工作空间列表展开状态 */
function toggleWorkspaceList(): void {
  workspaceListOpen.value = !workspaceListOpen.value
}

/** 选择工作空间 */
function selectWorkspace(id: number): void {
  currentSpaceId.value = id
}

/** 从模板创建 */
function handleFromTemplateCreate(): void {
  emit('navigate', 'fromTemplate')
}

/** 创建报表下拉菜单项点击 */
function handleCreateAction(action: string): void {
  showCreateMenu.value = false
  emit('navigate', action)
}

/** 创建工作空间 */
function handleCreateWorkspace(): void {
}

/** 返回工作台 */
function handleBackToWorkbench(): void {
  emit('navigate', 'workbench')
}

/** 点击模板 */
function handleTemplateClick(template: any): void {
}
</script>

<style scoped>
.workspace-home {
  display: flex;
  width: 100%;
  height: 100%;
  background: #f7f8fa;
  overflow: hidden;
}

/* ==================== 左侧导航栏 ==================== */
.workspace-sidebar {
  width: 200px;
  min-width: 200px;
  background: #fff;
  border-right: 1px solid #e8e9eb;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  overflow-x: hidden;
  flex-shrink: 0;
}

/* 用户信息区 */
.user-section {
  padding: 16px 14px 12px 14px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid #f2f3f5;
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, #e8edff 0%, #d6e4ff 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.user-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: #1d2129;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-role {
  font-size: 11px;
  color: #86909c;
}

/* 快捷操作按钮 */
.quick-actions {
  padding: 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.action-btn {
  width: 100%;
  height: 30px;
  border-radius: 4px;
  border: none;
  font-size: 12.5px;
  cursor: pointer;
  font-family: inherit;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  letter-spacing: 0.2px;
}

.action-btn.primary {
  background: #165dff;
  color: #fff;
}

.action-btn.primary:hover {
  background: #0e42d2;
}

.action-btn.primary.active {
  background: #0e42d2;
  box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.15);
}

/* 创建报表下拉菜单 */
.dropdown-wrapper {
  position: relative;
}

.create-dropdown {
  position: absolute;
  top: calc(100% + 6px);
  left: -4px;
  width: 300px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  border: 1px solid #e5e6eb;
  padding: 14px 12px 10px 12px;
  z-index: 999;
  animation: dropdownFadeIn 0.15s ease-out;
}

@keyframes dropdownFadeIn {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.dropdown-title {
  font-size: 13px;
  font-weight: 600;
  color: #1d2129;
  margin: 0 0 10px 0;
}

.dropdown-row {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
}

.dropdown-row:last-child {
  margin-bottom: 0;
}

.dropdown-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 8px 4px;
  border-radius: 5px;
  cursor: pointer;
  transition: all 0.15s;
  text-decoration: none;
  flex: 1;
  min-width: 0;
}

.dropdown-item:hover {
  background: #f2f3f5;
}

.dropdown-item .item-icon {
  font-size: 20px;
  line-height: 1;
}

.dropdown-item span:last-child {
  font-size: 10px;
  color: #4e5969;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
  text-align: center;
}

.large-items .dropdown-item {
  padding: 12px 4px;
}

.large-items .dropdown-item .item-icon {
  font-size: 26px;
}

.normal-items .dropdown-item .item-icon {
  font-size: 18px;
}

/* 快捷创建常驻网格 */
.quick-grid-section {
  padding: 10px 12px 6px 12px;
  border-top: 1px solid #f2f3f5;
}

.grid-label {
  font-size: 11.5px;
  font-weight: 500;
  color: #86909c;
  margin: 0 0 8px 0;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px 3px;
}

.quick-grid-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  padding: 7px 2px 5px 2px;
  border-radius: 5px;
  cursor: pointer;
  transition: all 0.15s;
  text-decoration: none;
}

.quick-grid-item:hover {
  background: #f2f3f5;
}

.quick-grid-item.featured {
  padding: 10px 2px 7px 2px;
}

.qgi-icon {
  font-size: 18px;
  line-height: 1;
  flex-shrink: 0;
}

.quick-grid-item.featured .qgi-icon {
  font-size: 22px;
}

.qgi-text {
  font-size: 10.5px;
  color: #4e5969;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
  text-align: center;
  line-height: 1.2;
}

/* 工作空间列表 */
.workspace-list-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  font-size: 12.5px;
  font-weight: 500;
  color: #1d2129;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
}

.workspace-list-header:hover {
  background: #f7f8fa;
}

.list-icon {
  font-size: 13px;
}

.list-arrow {
  margin-left: auto;
  font-size: 9px;
  color: #c9cdd4;
  transition: transform 0.2s;
}

.list-arrow.open {
  transform: rotate(90deg);
}

.workspace-items {
  padding: 2px 0;
}

.workspace-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px 6px 28px;
  font-size: 12px;
  color: #4e5969;
  cursor: pointer;
  transition: all 0.15s;
}

.workspace-item:hover {
  background: #f2f3f5;
}

.workspace-item.active {
  background: #e8f3ff;
  color: #165dff;
}

.wi-icon {
  font-size: 13px;
  flex-shrink: 0;
}

.wi-name {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.wi-count {
  font-size: 10px;
  color: #c9cdd4;
}

/* 最近工作空间 */
.recent-section {
  margin-top: 4px;
  padding: 6px 0;
  border-top: 1px solid #f2f3f5;
}

.section-label {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 14px;
  font-size: 11.5px;
  color: #86909c;
  margin: 0;
  font-weight: 400;
}

.sl-icon {
  font-size: 12px;
}

.recent-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px 6px 28px;
  font-size: 12px;
  color: #4e5969;
  cursor: pointer;
  transition: all 0.15s;
}

.recent-item:hover {
  background: #f2f3f5;
}

.recent-item.active {
  background: #e8f3ff;
  color: #165dff;
}

.ri-icon {
  font-size: 13px;
  flex-shrink: 0;
}

.ri-name {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ri-meta {
  font-size: 10px;
  color: #c9cdd4;
}

/* 创建工作空间 */
.create-workspace {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  margin-top: auto;
  font-size: 12px;
  color: #165dff;
  cursor: pointer;
  border-top: 1px solid #f2f3f5;
  transition: background 0.15s;
}

.create-workspace:hover {
  background: #f7f8fa;
}

.cw-icon {
  font-size: 13px;
}

/* 底部链接 */
.sidebar-footer {
  padding: 8px 14px 12px 14px;
  border-top: 1px solid #f2f3f5;
}

.footer-link {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11.5px;
  color: #165dff;
  cursor: pointer;
  transition: opacity 0.15s;
}

.footer-link:hover {
  opacity: 0.75;
}

/* ==================== 主内容区 ==================== */
.workspace-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  padding: 24px 32px;
}

/* 头部标题和筛选 */
.main-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-title {
  font-size: 17px;
  font-weight: 600;
  color: #1d2129;
  margin: 0;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.filter-tabs {
  display: flex;
  gap: 2px;
  background: #f2f3f5;
  padding: 3px;
  border-radius: 6px;
}

.filter-tab {
  padding: 5px 14px;
  border: none;
  background: transparent;
  font-size: 12.5px;
  color: #4e5969;
  cursor: pointer;
  border-radius: 4px;
  font-family: inherit;
  transition: all 0.2s;
}

.filter-tab:hover {
  color: #1d2129;
}

.filter-tab.active {
  background: #fff;
  color: #165dff;
  font-weight: 500;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.view-all-btn {
  padding: 5px 12px;
  border: none;
  background: transparent;
  font-size: 12.5px;
  color: #165dff;
  cursor: pointer;
  font-family: inherit;
  white-space: nowrap;
}

.view-all-btn:hover {
  opacity: 0.8;
}

/* 模板卡片网格 */
.template-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 16px;
  margin-bottom: 32px;
}

.template-card {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.25s ease;
  border: 1px solid #ebeef5;
}

.template-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 6px 20px rgba(22, 93, 255, 0.08);
  border-color: #b4d0ff;
}

.card-image {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 10;
  overflow: hidden;
  background: #fafbfc;
  border-bottom: 1px solid #f2f3f5;
}

.card-image-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.placeholder-icon {
  font-size: 28px;
  opacity: 0.35;
}

.placeholder-line1 {
  font-size: 11px;
  color: #c9cdd4;
  margin: 0;
  line-height: 1.3;
}

.placeholder-line2 {
  font-size: 10px;
  color: #dee0e3;
  margin: 0;
  line-height: 1.3;
}

.card-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 2px 8px;
  background: #165dff;
  color: #fff;
  font-size: 9.5px;
  font-weight: 700;
  border-radius: 3px;
  letter-spacing: 0.3px;
}

.card-body {
  padding: 12px 14px 10px 14px;
}

.card-title {
  font-size: 13px;
  font-weight: 600;
  color: #1d2129;
  margin: 0 0 8px 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.4;
}

.card-stats {
  display: flex;
  gap: 12px;
  margin-bottom: 6px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #86909c;
}

.stat-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #c9cdd4;
  flex-shrink: 0;
}

.card-author {
  font-size: 11px;
  color: #c9cdd4;
  margin: 0;
}

/* 内容标签页区域 */
.content-area {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 14px;
  border-bottom: 1px solid #e5e6eb;
  margin-bottom: 40px;
}

.content-tabs {
  display: flex;
  gap: 20px;
}

.content-tab {
  padding: 7px 0;
  border: none;
  background: transparent;
  font-size: 13.5px;
  color: #86909c;
  cursor: pointer;
  font-family: inherit;
  position: relative;
  transition: all 0.2s;
}

.content-tab:hover {
  color: #4e5969;
}

.content-tab.active {
  color: #165dff;
  font-weight: 600;
}

.content-tab.active::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 0;
  right: 0;
  height: 2px;
  background: #165dff;
  border-radius: 1px;
}

.content-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.toolbar-search {
  width: 160px;
  height: 30px;
  border-radius: 15px;
  border: 1px solid #e5e6eb;
  padding: 0 12px;
  font-size: 12px;
  outline: none;
  font-family: inherit;
  transition: border-color 0.2s;
}

.toolbar-search:focus {
  border-color: #165dff;
}

.toolbar-search::placeholder {
  color: #c9cdd4;
}

.toolbar-hints {
  font-size: 11px;
  color: #c9cdd4;
  white-space: nowrap;
}

.toolbar-sort {
  height: 30px;
  border-radius: 15px;
  border: 1px solid #e5e6eb;
  padding: 0 12px;
  font-size: 12px;
  outline: none;
  font-family: inherit;
  background: #fff;
  cursor: pointer;
  color: #4e5969;
}

/* 空状态 */
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 60px 0;
}

.empty-visual {
  position: relative;
}

.empty-box {
  width: 72px;
  height: 72px;
  border-radius: 16px;
  background: linear-gradient(135deg, #f0f2f5 0%, #e8ecf2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.04);
}

.box-icon {
  font-size: 32px;
  opacity: 0.45;
}

.empty-text {
  font-size: 13px;
  color: #c9cdd4;
  margin: 0;
}
</style>
