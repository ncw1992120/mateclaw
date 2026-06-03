<template>
  <aside class="side-menu" :class="{ collapsed: collapsed }">
    <div class="menu-header">
      <span v-if="!collapsed" class="back-link" @click="goBack">
        <span class="back-icon">◀</span>
        {{ t('sideMenu.backToWorkspace') }}
      </span>
      <button v-if="!collapsed" class="collapse-btn" @click="toggleCollapse">☰</button>
    </div>

    <div class="menu-body">
      <template v-if="!collapsed">
        <!-- 我的工作空间 -->
        <div class="menu-section">
          <div class="section-header workspace-header" @click="toggleSection('workspace')">
            <span class="section-icon">📂</span>
            <span class="section-text">{{ t('sideMenu.myWorkspace') }}</span>
            <span class="dropdown-arrow" :class="{ open: openSections.workspace }">▾</span>
          </div>
          <div v-show="openSections.workspace" class="section-items">
            <a
              class="menu-item"
              :class="{ active: currentPage === 'newCreate' }"
              @click="navigate('newCreate')"
            >
              <span class="item-icon">📄</span>
              <span>{{ t('sideMenu.newCreate') }}</span>
            </a>
          </div>
        </div>

        <!-- 数据分析 -->
        <div class="menu-section">
          <div class="section-header" @click="toggleSection('dataAnalysis')">
            <span class="section-icon">📊</span>
            <span class="section-text">{{ t('sideMenu.dataAnalysis') }}</span>
            <span class="expand-arrow" :class="{ open: openSections.dataAnalysis }">▸</span>
          </div>
          <div v-show="openSections.dataAnalysis" class="section-items">
            <a
              class="menu-item"
              :class="{ active: currentPage === 'dashboard' }"
              @click="navigate('dashboard')"
            >
              <span class="item-icon">📈</span>
              <span>{{ t('sideMenu.dashboard') }}</span>
            </a>
            <a
              class="menu-item"
              :class="{ active: currentPage === 'spreadsheet' }"
              @click="navigate('spreadsheet')"
            >
              <span class="item-icon">📋</span>
              <span>{{ t('sideMenu.spreadsheet') }}</span>
            </a>
            <a
              class="menu-item"
              :class="{ active: currentPage === 'dataScreen' }"
              @click="navigate('dataScreen')"
            >
              <span class="item-icon">🖥️</span>
              <span>{{ t('sideMenu.dataScreen') }}</span>
            </a>
            <a
              class="menu-item"
              :class="{ active: currentPage === 'adHocAnalysis' }"
              @click="navigate('adHocAnalysis')"
            >
              <span class="item-icon">📝</span>
              <span>{{ t('sideMenu.adHocAnalysis') }}</span>
            </a>
            <a
              class="menu-item"
              :class="{ active: currentPage === 'selfService' }"
              @click="navigate('selfService')"
            >
              <span class="item-icon">🔍</span>
              <span>{{ t('sideMenu.selfService') }}</span>
            </a>
          </div>
        </div>

        <!-- 数据构建 -->
        <div class="menu-section">
          <div class="section-header" @click="toggleSection('dataBuild')">
            <span class="section-icon">🏗️</span>
            <span class="section-text">{{ t('sideMenu.dataBuild') }}</span>
            <span class="expand-arrow" :class="{ open: openSections.dataBuild }">▸</span>
          </div>
          <div v-show="openSections.dataBuild" class="section-items">
            <a
              class="menu-item"
              :class="{ active: currentPage === 'dataEntry' }"
              @click="navigate('dataEntry')"
            >
              <span class="item-icon">📝</span>
              <span>{{ t('sideMenu.dataEntry') }}</span>
            </a>
            <a
              class="menu-item"
              :class="{ active: currentPage === 'dataPrep' }"
              @click="navigate('dataPrep')"
            >
              <span class="item-icon">🔧</span>
              <span>{{ t('sideMenu.dataPrep') }}</span>
            </a>
            <a
              class="menu-item"
              :class="{ active: currentPage === 'dataset' }"
              @click="navigate('dataset')"
            >
              <span class="item-icon">🗃️</span>
              <span>{{ t('sideMenu.dataset') }}</span>
            </a>
            <a
              class="menu-item"
              :class="{ active: currentPage === 'datasource' }"
              @click="navigate('datasource')"
            >
              <span class="item-icon">💾</span>
              <span>{{ t('sideMenu.datasource') }}</span>
            </a>
          </div>
        </div>

        <!-- 空间成员与信息 -->
        <div class="menu-section">
          <div class="section-header" @click="toggleSection('spaceInfo')">
            <span class="section-icon">👥</span>
            <span class="section-text">{{ t('sideMenu.spaceInfo') }}</span>
            <span class="expand-arrow" :class="{ open: openSections.spaceInfo }">▸</span>
          </div>
          <div v-show="openSections.spaceInfo" class="section-items">
            <a
              class="menu-item"
              :class="{ active: currentPage === 'memberPermission' }"
              @click="navigate('memberPermission')"
            >
              <span class="item-icon">🔐</span>
              <span>{{ t('sideMenu.memberPermission') }}</span>
            </a>
            <a
              class="menu-item"
              :class="{ active: currentPage === 'budgetAnalysis' }"
              @click="navigate('budgetAnalysis')"
            >
              <span class="item-icon">💰</span>
              <span>{{ t('sideMenu.budgetAnalysis') }}</span>
            </a>
            <a
              class="menu-item"
              :class="{ active: currentPage === 'dimensionAnalysis' }"
              @click="navigate('dimensionAnalysis')"
            >
              <span class="item-icon">📐</span>
              <span>{{ t('sideMenu.dimensionAnalysis') }}</span>
            </a>
            <a
              class="menu-item"
              :class="{ active: currentPage === 'quickEngine' }"
              @click="navigate('quickEngine')"
            >
              <span class="item-icon">⚡</span>
              <span>{{ t('sideMenu.quickEngine') }}</span>
            </a>
            <a
              class="menu-item"
              :class="{ active: currentPage === 'selfCheck' }"
              @click="navigate('selfCheck')"
            >
              <span class="item-icon">✅</span>
              <span>{{ t('sideMenu.selfCheck') }}</span>
            </a>
            <a
              class="menu-item"
              :class="{ active: currentPage === 'recycleBin' }"
              @click="navigate('recycleBin')"
            >
              <span class="item-icon">🗑️</span>
              <span>{{ t('sideMenu.recycleBin') }}</span>
            </a>
          </div>
        </div>
      </template>

      <!-- 折叠状态图标菜单 -->
      <div v-else class="collapsed-menu">
        <div class="collapsed-item" title="数据源" @click="navigate('datasource')">💾</div>
        <div class="collapsed-item" title="仪表板" @click="navigate('dashboard')">📈</div>
        <div class="collapsed-item" title="电子表格" @click="navigate('spreadsheet')">📋</div>
        <div class="collapsed-item" title="数据大屏" @click="navigate('dataScreen')">🖥️</div>
        <div class="collapsed-item" title="即席分析" @click="navigate('adHocAnalysis')">📝</div>
        <div class="spacer"></div>
        <div class="collapsed-item expand-trigger" title="展开" @click="toggleCollapse">▶</div>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { reactive, ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'

const props = withDefaults(defineProps<{
  collapsed?: boolean
  activePage?: string
}>(), {
  collapsed: false,
  activePage: '',
})

const emit = defineEmits<{
  (e: 'update:collapsed', value: boolean): void
  (e: 'page-change', page: string): void
}>()

const { t } = useI18n()

/** 展开的分组 */
const openSections = reactive({
  workspace: true,
  dataAnalysis: false,
  dataBuild: true,
  spaceInfo: false,
})

/** 内部页面状态（用于用户点击时的本地更新） */
const internalPage = ref('')

/** 当前页面（优先使用 prop，其次使用内部状态） */
const currentPage = computed(() => props.activePage || internalPage.value)

/** 切换分组展开/折叠 */
function toggleSection(section: keyof typeof openSections): void {
  openSections[section] = !openSections[section]
}

/** 切换侧栏折叠 */
function toggleCollapse(): void {
  emit('update:collapsed', !props.collapsed)
}

/** 返回工作台 */
function goBack(): void {
  emit('page-change', 'workbench')
}

/** 导航到页面 */
function navigate(page: string): void {
  internalPage.value = page
  emit('page-change', page)
}
</script>

<style scoped>
.side-menu {
  width: 200px;
  min-width: 200px;
  background: #fff;
  border-right: 1px solid #e5e6eb;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  transition: width 0.25s ease, min-width 0.25s ease;
  flex-shrink: 0;
}

.side-menu.collapsed {
  width: 52px;
  min-width: 52px;
}

.menu-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f1f3;
  flex-shrink: 0;
}

