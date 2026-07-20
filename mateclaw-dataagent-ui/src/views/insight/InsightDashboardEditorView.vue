<template>
  <div class="insight-editor-view">
    <!-- 顶部工具栏 -->
    <div class="editor-toolbar mc-toolbar">
      <div class="toolbar-left mc-toolbar-left">
        <el-button :icon="ArrowLeft" text @click="handleBack">{{ t('common.back') }}</el-button>
        <el-input
          v-model="dashboardName"
          class="toolbar-name-input"
          size="small"
          :placeholder="t('insight.editor')"
          @change="handleNameChange"
        />
        <el-input
          v-model="dashboardDescription"
          class="toolbar-desc-input"
          size="small"
          :placeholder="t('insight.description')"
          @change="handleDescriptionChange"
        />
        <el-input
          v-model="dashboardOwnerName"
          class="toolbar-owner-input"
          size="small"
          :placeholder="t('insight.ownerName')"
          @change="handleOwnerNameChange"
        />
      </div>
      <div class="toolbar-right mc-toolbar-right">
        <el-button @click="toggleAiChat">
          <template #icon>
            <RobotIcon style="width: 16px; height: 16px;" />
          </template>
          {{ t('insight.aiAssistant') }}
        </el-button>
        <el-button @click="handleSave" :loading="saving">{{ t('insight.save') }}</el-button>
        <el-button @click="handlePreview" :disabled="!dashboard">{{ t('insight.preview') }}</el-button>
      </div>
    </div>

    <!-- 四栏布局：页面菜单 | 物料面板 | 画布 | 属性面板 -->
    <div class="editor-body">
      <!-- 页面菜单树 -->
      <div class="editor-pages" :class="{ 'mobile-open': showMobilePages }">
        <div class="pages-header">
          <span class="pages-title">页面</span>
          <el-button text size="small" @click="addPage">+</el-button>
        </div>
        <div class="pages-list">
          <div
            v-for="page in sortedPages"
            :key="page.id"
            class="page-item"
            :class="{ active: page.id === activePageId }"
            :style="{ paddingLeft: (getPageDepth(page.id) * 12 + 12) + 'px' }"
            @click="handleSelectPage(page.id)"
          >
            <span v-if="page.icon" class="page-icon">{{ page.icon }}</span>
            <el-input
              v-if="editingPageId === page.id"
              v-model="editingPageName"
              size="small"
              autofocus
              class="page-name-input"
              @blur="handlePageNameBlur"
              @keyup.enter="handlePageNameBlur"
            />
            <span v-else class="page-name" @dblclick.stop="startEditPageName(page)">{{ page.name }}</span>
            <div v-if="editingPageId !== page.id" class="page-actions">
              <el-dropdown
                trigger="click"
                size="small"
                @command="(cmd) => handlePageAction(cmd, page)"
              >
                <el-button text size="small" @click.stop>
                  <el-icon><More /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="moveUp">
                      <el-icon><ArrowUp /></el-icon>上移
                    </el-dropdown-item>
                    <el-dropdown-item command="moveDown">
                      <el-icon><ArrowDown /></el-icon>下移
                    </el-dropdown-item>
                    <el-dropdown-item command="addSub">
                      <el-icon><Plus /></el-icon>添加子页面
                    </el-dropdown-item>
                    <el-dropdown-item command="rename">
                      <el-icon><Edit /></el-icon>重命名
                    </el-dropdown-item>
                    <el-dropdown-item command="delete" divided>
                      <el-icon><Delete /></el-icon>删除
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
        </div>
      </div>

      <!-- 物料面板 -->
      <div class="editor-palette" :class="{ 'mobile-open': showMobilePalette }">
        <ComponentPalette />
      </div>

      <!-- 画布 -->
      <div class="editor-canvas">
        <DashboardCanvas
          :components="currentPageComponents"
          :component-data-map="componentDataMap"
          :editable="true"
          :selected-id="selectedComponentId"
          @add-component="handleAddComponent"
          @update-layout="handleUpdateLayout"
          @select-component="handleSelectComponent"
          @delete-component="handleDeleteComponent"
        />
      </div>

      <!-- 右侧边栏：属性面板 + AI助手面板上下布局 -->
      <div class="editor-right-sidebar" :class="{ 'mobile-open': showMobileProperty || showAiChat }">
        <!-- 属性面板 -->
        <div class="editor-property" :class="{ 'mobile-open': showMobileProperty }">
          <PropertyPanel
            :component="selectedComponent"
            :all-components="currentPageComponents"
            @change="handleComponentChange"
            @preview="handlePreviewResult"
          />
        </div>

        <!-- AI助手面板 -->
        <div v-if="showAiChat" class="editor-ai-chat">
          <AiChatPanel
            :dashboard-id="dashboardId"
            @close="toggleAiChat"
            @dashboard-updated="handleAiDashboardUpdated"
          />
        </div>
      </div>

      <!-- 移动端面板切换栏 -->
      <div class="mobile-panel-bar">
        <el-button text size="small" @click="showMobilePages = !showMobilePages">
          <el-icon><Folder /></el-icon>
          <span class="mobile-bar-label">{{ t('insight.editorMobile.pages') }}</span>
        </el-button>
        <el-button text size="small" @click="showMobilePalette = !showMobilePalette">
          <el-icon><Plus /></el-icon>
          <span class="mobile-bar-label">{{ t('insight.editorMobile.components') }}</span>
        </el-button>
        <el-button text size="small" @click="showMobileProperty = !showMobileProperty">
          <el-icon><Setting /></el-icon>
          <span class="mobile-bar-label">{{ t('insight.editorMobile.properties') }}</span>
        </el-button>
      </div>

      <!-- 移动端面板遮罩 -->
      <div
        v-if="showMobilePages || showMobilePalette || showMobileProperty"
        class="mobile-panel-backdrop"
        @click="closeAllMobilePanels"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowUp, ArrowDown, ChatDotRound, Folder, Plus, Setting, More, Edit, Delete } from '@element-plus/icons-vue'