.back-link {
  font-size: 13px;
  color: #4e5969;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  transition: color 0.2s;
}

.back-link:hover {
  color: #165dff;
}

.back-icon {
  font-size: 10px;
}

.collapse-btn {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  font-size: 14px;
  cursor: pointer;
  color: #86909c;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: all 0.2s;
}

.collapse-btn:hover {
  background: #f2f3f5;
  color: #4e5969;
}

.menu-body {
  flex: 1;
  padding: 8px 0;
  overflow-y: auto;
}

.menu-section {
  margin-bottom: 2px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  font-size: 13px;
  font-weight: 500;
  color: #1d2129;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
}

.section-header:hover {
  background: #f7f8fa;
}

.section-header.workspace-header {
  color: #165dff;
}

.section-icon {
  font-size: 15px;
  flex-shrink: 0;
}

.section-text {
  flex: 1;
  font-size: 13px;
}

.dropdown-arrow {
  font-size: 10px;
  color: #86909c;
  transition: transform 0.2s;
}

.dropdown-arrow.open {
  transform: rotate(180deg);
}

.expand-arrow {
  margin-left: auto;
  font-size: 10px;
  color: #c9cdd4;
  transition: transform 0.2s;
}

.expand-arrow.open {
  transform: rotate(90deg);
}

.section-items {
  padding-left: 8px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px 8px 24px;
  font-size: 13px;
  color: #4e5969;
  cursor: pointer;
  border-radius: 4px;
  margin: 1px 8px;
  transition: all 0.15s;
  white-space: nowrap;
}

.menu-item:hover {
  background: #f2f3f5;
  color: #1d2129;
}

.menu-item.active {
  background: #e8f3ff;
  color: #165dff;
  font-weight: 500;
}

.item-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.collapsed-menu {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 0;
  gap: 4px;
}

.collapsed-item {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.15s;
}

.collapsed-item:hover {
  background: #f2f3f5;
}

.collapsed-item.expand-trigger {
  margin-top: auto;
  font-size: 12px;
  color: #86909c;
}

.spacer {
  flex: 1;
}
</style>