import RobotIcon from './components/RobotIcon.vue'
import type { InsightDashboardSchema, InsightComponent, InsightComponentType, ChartType, InsightComponentData, DashboardPage } from '@/types'
import { useInsightDashboardStore } from '@/stores/useInsightDashboardStore'
import * as insightDashboardApi from '@/api/insight-dashboard'
import ComponentPalette from './components/ComponentPalette.vue'
import DashboardCanvas from './components/DashboardCanvas.vue'
import PropertyPanel from './components/PropertyPanel.vue'
import AiChatPanel from './components/AiChatPanel.vue'

defineOptions({
  name: 'InsightDashboardEditorView',
})

const props = defineProps<{
  /** 仪表盘 ID */
  dashboardId: string
}>()

const emit = defineEmits<{
  (e: 'back'): void
  (e: 'preview', dashboardId: string): void
}>()

const { t } = useI18n()
const store = useInsightDashboardStore()

const dashboard = computed(() => store.currentDashboard)
const saving = ref(false)
const selectedComponentId = ref<string>('')
const dashboardName = ref('')
const dashboardDescription = ref('')
const dashboardOwnerName = ref('')

/** AI对话面板可见性 */
const showAiChat = ref(false)

/** 移动端面板可见性 */
const showMobilePages = ref(false)
const showMobilePalette = ref(false)
const showMobileProperty = ref(false)

/** 本地 Schema 副本 */
const schema = reactive<InsightDashboardSchema>({
  version: '1.0',
  pages: [],
})

/** 组件渲染数据映射（编辑模式自动预览） */
const componentDataMap = ref<Record<string, InsightComponentData>>({})

/** 当前激活的页面 ID */
const activePageId = ref<string>('')

/** 正在编辑名称的页面 ID */
const editingPageId = ref<string>('')
const editingPageName = ref<string>('')

/** 预览防抖定时器 */
let previewTimer: ReturnType<typeof setTimeout> | null = null

/** 排序后的页面列表（按 order 排序，支持树形缩进展示） */
const sortedPages = computed<DashboardPage[]>(() => {
  const sorted = [...schema.pages].sort((a, b) => {
    const orderDiff = (a.order ?? 0) - (b.order ?? 0)
    if (orderDiff !== 0) return orderDiff
    return a.name.localeCompare(b.name)
  })
  return sorted
})

/** 获取页面在树中的深度（用于缩进） */
function getPageDepth(pageId: string): number {
  let depth = 0
  let page = schema.pages.find((p) => p.id === pageId)
  while (page?.parentId) {
    depth++
    page = schema.pages.find((p) => p.id === page!.parentId)
    if (depth > 20) break // 防止循环引用
  }
  return depth
}

/** 当前激活页面的组件列表 */
const currentPageComponents = computed<InsightComponent[]>(() => {
  const page = schema.pages.find((p) => p.id === activePageId.value)
  return page?.components ?? []
})

/** 当前选中的组件 */
const selectedComponent = computed<InsightComponent | null>(() => {
  if (!selectedComponentId.value) {
    return null
  }
  return currentPageComponents.value.find((c) => c.id === selectedComponentId.value) ?? null
})

onMounted(async () => {
  await loadDashboard(props.dashboardId)
})

/** 监听 dashboardId 变化时重新加载 */
watch(
  () => props.dashboardId,
  async (newId) => {
    if (newId) {
      await loadDashboard(newId)
    }
  }
)

/** 生成 ID */
function generateId(prefix: string): string {
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`
}

/** 迁移旧 Schema（单 components 数组 → pages[0]） */
function migrateSchema(parsed: any): InsightDashboardSchema {
  // 新格式：已有 pages 数组
  if (parsed.pages && Array.isArray(parsed.pages)) {
    return parsed as InsightDashboardSchema
  }
  // 旧格式：components + perspectives，迁移为单页面
  const oldComponents = parsed.components ?? []
  return {
    version: parsed.version ?? '1.0',
    pages: [{
      id: generateId('page'),
      name: t('insight.firstPageName'),
      components: oldComponents,
    }],
  }
}

/** 加载仪表盘数据 */
async function loadDashboard(id: string): Promise<void> {
  await store.selectDashboard(id)
  if (dashboard.value) {
    dashboardName.value = dashboard.value.name
    dashboardDescription.value = dashboard.value.description ?? ''
    dashboardOwnerName.value = dashboard.value.ownerName ?? ''
    try {
      const parsed = JSON.parse(dashboard.value.schemaJson)
      const migrated = migrateSchema(parsed)
      schema.version = migrated.version
      schema.pages = migrated.pages
    } catch {
      // Schema 解析失败时使用空 Schema（含一个默认页面）
      schema.pages = [{
        id: generateId('page'),
        name: t('insight.firstPageName'),
        components: [],
      }]
    }
    // 默认选中第一个页面
    if (schema.pages.length > 0) {
      activePageId.value = schema.pages[0].id
    }
  }
}

/** 生成默认标题 */
function getDefaultTitle(type: InsightComponentType, chartType?: ChartType): string {
  const titleMap: Record<string, string> = {
    kpi: t('insight.component.kpi'),
    'chart-line': t('insight.component.line'),
    'chart-bar': t('insight.component.bar'),
    'chart-pie': t('insight.component.pie'),
    'chart-area': t('insight.component.area'),
    'chart-scatter': t('insight.component.scatter'),
    'chart-radar': t('insight.component.radar'),
    table: t('insight.component.table'),
    filter: t('insight.component.filter'),
    timeFilter: t('insight.component.timeFilter'),
    aiAnalysis: t('insight.component.aiAnalysis'),
  }
  const key = type === 'chart' && chartType ? `chart-${chartType}` : type
  return titleMap[key] ?? type
}

/** 添加新组件到当前页面 */
function handleAddComponent(payload: { type: InsightComponentType; chartType?: ChartType }): void {
  const page = schema.pages.find((p) => p.id === activePageId.value)
  if (!page) {
    return
  }
  const maxY = page.components.reduce((max, c) => Math.max(max, c.position.y + c.position.h), 0)
  const newComponent: InsightComponent = {
    id: generateId('comp'),
    type: payload.type,
    title: getDefaultTitle(payload.type, payload.chartType),
    position: { x: 0, y: maxY, w: 6, h: 4 },
    chartType: payload.chartType,
    dataSource: payload.type !== 'filter' && payload.type !== 'timeFilter' && payload.type !== 'aiAnalysis' ? {
      datasourceId: '',
      metrics: [],
      dimensions: [],
      filters: [],
      limit: 100,
    } : undefined,
    config: payload.type === 'timeFilter' ? {
      field: 'metric_time',
      availablePresets: ['today', '7d', '30d', '90d', 'custom'],
    } : payload.type === 'aiAnalysis' ? {
      autoGenerate: false,
    } : undefined,
  }
  page.components.push(newComponent)
  selectedComponentId.value = newComponent.id
}

/** 更新布局（拖动/缩放后） */
function handleUpdateLayout(layout: Array<{ id: string; x: number; y: number; w: number; h: number }>): void {
  const page = schema.pages.find((p) => p.id === activePageId.value)
  if (!page) {
    return
  }
  layout.forEach((item) => {
    const comp = page.components.find((c) => c.id === item.id)
    if (comp) {
      comp.position = { x: item.x, y: item.y, w: item.w, h: item.h }
    }
  })
}

/** 选中组件 */
function handleSelectComponent(id: string): void {
  selectedComponentId.value = id
}

/** 删除组件 */
function handleDeleteComponent(id: string): void {
  const page = schema.pages.find((p) => p.id === activePageId.value)
  if (!page) {
    return
  }
  const idx = page.components.findIndex((c) => c.id === id)
  if (idx >= 0) {
    page.components.splice(idx, 1)
    if (selectedComponentId.value === id) {
      selectedComponentId.value = ''
    }
  }
}

/** 组件属性变更（保留画布管理的 position） */
function handleComponentChange(updated: InsightComponent): void {
  const page = schema.pages.find((p) => p.id === activePageId.value)
  if (!page) {
    return
  }
  const idx = page.components.findIndex((c) => c.id === updated.id)
  if (idx >= 0) {
    const existing = page.components[idx]
    page.components[idx] = {
      ...updated,
      // 保留画布拖拽/缩放管理的 position，不被属性面板覆盖
      position: existing.position,
    }
  }
  // 数据源变更时触发自动预览
  schedulePreview()
}

/** 处理属性面板验证数据结果，写入 componentDataMap 让画布组件渲染 */
function handlePreviewResult(data: InsightComponentData): void {
  if (data.componentId) {
    componentDataMap.value[data.componentId] = data
  }
}

/** 延迟预览：数据源变更后 500ms 自动获取组件数据 */
function schedulePreview(): void {
  if (previewTimer) {
    clearTimeout(previewTimer)
  }
  previewTimer = setTimeout(() => {
    previewAllConfiguredComponents()
  }, 500)
}

/** 为当前页面所有已配置数据源的组件获取预览数据 */
async function previewAllConfiguredComponents(): Promise<void> {
  const page = schema.pages.find((p) => p.id === activePageId.value)
  if (!page) {
    return
  }
  const tasks = page.components
    .filter((c) => c.type !== 'filter' && c.type !== 'timeFilter' && c.dataSource?.datasourceId && c.dataSource?.metrics?.length)
    .map(async (c) => {
      try {
        const result = await insightDashboardApi.previewComponent(c) as unknown as InsightComponentData
        componentDataMap.value[c.id] = result
      } catch (e: any) {
        componentDataMap.value[c.id] = {
          componentId: c.id,
          renderType: 'table',
          error: e.message ?? t('insight.previewFailed'),
        }
      }
    })
  await Promise.allSettled(tasks)
}

/** 保存仪表盘 */
async function handleSave(): Promise<void> {
  if (!dashboard.value) {
    return
  }
  saving.value = true
  try {
    await store.updateDashboard(dashboard.value.id, {
      name: dashboardName.value,
      description: dashboardDescription.value,
      ownerName: dashboardOwnerName.value,
      schemaJson: JSON.stringify(schema),
    })
    ElMessage.success(t('insight.saveSuccess'))
  } catch {
    ElMessage.error(t('insight.saveFailed'))
  } finally {
    saving.value = false
  }
}

/** 名称变更时自动保存 */
function handleNameChange(): void {
  if (dashboard.value && dashboardName.value.trim()) {
    store.updateDashboard(dashboard.value.id, { name: dashboardName.value.trim() }).catch(() => {
      // 静默失败
    })
  }
}

/** 描述变更时自动保存 */
function handleDescriptionChange(): void {
  if (dashboard.value) {
    store.updateDashboard(dashboard.value.id, { description: dashboardDescription.value }).catch(() => {
      // 静默失败
    })
  }
}

/** 负责人变更时自动保存 */
function handleOwnerNameChange(): void {
  if (dashboard.value) {
    store.updateDashboard(dashboard.value.id, { ownerName: dashboardOwnerName.value }).catch(() => {
      // 静默失败
    })
  }
}

/** 预览 */
function handlePreview(): void {
  handleSave().then(() => {
    emit('preview', props.dashboardId)
  })
}

/** 返回列表 */
function handleBack(): void {
  emit('back')
}

/** 切换AI对话面板 */
function toggleAiChat(): void {
  showAiChat.value = !showAiChat.value
}

/** 关闭所有移动端面板 */
function closeAllMobilePanels(): void {
  showMobilePages.value = false
  showMobilePalette.value = false
  showMobileProperty.value = false
}

/** AI助手修改后刷新Schema */
async function handleAiDashboardUpdated(): Promise<void> {
  if (!dashboard.value) {
    return
  }
  // 重新从后端加载仪表盘数据
  await store.selectDashboard(dashboard.value.id)
  if (dashboard.value) {
    try {
      const parsed = JSON.parse(dashboard.value.schemaJson)
      const migrated = migrateSchema(parsed)
      schema.version = migrated.version
      schema.pages = migrated.pages
    } catch {
      // Schema解析失败时保持当前状态
    }
    // 刷新组件预览数据
    schedulePreview()
  }
}

/** 选中页面 */
function handleSelectPage(pageId: string): void {
  activePageId.value = pageId
  selectedComponentId.value = ''
}

/** 添加顶级页面 */
function addPage(): void {
  const newPage: DashboardPage = {
    id: generateId('page'),
    name: t('insight.pageDefaultName', { index: schema.pages.length + 1 }),
    components: [],
  }
  schema.pages.push(newPage)
  activePageId.value = newPage.id
  selectedComponentId.value = ''
}

/** 添加子页面 */
function addSubPage(parent: DashboardPage): void {
  const newPage: DashboardPage = {
    id: generateId('page'),
    name: t('insight.subPageDefaultName', { index: schema.pages.length + 1 }),
    parentId: parent.id,
    components: [],
  }
  schema.pages.push(newPage)
  activePageId.value = newPage.id
  selectedComponentId.value = ''
}

/** 删除页面 */
function deletePage(pageId: string): void {
  // 同时删除所有子页面
  const toDelete = new Set<string>([pageId])
  let changed = true
  while (changed) {
    changed = false
    schema.pages.forEach((p) => {
      if (p.parentId && toDelete.has(p.parentId) && !toDelete.has(p.id)) {
        toDelete.add(p.id)
        changed = true
      }
    })
  }
  schema.pages = schema.pages.filter((p) => !toDelete.has(p.id))
  // 如果当前激活页面被删除，切换到第一个页面
  if (toDelete.has(activePageId.value)) {
    activePageId.value = schema.pages[0]?.id ?? ''
    selectedComponentId.value = ''
  }
}

/** 开始编辑页面名称 */
function startEditPageName(page: DashboardPage): void {
  editingPageId.value = page.id
  editingPageName.value = page.name
}

/** 完成编辑页面名称 */
function handlePageNameBlur(): void {
  if (editingPageId.value) {
    const page = schema.pages.find((p) => p.id === editingPageId.value)
    if (page && editingPageName.value.trim()) {
      page.name = editingPageName.value.trim()
    }
  }
  editingPageId.value = ''
  editingPageName.value = ''
}

/** 获取同级页面列表（按 order 排序） */
function getSiblings(page: DashboardPage): DashboardPage[] {
  return schema.pages
    .filter((p) => (p.parentId ?? '') === (page.parentId ?? ''))
    .sort((a, b) => (a.order ?? 0) - (b.order ?? 0))
}

/** 重新分配同级页面的 order 值（确保连续递增，无冲突） */
function reorderSiblings(parentId: string | undefined): void {
  const pid = parentId ?? ''
  const siblings = schema.pages.filter((p) => (p.parentId ?? '') === pid)
  siblings.sort((a, b) => (a.order ?? 0) - (b.order ?? 0))
  siblings.forEach((p, idx) => {
    p.order = idx
  })
}

/** 页面上移（在同级中上移一位） */
function movePageUp(page: DashboardPage): void {
  reorderSiblings(page.parentId)
  const siblings = getSiblings(page)
  const idx = siblings.findIndex((p) => p.id === page.id)
  if (idx <= 0) {
    return
  }
  const prevPage = siblings[idx - 1]
  const temp = page.order ?? 0
  page.order = prevPage.order ?? 0
  prevPage.order = temp
}

/** 页面下移（在同级中下移一位） */
function movePageDown(page: DashboardPage): void {
  reorderSiblings(page.parentId)
  const siblings = getSiblings(page)
  const idx = siblings.findIndex((p) => p.id === page.id)
  if (idx < 0 || idx >= siblings.length - 1) {
    return
  }
  const nextPage = siblings[idx + 1]
  const temp = page.order ?? 0
  page.order = nextPage.order ?? 0
  nextPage.order = temp
}

/** 处理页面操作下拉菜单命令 */
function handlePageAction(cmd: string, page: DashboardPage): void {
  switch (cmd) {
    case 'moveUp':
      movePageUp(page)
      break
    case 'moveDown':
      movePageDown(page)
      break
    case 'addSub':
      addSubPage(page)
      break
    case 'rename':
      startEditPageName(page)
      break
    case 'delete':
      deletePage(page.id)
      break
  }
}
</script>

<style scoped>
.insight-editor-view {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--db-bg);
  overflow: hidden;
}

.editor-toolbar {
  min-height: 48px;
}

.toolbar-left {
  gap: var(--space-md);
}

.toolbar-name-input {
  width: 200px;
  font-size: 15px;
  font-weight: 600;

  :deep(.el-input__wrapper) {
    background: transparent;
    box-shadow: none;
    padding: 0 4px;
  }

  :deep(.el-input__wrapper:hover),
  :deep(.el-input__wrapper.is-focus) {
    box-shadow: 0 0 0 1px var(--db-border) inset;
  }

  :deep(.el-input__inner) {
    color: var(--db-text);
    font-weight: 600;
  }
}

.toolbar-desc-input {
  width: 300px;
  font-size: 13px;

  :deep(.el-input__wrapper) {
    background: transparent;
    box-shadow: none;
    padding: 0 4px;
  }

  :deep(.el-input__wrapper:hover),
  :deep(.el-input__wrapper.is-focus) {
    box-shadow: 0 0 0 1px var(--db-border) inset;
  }

  :deep(.el-input__inner) {
    color: var(--db-text-secondary);
  }
}

.toolbar-owner-input {
  width: 120px;
  font-size: 13px;

  :deep(.el-input__wrapper) {
    background: transparent;
    box-shadow: none;
    padding: 0 4px;
  }

  :deep(.el-input__wrapper:hover),
  :deep(.el-input__wrapper.is-focus) {
    box-shadow: 0 0 0 1px var(--db-border) inset;
  }

  :deep(.el-input__inner) {
    color: var(--db-text-secondary);
  }
}

.toolbar-right {
  gap: var(--space-sm);
}

.editor-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.editor-pages {
  width: 200px;
  flex-shrink: 0;
  overflow-y: auto;
  background: var(--db-card);
  border-right: 1px solid var(--db-border);
  display: flex;
  flex-direction: column;
  animation: fadeIn var(--transition-base) both;
}

.pages-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-sm) var(--space-md);
  border-bottom: 1px solid var(--db-border);
  flex-shrink: 0;
}

.pages-title {
  font-size: 13px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--db-text-secondary);
}

.pages-list {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-xs) 0;
}

.page-item {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  margin: 0 var(--space-xs);
  padding: var(--space-xs) var(--space-sm);
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 13px;
  color: var(--db-text-secondary);
  transition: color var(--transition-fast), background var(--transition-fast);
  white-space: nowrap;
}

.page-item:hover {
  background: var(--db-hover);
  color: var(--db-text);
}

.page-item.active {
  background: color-mix(in srgb, var(--db-accent) 10%, transparent);
  color: var(--db-accent);
  font-weight: 500;
}

.page-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.page-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.page-name-input {
  flex: 1;
  min-width: 0;
}

.page-actions {
  display: none;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.page-item:hover .page-actions {
  display: flex;
}

.page-actions :deep(.el-button) {
  padding: 2px;
  font-size: 11px;
  color: var(--db-text-muted);
  min-width: 20px;
  height: 20px;
}

.page-actions :deep(.el-button:hover) {
  color: var(--db-text);
}

.editor-palette {
  width: 200px;
  flex-shrink: 0;
  overflow: hidden;
  animation: fadeIn var(--transition-base) both;
}

.editor-canvas {
  flex: 1;
  overflow: hidden;
  background: var(--db-bg);
}

.editor-right-sidebar {
  width: 340px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: fadeIn var(--transition-base) both;
}

.editor-property {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  animation: fadeIn var(--transition-base) both;
}

.editor-ai-chat {
  flex: 2;
  min-height: 0;
  overflow: hidden;
  border-top: 1px solid var(--db-border);
  border-left: 1px solid var(--db-border);
  animation: fadeIn var(--transition-base) both;
}

@media (max-width: 1279px) {
  .editor-pages,
  .editor-palette {
    width: 56px;
  }

  .pages-header {
    justify-content: center;
    padding: var(--space-sm);
  }

  .pages-title,
  .page-name,
  .page-actions {
    display: none;
  }

  .page-item {
    justify-content: center;
    padding: var(--space-sm);
  }

  .editor-palette :deep(.palette-header),
  .editor-palette :deep(.palette-label) {
    display: none;
  }

  .editor-palette :deep(.palette-list) {
    align-items: center;
  }

  .editor-palette :deep(.palette-item) {
    justify-content: center;
    padding: var(--space-sm);
  }
}

@media (max-width: 1023px) {
  .editor-right-sidebar {
    position: absolute;
    right: 0;
    top: 48px;
    bottom: 0;
    z-index: 100;
    box-shadow: var(--shadow-dropdown);
  }

  .editor-ai-chat {
    border-top: 1px solid var(--db-border);
  }
}

.mobile-panel-bar,
.mobile-panel-backdrop {
  display: none;
}

@media (max-width: 767px) {
  .editor-toolbar {
    padding: var(--space-sm);
    min-height: auto;
    flex-wrap: wrap;
    gap: var(--space-sm);
  }

  .toolbar-left,
  .toolbar-right {
    width: 100%;
  }

  .toolbar-right {
    justify-content: flex-end;
  }

  .toolbar-name-input,
  .toolbar-desc-input,
  .toolbar-owner-input {
    width: auto;
    flex: 1;
  }

  .editor-body {
    position: relative;
  }

  .editor-pages,
  .editor-palette,
  .editor-right-sidebar {
    position: absolute;
    top: 0;
    bottom: 48px;
    z-index: 100;
    box-shadow: var(--shadow-dropdown);
    transition: transform var(--transition-base);
  }

  .editor-pages {
    left: 0;
    width: 72%;
    transform: translateX(-100%);
  }

  .editor-pages.mobile-open {
    transform: translateX(0);
  }

  .editor-palette {
    left: 0;
    width: 72%;
    transform: translateX(-100%);
  }

  .editor-palette.mobile-open {
    transform: translateX(0);
  }

  .editor-right-sidebar {
    right: 0;
    width: 80%;
    transform: translateX(100%);
  }

  .editor-right-sidebar.mobile-open {
    transform: translateX(0);
  }

  .mobile-panel-bar {
    display: flex;
    align-items: center;
    justify-content: space-around;
    padding: var(--space-xs) 0;
    background: var(--db-hover);
    border-top: 1px solid var(--db-border);
    flex-shrink: 0;
    position: relative;
    z-index: 99;
  }

  .mobile-panel-bar :deep(.el-button) {
    display: inline-flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
    padding: var(--space-xs) var(--space-sm);
    height: auto;
    line-height: 1.2;
  }

  .mobile-bar-label {
    font-size: 11px;
  }

  .mobile-panel-backdrop {
    display: block;
    position: absolute;
    inset: 0;
    background: rgba(0, 0, 0, 0.25);
    z-index: 95;
    animation: fadeIn var(--transition-fast) both;
  }
}
</style>
