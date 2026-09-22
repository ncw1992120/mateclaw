<template>
  <div class="insight-editor-view" @click="closeComponentContextMenu">
    <!-- 顶部工具栏 -->
    <div class="editor-toolbar mc-toolbar">
      <div class="toolbar-left mc-toolbar-left">
        <button type="button" class="back-btn" :title="t('common.back')" :aria-label="t('common.back')" @click="handleBack">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg>
        </button>
        <div class="toolbar-title-block">
          <input
            v-if="editingName"
            v-model="dashboardName"
            class="toolbar-name-input"
            :aria-label="t('insight.editor')"
            :placeholder="t('insight.editor')"
            autofocus
            @blur="commitName"
            @keydown.enter.prevent="blurTarget"
          />
          <h2
            v-else
            class="toolbar-title"
            :class="{ placeholder: !dashboardName }"
            tabindex="0"
            :title="t('insight.editor')"
            @click="editingName = true"
            @keydown.enter.prevent="startNameEditing"
            @keydown.space.prevent="startNameEditing"
          >
            {{ dashboardName || t('insight.editor') }}
          </h2>
          <input
            v-if="editingDesc"
            v-model="dashboardDescription"
            class="toolbar-desc-input"
            :aria-label="t('insight.description')"
            :placeholder="t('insight.description')"
            autofocus
            @blur="commitDesc"
            @keydown.enter.prevent="blurTarget"
          />
          <div
            v-else
            class="toolbar-subtitle"
            :class="{ placeholder: !dashboardDescription }"
            role="button"
            tabindex="0"
            aria-label="编辑仪表盘描述"
            @click="editingDesc = true"
            @keydown.enter="editingDesc = true"
            @keydown.space.prevent="editingDesc = true"
          >
            {{ dashboardDescription || t('insight.description') }}
          </div>
        </div>
      </div>
      <div class="toolbar-right mc-toolbar-right">
        <el-input
          v-model="dashboardOwnerName"
          class="toolbar-owner-input"
          :aria-label="t('insight.ownerName')"
          size="small"
          :placeholder="t('insight.ownerName')"
          @change="handleOwnerNameChange"
        />
        <span class="toolbar-separator"></span>
        <el-button class="ai-assistant-btn toolbar-btn" :class="{ on: showAiChat }" @click="toggleAiChat">
          <template #icon>
            <RobotIcon style="width: 16px; height: 16px;" />
          </template>
          {{ t('insight.aiAssistant') }}
        </el-button>
        <el-button class="toolbar-btn" aria-label="主题外观" @click="showThemePanel = true">主题外观</el-button>
        <el-button class="toolbar-btn" @click="handleSave" :loading="saving">
          <template #icon><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg></template>
          {{ t('insight.save') }}
        </el-button>
        <el-button type="primary" class="toolbar-btn" @click="handlePreview" :disabled="!dashboard">
          <template #icon><el-icon><View /></el-icon></template>
          {{ t('insight.preview') }}
        </el-button>
      </div>
    </div>

    <!-- 四栏布局：页面菜单 | 物料面板 | 画布 | 属性面板 -->
    <div class="editor-body">
      <!-- 页面菜单树 -->
      <div v-if="!pagesCollapsed" class="editor-pages" :class="{ 'mobile-open': showMobilePages }">
        <div class="pages-header">
          <span class="pages-title">页面</span>
          <div class="pages-header-actions">
            <el-button text size="small" aria-label="新增页面" @click="addPage">+</el-button>
            <button type="button" class="panel-collapse-btn" title="收起面板" aria-label="收起面板" @click="pagesCollapsed = true">
              <el-icon :size="14"><Fold /></el-icon>
            </button>
          </div>
        </div>
        <div class="pages-list">
          <el-tree
            ref="pageTreeRef"
            :data="treeData"
            :props="{ children: 'children', label: 'name' }"
            node-key="id"
            :current-node-key="activePageId"
            highlight-current
            :expand-on-click-node="false"
            :default-expand-all="true"
            @node-click="handleTreeNodeClick"
          >
            <template #default="{ data: node }">
              <div class="page-node">
                <span v-if="node.icon" class="page-icon">{{ node.icon }}</span>
                <el-input
                  v-if="editingPageId === node.id"
                  v-model="editingPageName"
                  size="small"
                  autofocus
                  class="page-name-input"
                  @blur="handlePageNameBlur"
                  @keyup.enter="handlePageNameBlur"
                  @click.stop
                />
                <span v-else class="page-name" @dblclick.stop="startEditPageName(node)">{{ node.name }}</span>
                <div v-if="editingPageId !== node.id" class="page-actions">
                  <el-dropdown
                    trigger="click"
                    size="small"
                    @command="(cmd: string) => handlePageAction(cmd, node)"
                  >
                    <el-button text size="small" aria-label="页面操作" @click.stop>
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
                        <el-dropdown-item command="copy">
                          <el-icon><DocumentCopy /></el-icon>复制页面
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
            </template>
          </el-tree>
        </div>
      </div>
      <!-- 页面面板折叠态：悬浮按钮 -->
      <PanelFloatButton
        v-if="pagesCollapsed"
        label="页面"
        side="left"
        :default-top-percent="38"
        @expand="pagesCollapsed = false"
      />

      <!-- 物料面板 -->
      <div v-if="!paletteCollapsed" class="editor-palette" :class="{ 'mobile-open': showMobilePalette }">
        <ComponentPalette @collapse="paletteCollapsed = true" @add-component="handleAddComponent" />
      </div>
      <!-- 物料面板折叠态：悬浮按钮 -->
      <PanelFloatButton
        v-if="paletteCollapsed"
        :label="t('insight.paletteTitle')"
        side="left"
        :default-top-percent="55"
        @expand="paletteCollapsed = false"
      />

      <!-- 画布 -->
      <div class="editor-canvas">
        <DashboardCanvas
          :components="currentPageComponents"
          :component-data-map="componentDataMap"
          :editable="true"
          :dashboard-theme="dashboardTheme"
          :selected-id="selectedComponentId"
          @add-component="handleAddComponent"
          @update-layout="handleUpdateLayout"
          @select-component="handleSelectComponent"
          @select-child="handleSelectChild"
          @combination-add-tab="handleCombinationAddTab"
          @combination-remove-tab="handleCombinationRemoveTab"
          @delete-component="handleDeleteComponent"
          @copy-component="handleCopyComponent"
          @paste-component="handlePasteComponent"
          @context-menu="handleComponentContextMenu"
          @open-metric-style="handleOpenMetricStyle"
        />
      </div>

      <!-- 右侧边栏：属性面板 + AI助手面板上下布局 -->
      <div v-if="!sidebarCollapsed" class="editor-right-sidebar" :class="{ 'mobile-open': showMobileProperty || showAiChat }">
        <!-- 属性面板 -->
        <div class="editor-property" :class="{ 'mobile-open': showMobileProperty }">
          <!-- 数据组件（kpi/chart/table）：原型版「卡片属性配置」面板（docs/策略解读/原型设计.md §10~§12 唯一交互依据） -->
          <!-- combination（组合卡片容器）与筛选类组件走 PropertyPanel（容器配置/页签管理在其内部） -->
          <CardAttributeSidebar
            v-if="panelComponent && !['filter', 'timeFilter', 'aiAnalysis', 'combination'].includes(panelComponent.type)"
            :component="panelComponent"
            :dashboard-id="dashboardId"
            :execution-policy="schema.executionPolicy"
            :filter-components="filterComponents"
            @change="handleComponentChange"
            @resultset="handleComponentResultSet"
          />
          <!-- 筛选/时间筛选/AI分析组件：沿用正式属性面板 -->
          <PropertyPanel
            v-else
            :component="panelComponent"
            :all-components="currentPageComponents"
            :use-dataset-pipeline="false"
            @change="handleComponentChange"
            @preview="handlePreviewResult"
            @combination-add-tab="handleCombinationAddTabFromPanel"
            @combination-remove-tab="handleCombinationRemoveTabFromPanel"
            @collapse="sidebarCollapsed = true"
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
      <!-- 右侧边栏折叠态：悬浮按钮 -->
      <PanelFloatButton
        v-if="sidebarCollapsed"
        label="属性"
        side="right"
        :default-top-percent="45"
        @expand="sidebarCollapsed = false"
      />

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

      <!-- 「筛选预览」弹窗：定义 / 筛选条件 / 结果（条件由用户添加后点查询下推） -->
      <DatasetDataDialog v-if="dataDialogDataset" :dataset="dataDialogDataset" />

      <DashboardThemePanel
        ref="themePanelRef"
        :model-value="schema.theme ?? { mode: 'preset', presetId: 'blue' }"
        :visible="showThemePanel"
        @update:model-value="updateThemeDraft"
        @update:visible="showThemePanel = $event"
        @confirm-theme-switch="confirmThemeSwitch"
      />

      <!-- 移动端面板遮罩 -->
      <div
        v-if="showMobilePages || showMobilePalette || showMobileProperty"
        class="mobile-panel-backdrop"
        @click="closeAllMobilePanels"
      />

      <!-- 画布组件右键菜单：复制当前组件或粘贴到当前页面 -->
      <div
        v-if="componentContextMenu"
        class="component-context-menu"
        :style="componentContextMenuStyle"
        role="menu"
        @click.stop
        @contextmenu.stop.prevent
      >
        <button
          v-if="componentContextMenu.componentId"
          type="button"
          role="menuitem"
          @click="copyFromContextMenu"
        >
          <span>复制组件</span><kbd>⌘ / Ctrl + C</kbd>
        </button>
        <button
          type="button"
          role="menuitem"
          :disabled="!clipboardComponent"
          @click="pasteFromContextMenu"
        >
          <span>粘贴组件</span><kbd>⌘ / Ctrl + V</kbd>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowUp, ArrowDown, ChatDotRound, DocumentCopy, Folder, Plus, Setting, More, Edit, Delete, View, Fold } from '@element-plus/icons-vue'
import RobotIcon from './components/RobotIcon.vue'
import type { InsightDashboardSchema, InsightComponent, InsightComponentType, InsightCombinationChild, InsightCombinationConfig, ChartType, InsightComponentData, DashboardPage } from '@/types'
import type { PanelFilterComponent } from './components/card-attribute/useCardAttributeBridge'
import { useInsightDashboardStore } from '@/stores/useInsightDashboardStore'
import { useUserStore } from '@/stores/useUserStore'
import { usePermission } from '@/composables/usePermission'
import * as insightDashboardApi from '@/api/insight-dashboard'
import ComponentPalette from './components/ComponentPalette.vue'
import DashboardCanvas from './components/DashboardCanvas.vue'
import PropertyPanel from './components/PropertyPanel.vue'
import CardAttributeSidebar from './components/card-attribute/CardAttributeSidebar.vue'
import { useInsight } from './components/card-attribute/useInsight'
import { toComponentData, restoreResultSetData } from './composables/useResultSetRestore'
import DatasetDataDialog from './components/DatasetDataDialog.vue'
import AiChatPanel from './components/AiChatPanel.vue'
import PanelFloatButton from './components/PanelFloatButton.vue'
import { rowsToComponentData } from '@/utils/dataset-result'
import { readComponentDatasetPipeline } from '@/utils/component-dataset-pipeline'
import { migrateInsightDashboardSchema } from '@/utils/dashboard-schema'
import { addCombinationTab, removeCombinationTab } from '@/utils/combination-tabs'
import { insightDashboardListLocation } from './insightDashboardNavigation'
import { cloneInsightComponentForPaste } from '@/utils/insight-component-clipboard'
import DashboardThemePanel from './components/DashboardThemePanel.vue'
import { resolveDashboardTheme } from '@/utils/dashboard-theme'
import { defaultComponentVisualStyle } from '@/utils/component-visual-style'

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
const route = useRoute()
const router = useRouter()
const store = useInsightDashboardStore()
const userStore = useUserStore()
const { canModifyResource } = usePermission()
// KPI 指标分组：画布「:」直入口打开字段样式弹窗（弹窗本体挂载在 CardAttributeSidebar 内）
const { openMetricStyle, state: insightState } = useInsight()

/** 全屏「筛选预览」工作台：数据集不存在时（如刚被移除）不渲染 */
const dataDialogDataset = computed(
  () => insightState.datasets.find((item) => item.id === insightState.ui.dataDialog.datasetId) ?? null,
)

const dashboard = computed(() => store.currentDashboard)
const saving = ref(false)
const selectedComponentId = ref<string>('')
const dashboardName = ref('')
const dashboardDescription = ref('')
const dashboardOwnerName = ref('')

type ComponentContextMenu = { componentId: string | null; x: number; y: number }
const componentContextMenu = ref<ComponentContextMenu | null>(null)
const clipboardComponent = ref<InsightComponent | null>(null)
const componentContextMenuStyle = computed(() => {
  if (!componentContextMenu.value) return undefined
  return {
    left: `${componentContextMenu.value.x}px`,
    top: `${componentContextMenu.value.y}px`,
  }
})

/** AI对话面板可见性 */
const showAiChat = ref(false)
const showThemePanel = ref(false)
const themePanelRef = ref<InstanceType<typeof DashboardThemePanel> | null>(null)

/** topbar 标题/描述点击编辑状态 */
const editingName = ref(false)
const editingDesc = ref(false)

/** 完成名称编辑（失焦/回车） */
function commitName(): void {
  editingName.value = false
  handleNameChange()
}

/** 通过键盘进入名称编辑；阻止 h2 的默认按键行为，避免输入框刚挂载就失焦。 */
function startNameEditing(): void {
  editingName.value = true
}

/** 完成描述编辑（失焦/回车） */
function commitDesc(): void {
  editingDesc.value = false
  handleDescriptionChange()
}

/** 回车时使输入框失焦以触发提交 */
function blurTarget(event: Event): void {
  (event.target as HTMLInputElement).blur()
}

/** 移动端面板可见性 */
const showMobilePages = ref(false)
const showMobilePalette = ref(false)
const showMobileProperty = ref(false)

/** 侧边面板折叠状态（展开以最大化画布操作空间） */
const pagesCollapsed = ref(false)
const paletteCollapsed = ref(false)
const sidebarCollapsed = ref(false)

/** 本地 Schema 副本 */
const schema = reactive<InsightDashboardSchema>({
  version: '1.1',
  pages: [],
  datasetInputs: [],
  parameters: [],
  executionPolicy: {},
  scriptBindings: [],
  scriptFilterBindings: [],
})

const dashboardTheme = computed(() => resolveDashboardTheme(schema.theme, 'light'))

function updateThemeDraft(value: InsightDashboardSchema['theme']): void {
  schema.theme = value
}

async function confirmThemeSwitch(presetId: string): Promise<void> {
  try {
    await ElMessageBox.confirm('切换预设会清除当前主题级颜色、圆角和阴影覆盖，但不会影响指标级视觉例外和数据配置。是否继续？', '切换主题预设', { type: 'warning', confirmButtonText: '继续', cancelButtonText: '取消' })
    themePanelRef.value?.applyPreset(presetId)
  } catch {
    // 用户取消时保留当前草稿
  }
}

/** 脚本结果的临时绑定目标；用户确认应用结果后写入 scriptBindings。 */
const scriptTargetComponentId = ref('')

/** 组件渲染数据映射（编辑模式自动预览） */
const componentDataMap = ref<Record<string, InsightComponentData>>({})

/** 当前激活的页面 ID */
const activePageId = ref<string>('')

/** 正在编辑名称的页面 ID */
const editingPageId = ref<string>('')
const editingPageName = ref<string>('')

/** 预览防抖定时器 */
let previewTimer: ReturnType<typeof setTimeout> | null = null

/** 树形节点类型（DashboardPage + children） */
interface PageTreeNode extends DashboardPage {
  children?: PageTreeNode[]
}

/** 将扁平页面列表构建为 el-tree 所需的树形数据 */
const treeData = computed<PageTreeNode[]>(() => {
  const sorted = [...schema.pages].sort((a, b) => {
    const orderDiff = (a.order ?? 0) - (b.order ?? 0)
    if (orderDiff !== 0) return orderDiff
    return a.name.localeCompare(b.name)
  })

  const map = new Map<string, PageTreeNode>()
  const roots: PageTreeNode[] = []

  for (const page of sorted) {
    map.set(page.id, { ...page, children: [] })
  }

  for (const page of sorted) {
    const node = map.get(page.id)!
    if (page.parentId && map.has(page.parentId)) {
      map.get(page.parentId)!.children!.push(node)
    } else {
      roots.push(node)
    }
  }

  // 移除空 children 数组（el-tree 不显示展开箭头）
  for (const node of map.values()) {
    if (node.children && node.children.length === 0) {
      delete node.children
    }
  }

  return roots
})

/** el-tree 组件引用 */
const pageTreeRef = ref<any>(null)

/** activePageId 变化时同步 el-tree 高亮 */
watch(activePageId, (newId) => {
  selectedChildInfo.value = null
  nextTick(() => {
    pageTreeRef.value?.setCurrentKey(newId)
  })
})

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

// ── 组合卡片子组件选中 → 属性面板联动 ──────────────────
/** 选中子组件信息（画布内点选子卡片时设置） */
const selectedChildInfo = ref<{ containerId: string; childId: string } | null>(null)

/** 在容器（children 或页签 children）中查找子组件 */
type CombinationContainer = {
  id: string
  type: InsightComponentType
  children?: InsightCombinationChild[]
  containerConfig?: InsightComponent['containerConfig']
}

function findCombinationChild(container: CombinationContainer, childId: string): InsightCombinationChild | null {
  const direct = container.children?.find((c) => c.id === childId)
  if (direct) return direct
  for (const child of container.children ?? []) {
    if (child.type === 'combination') {
      const nested = findCombinationChild(child, childId)
      if (nested) return nested
    }
  }
  for (const tab of container.containerConfig?.tabs ?? []) {
    const hit = tab.children.find((c) => c.id === childId)
    if (hit) return hit
    for (const child of tab.children) {
      if (child.type === 'combination') {
        const nested = findCombinationChild(child, childId)
        if (nested) return nested
      }
    }
  }
  return null
}

function findCombinationContainer(containerId: string): CombinationContainer | null {
  const walk = (items: CombinationContainer[]): CombinationContainer | null => {
    for (const item of items) {
      if (item.id === containerId) return item
      const nested = walk([
        ...(item.children ?? []).filter((child) => child.type === 'combination'),
        ...((item.containerConfig?.tabs ?? []).flatMap((tab) => tab.children).filter((child) => child.type === 'combination')),
      ])
      if (nested) return nested
    }
    return null
  }
  return walk(currentPageComponents.value.filter((component) => component.type === 'combination'))
}

/** 选中子组件的合成组件（供属性面板编辑；position 仅占位，不参与写回） */
const selectedChildComponent = computed<InsightComponent | null>(() => {
  const info = selectedChildInfo.value
  if (!info) return null
  const container = findCombinationContainer(info.containerId)
  const child = container ? findCombinationChild(container, info.childId) : null
  if (!child) return null
  return { ...child, position: { x: 0, y: 0, w: 6, h: 4 } }
})

/** 属性面板中的「页签」操作应作用于当前选中的组合卡片，而不是它的父容器。 */
function selectedCombinationContainer(): InsightComponent | null {
  const info = selectedChildInfo.value
  if (info) {
    const parent = findCombinationContainer(info.containerId)
    const child = parent ? findCombinationChild(parent, info.childId) : null
    if (child?.type === 'combination') return child as InsightComponent
    return parent as InsightComponent | null
  }
  return selectedComponent.value?.type === 'combination' ? selectedComponent.value : null
}

/** 属性面板当前编辑对象：优先画布选中的子组件，其次顶层组件 */
const panelComponent = computed<InsightComponent | null>(() => selectedChildComponent.value ?? selectedComponent.value)

/**
 * 递归收集页面内所有筛选类组件，供「筛选器绑定」弹窗做参数名候选。
 *
 * 覆盖三类此前收集不到的筛选器（2026-09-18 修）：
 *  1. **组合卡片容器内**的子筛选器 —— 旧实现只看 `currentPageComponents`（顶层），
 *     容器内的 `children` 与 `containerConfig.tabs[].children` 完全扫不到；
 *  2. **各页签内**的子筛选器（同一容器不同页签下的筛选器都要能绑定）；
 *  3. **时间筛选器**（`type === 'timeFilter'`）—— 旧实现只认 `'filter'`，
 *     导致「所有时间筛选器都选不到」。
 */
function collectPanelFilters(list: InsightComponent[]): PanelFilterComponent[] {
  const out: PanelFilterComponent[] = []
  const seen = new Set<string>()
  const push = (item: { id: string; type: InsightComponentType; title: string }) => {
    if (!item.id || seen.has(item.id)) return
    seen.add(item.id)
    out.push({ id: String(item.id), type: item.type, title: item.title || String(item.id) })
  }
  const isFilterLike = (type: unknown) => type === 'filter' || type === 'timeFilter'
  const walkChildren = (children?: Array<{ id: string; type: InsightComponentType; title: string }>) => {
    ;(children ?? []).forEach((child) => {
      if (isFilterLike(child.type)) push(child)
    })
  }
  list.forEach((c) => {
    if (isFilterLike(c.type)) push(c)
    // 组合卡片容器：默认 Tab 的子卡片 + 每个页签各自的子卡片
    walkChildren(c.children)
    c.containerConfig?.tabs?.forEach((tab) => walkChildren(tab.children))
  })
  return out
}

/** 当前页面内的筛选类组件（filter / timeFilter，含组合卡片容器与页签内的子筛选器） */
const filterComponents = computed<PanelFilterComponent[]>(() =>
  collectPanelFilters(currentPageComponents.value),
)

onMounted(async () => {
  document.addEventListener('keydown', handleEditorClipboardKeydown)
  await loadDashboard(props.dashboardId)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleEditorClipboardKeydown)
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

function closeComponentContextMenu(): void {
  componentContextMenu.value = null
}

function handleComponentContextMenu(payload: ComponentContextMenu): void {
  if (payload.componentId) {
    handleSelectComponent(payload.componentId)
  }
  componentContextMenu.value = payload
}

function handleCopyComponent(id: string): void {
  const source = currentPageComponents.value.find((component) => component.id === id)
  if (!source) return
  clipboardComponent.value = JSON.parse(JSON.stringify(source)) as InsightComponent
  selectedComponentId.value = id
  selectedChildInfo.value = null
  closeComponentContextMenu()
  ElMessage.success('组件已复制')
}

function handlePasteComponent(): void {
  const page = schema.pages.find((item) => item.id === activePageId.value)
  const source = clipboardComponent.value
  if (!page || !source) return

  const pasted = cloneInsightComponentForPaste(source, generateId)
  let x = pasted.position.x
  let y = pasted.position.y
  const overlaps = (left: number, top: number): boolean => page.components.some((component) => (
    left < component.position.x + component.position.w
      && left + pasted.position.w > component.position.x
      && top < component.position.y + component.position.h
      && top + pasted.position.h > component.position.y
  ))
  while (overlaps(x, y)) {
    x += 1
    if (x + pasted.position.w > 24) {
      x = 0
      y += 1
    }
  }
  pasted.position = { ...pasted.position, x, y }
  page.components.push(pasted)
  selectedComponentId.value = pasted.id
  selectedChildInfo.value = null
  scriptTargetComponentId.value ||= pasted.id
  closeComponentContextMenu()
  ElMessage.success('组件已粘贴')
}

function copyFromContextMenu(): void {
  const id = componentContextMenu.value?.componentId
  if (id) handleCopyComponent(id)
}

function pasteFromContextMenu(): void {
  handlePasteComponent()
}

function handleEditorClipboardKeydown(event: KeyboardEvent): void {
  if (!event.ctrlKey && !event.metaKey) return
  if (event.altKey || event.key.toLowerCase() !== 'v') return
  const target = event.target as HTMLElement | null
  if (target?.matches('input, textarea, select, [contenteditable="true"]')) return
  if (!clipboardComponent.value) return
  event.preventDefault()
  handlePasteComponent()
}

/** 迁移旧 Schema（单 components 数组 → pages[0]） */
function migrateSchema(parsed: any): InsightDashboardSchema {
  return migrateInsightDashboardSchema(parsed, t('insight.firstPageName'))
}

/** 加载仪表盘数据 */
async function loadDashboard(id: string): Promise<void> {
  // App.vue 会异步恢复 /auth/me；编辑器不能在 userId 尚未恢复时先做归属校验，
  // 否则真实刷新或 E2E 直达编辑器会把创建者误判成无权限并留下空画布。
  if (userStore.token && userStore.userId == null) {
    await userStore.fetchCurrentUser()
  }
  if (!userStore.token) {
    return
  }
  await store.selectDashboard(id)
  // 归属守卫：非创建者且非工作区管理员不可进入编辑（防止 localStorage 残留的编辑模式）
  if (dashboard.value && !canModifyResource(dashboard.value.ownerId)) {
    ElMessage.warning(t('insight.noEditPerm'))
    emit('back')
    return
  }
  if (dashboard.value) {
    dashboardName.value = dashboard.value.name
    dashboardDescription.value = dashboard.value.description ?? ''
    dashboardOwnerName.value = dashboard.value.ownerName ?? ''
    try {
      const parsed = JSON.parse(dashboard.value.schemaJson)
      const migrated = migrateSchema(parsed)
      schema.version = migrated.version || '1.1'
      schema.pages = migrated.pages
      schema.datasetInputs = migrated.datasetInputs ?? []
      schema.script = migrated.script
      schema.parameters = migrated.parameters ?? []
      schema.scriptFilterBindings = migrated.scriptFilterBindings ?? []
      schema.executionPolicy = migrated.executionPolicy ?? {}
      schema.scriptBindings = migrated.scriptBindings ?? []
      schema.theme = migrated.theme
      scriptTargetComponentId.value = schema.scriptBindings[0]?.componentId ?? ''
    } catch {
      // Schema 解析失败时使用空 Schema（含一个默认页面）
      schema.pages = [{
        id: generateId('page'),
        name: t('insight.firstPageName'),
        components: [],
      }]
      schema.version = '1.1'
      schema.datasetInputs = []
      schema.script = undefined
      schema.parameters = []
      schema.scriptFilterBindings = []
      schema.executionPolicy = {}
      schema.scriptBindings = []
      schema.theme = undefined
      scriptTargetComponentId.value = ''
    }
    // 默认选中第一个页面
    if (schema.pages.length > 0) {
      activePageId.value = schema.pages[0].id
    }
    // 结果集持久化（决策 B）：按各组件已保存的结果集恢复画布数据，不阻塞编辑器打开
    void restorePipelineResults()
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
    combination: t('insight.component.combination'),
  }
  const key = type === 'chart' && chartType ? `chart-${chartType}` : type
  return titleMap[key] ?? type
}

/** 添加新组件到当前页面（拖入时携带鼠标落点的栅格坐标，点选物料面板时落到画布底部） */
function handleAddComponent(payload: { type: InsightComponentType; chartType?: ChartType; position?: { x: number; y: number } }): void {
  const page = schema.pages.find((p) => p.id === activePageId.value)
  if (!page) {
    return
  }
  const maxY = page.components.reduce((max, c) => Math.max(max, c.position.y + c.position.h), 0)
  const w = 6
  const h = 4
  // 拖入落点优先：新组件放在鼠标松开的位置（列钳制到画布内），与物料面板点选的「追加到底部」区分
  const x = payload.position ? Math.max(0, Math.min(payload.position.x, 24 - w)) : 0
  const y = payload.position ? Math.max(0, payload.position.y) : maxY
  const newComponent: InsightComponent = {
    id: generateId('comp'),
    type: payload.type,
    title: getDefaultTitle(payload.type, payload.chartType),
    position: { x, y, w, h },
    chartType: payload.chartType,
    titleBarStyle: 'standard',
    visualStyle: defaultComponentVisualStyle(payload.type),
    dataSource: payload.type !== 'filter' && payload.type !== 'timeFilter' && payload.type !== 'aiAnalysis' && payload.type !== 'combination' ? {
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
    // 组合卡片：默认空子卡片 + 容器配置
    children: payload.type === 'combination' ? [] : undefined,
    containerConfig: payload.type === 'combination' ? {
      title: '',
      showTitle: true,
      background: '#ffffff',
      backgroundMode: 'theme',
      radius: 12,
      padding: 16,
      shadow: 'none',
      layoutMode: 'free',
      tabs: [],
      activeTab: undefined,
      style: { border: { enabled: false, color: 'transparent', mode: 'hidden', colorMode: 'theme', width: 1, style: 'solid' } },
    } : undefined,
  }
  page.components.push(newComponent)
  selectedComponentId.value = newComponent.id
  selectedChildInfo.value = null
  // 新增组件后立即作为脚本结果的默认目标，避免用户还要再次点击画布组件。
  if (!scriptTargetComponentId.value) {
    scriptTargetComponentId.value = newComponent.id
  }
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

/** 选中组件（顶层） */
function handleSelectComponent(id: string): void {
  selectedComponentId.value = id
  selectedChildInfo.value = null
  if (!scriptTargetComponentId.value) {
    scriptTargetComponentId.value = id
  }
}

/** 画布 KPI 指标「:」直入口：先选中该组件（触发属性面板 hydrate），再打开字段样式弹窗 */
function handleOpenMetricStyle(payload: { componentId: string; fieldKey: string; field: string }): void {
  handleSelectComponent(payload.componentId)
  const targetId = payload.componentId
  nextTick(() => {
    if (selectedComponentId.value === targetId) {
      openMetricStyle(payload.fieldKey, payload.field)
    }
  })
}

/** 画布内组合卡片子组件选中/取消（childId=null 表示回到容器） */
function handleSelectChild(payload: { containerId: string; childId: string | null }): void {
  selectedChildInfo.value = payload.childId
    ? { containerId: payload.containerId, childId: payload.childId }
    : null
}

// ── 组合卡片页签增删（画布与属性面板两个入口统一走这里，操作 schema）──
/** 新增页签：首次添加时把容器内已有子卡片平移进第一个页签，避免已配置的组件丢失 */
function addTabToContainer(container: InsightComponent): void {
  const res = addCombinationTab(container)
  if (!res.tab) return
  if (res.migratedFromContainer) {
    ElMessage.success(t('insight.combination.tabMigratedHint', { count: res.migratedCount, name: res.tab.title }))
  }
}

/**
 * 删除页签：
 * - 页签内有组件且不是最后一个页签 → 二次确认（组件会随页签一并删除）；
 * - 删除最后一个页签 → 页签内组件平移回容器（回到无页签态），不丢配置。
 */
async function removeTabFromContainer(container: InsightComponent, tabId: string): Promise<void> {
  const cfg = container.containerConfig
  if (!cfg) return
  const tab = cfg.tabs.find((x) => x.id === tabId)
  if (!tab) return
  const isLastTab = cfg.tabs.length === 1
  if (!isLastTab && tab.children.length > 0) {
    try {
      await ElMessageBox.confirm(
        t('insight.combination.deleteTabConfirm', { name: tab.title, count: tab.children.length }),
        '',
        {
          confirmButtonText: t('common.confirm'),
          cancelButtonText: t('common.cancel'),
          type: 'warning',
        },
      )
    } catch {
      return // 用户取消
    }
  }
  const res = removeCombinationTab(container, tabId)
  if (!res.removed) return
  // 选中的子卡片若随页签一起没了，清掉面板选中态
  if (selectedChildInfo.value?.containerId === container.id) {
    if (!findCombinationChild(container, selectedChildInfo.value.childId)) {
      selectedChildInfo.value = null
    }
  }
  if (res.migrated && res.migratedCount > 0) {
    ElMessage.success(t('insight.combination.tabChildrenBackHint', { count: res.migratedCount }))
  }
}

/** 画布内页签栏「+」 */
function handleCombinationAddTab(payload: { containerId: string }): void {
  const container = findCombinationContainer(payload.containerId)
  if (container?.type === 'combination') addTabToContainer(container as InsightComponent)
}

/** 画布内页签栏「✕」 */
function handleCombinationRemoveTab(payload: { containerId: string; tabId: string }): void {
  const container = findCombinationContainer(payload.containerId)
  if (container?.type === 'combination') void removeTabFromContainer(container as InsightComponent, payload.tabId)
}

/** 属性面板「添加页签」 */
function handleCombinationAddTabFromPanel(): void {
  const container = selectedCombinationContainer()
  if (container?.type === 'combination') addTabToContainer(container)
}

/** 属性面板页签行「✕」 */
function handleCombinationRemoveTabFromPanel(tabId: string): void {
  const container = selectedCombinationContainer()
  if (container?.type === 'combination') void removeTabFromContainer(container, tabId)
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
    // 删除的是组合卡片容器时，同步清掉子组件选中态
    if (selectedChildInfo.value?.containerId === id) {
      selectedChildInfo.value = null
    }
  }
}

/**
 * 组合卡片容器配置合并：面板只负责外观/页签元信息，页签内的子卡片数据一律以 schema（画布侧）为准，
 * 避免属性面板的本地副本过期时把子卡片配置覆盖掉。
 */
function mergeCombinationConfig(
  existing: Pick<InsightComponent, 'containerConfig'>,
  updated: Pick<InsightComponent, 'containerConfig'>,
): InsightCombinationConfig | undefined {
  const exCfg = existing.containerConfig
  const upCfg = updated.containerConfig
  if (!exCfg) return upCfg
  if (!upCfg) return exCfg
  const tabs = upCfg.tabs.map((t) => {
    const ex = exCfg.tabs.find((x) => x.id === t.id)
    return ex ? { ...t, children: ex.children } : t
  })
  // 面板副本若没回显到画布侧新增的页签，这里补回，避免回写把新页签弄丢
  for (const ex of exCfg.tabs) {
    if (!tabs.some((t) => t.id === ex.id)) tabs.push(ex)
  }
  return { ...exCfg, ...upCfg, tabs }
}

/** 组件属性变更（保留画布管理的 position） */
function handleComponentChange(updated: InsightComponent): void {
  // 组合卡片子组件：把面板编辑结果写回容器内的 child；位置/尺寸仍由容器布局管理。
  if (selectedChildInfo.value) {
    const container = findCombinationContainer(selectedChildInfo.value!.containerId)
    const child = container ? findCombinationChild(container, selectedChildInfo.value.childId) : null
    if (child) {
      child.title = updated.title
      child.showTitle = updated.showTitle
      child.titleBarStyle = updated.titleBarStyle
      child.visualStyle = updated.visualStyle
      child.showHeader = updated.showHeader
      child.chartType = updated.chartType
      child.config = updated.config
      child.dataSource = updated.dataSource
      child.boundFilterIds = updated.boundFilterIds
      child.enableTimeFilter = updated.enableTimeFilter
      child.multiKpi = updated.multiKpi
      if (child.type === 'combination') {
        child.children = child.children ?? updated.children ?? []
        child.containerConfig = mergeCombinationConfig(child, updated)
      }
    }
    return
  }
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
      // 组合卡片：children（子卡片）只在画布内维护，属性面板不参与编辑，
      // 回写时一律保留 schema 中的现有值，避免被面板的本地副本覆盖为空
      ...(existing.type === 'combination'
        ? {
            children: existing.children ?? [],
            containerConfig: mergeCombinationConfig(existing, updated),
          }
        : {}),
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

/** 将用户确认的脚本结果应用到当前选中组件；未选中组件时只保留预览，不覆盖画布。 */
function handleScriptResult(rows: Record<string, unknown>[]): void {
  const component = currentPageComponents.value.find((item) => item.id === scriptTargetComponentId.value)
  if (!component) {
    ElMessage.warning('请先选择要应用结果的组件')
    return
  }
  const renderType = component.type === 'chart' ? 'echarts' : 'table'
  componentDataMap.value[component.id] = rowsToComponentData(component.id, rows, renderType)
  schema.scriptBindings = [
    ...(schema.scriptBindings ?? []).filter((binding) => binding.componentId !== component.id),
    { componentId: component.id, renderType },
  ]
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

/* ── 结果集：卡片唯一数据来源 ─────────────────────────────── */

/**
 * 结果集回写：把属性面板产出的结果集渲染到卡片上。
 *
 * 结果集是卡片唯一的数据来源（数据集 / 筛选 / 脚本只是产出它的手段），
 * 所以这里不设任何「直连数据源」的旁路，保证「预览所见 = 卡片所见」。
 */
function handleComponentResultSet(payload: {
  componentId: string
  status: string
  source: 'dataset' | 'script'
  rows: Record<string, unknown>[]
  error: string
}): void {
  const component = currentPageComponents.value.find((item) => item.id === payload.componentId)
  if (!component) return
  if (payload.status === 'ready') {
    componentDataMap.value[payload.componentId] = toComponentData(component, payload.rows)
    return
  }
  if (payload.status === 'stale' || payload.status === 'running') {
    // 沿用上一次渲染（不动 componentDataMap）：配置微调时不闪白；
    // 「已过期」提示由属性面板的结果集节点呈现
    return
  }
  if (payload.status === 'failed' && payload.error) {
    ElMessage.warning(payload.error)
  }
}

/**
 * 打开仪表盘 / 切换页面时恢复结果集（决策 B：结果集持久化）。
 * 回读策略见 composables/useResultSetRestore：有脚本按 executionId 回读、
 * 无脚本回源重算；取不到就保持空态，不阻塞编辑器打开。
 */
async function restorePipelineResults(): Promise<void> {
  const page = schema.pages.find((item) => item.id === activePageId.value)
  if (!page) return
  const data = await restoreResultSetData(page.components)
  Object.assign(componentDataMap.value, data)
}

/** 为当前页面所有已配置数据源的组件获取预览数据 */
async function previewAllConfiguredComponents(): Promise<void> {
  const page = schema.pages.find((p) => p.id === activePageId.value)
  if (!page) {
    return
  }
  const tasks = page.components
    // 管道卡片由「结果集」链路取数（面板产出结果集 → 回写画布）；
    // 这里只处理旧版直连 dataSource 的卡片，避免两条取数路径互相覆盖
    .filter((c) => c.type !== 'filter' && c.type !== 'timeFilter'
      && c.dataSource?.datasourceId && c.dataSource?.metrics?.length
      && !readComponentDatasetPipeline(c))
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
  void router.push(insightDashboardListLocation(route.query))
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

/** AI助手修改后刷新Schema（增量预览：只对新增/数据源变更的组件重新取数） */
async function handleAiDashboardUpdated(): Promise<void> {
  if (!dashboard.value) {
    return
  }

  // 1. 替换 schema 前快照所有组件签名，用于增量对比
  const oldSignatures = new Map<string, string>()
  for (const page of schema.pages) {
    for (const c of page.components ?? []) {
      oldSignatures.set(c.id, componentSignature(c))
    }
  }

  // 2. 重新从后端加载仪表盘数据
  await store.selectDashboard(dashboard.value.id)
  if (!dashboard.value) {
    return
  }

  let newSchema: InsightDashboardSchema | null = null
  try {
    const parsed = JSON.parse(dashboard.value.schemaJson)
    newSchema = migrateSchema(parsed)
  } catch {
    // Schema解析失败时保持当前状态
    return
  }

  // 3. 清理已删除组件的预览数据
  const newIds = new Set<string>()
  for (const page of newSchema.pages) {
    for (const c of page.components ?? []) {
      newIds.add(c.id)
    }
  }
  for (const oldId of oldSignatures.keys()) {
    if (!newIds.has(oldId)) {
      delete componentDataMap.value[oldId]
    }
  }

  // 4. 应用新 schema
  schema.version = newSchema.version
  schema.pages = newSchema.pages
  schema.theme = newSchema.theme

  // 5. 增量预览：只对新增或签名变化的组件重新取数，未变组件保留已有数据
  scheduleIncrementalPreview(oldSignatures)
}

/** 计算组件数据签名（用于判断是否需要重新取数） */
function componentSignature(c: InsightComponent): string {
  const ds = c.dataSource
  return [
    ds?.datasourceId ?? '',
    (ds?.metrics ?? []).join(','),
    (ds?.dimensions ?? []).join(','),
    c.chartType ?? '',
    c.type,
    JSON.stringify(ds?.filters ?? []),
    ds?.timeConstraint ?? '',
  ].join('|')
}

/** 增量预览：对比旧签名，只对新增/变更的组件取数 */
function scheduleIncrementalPreview(oldSignatures: Map<string, string>): void {
  if (previewTimer) {
    clearTimeout(previewTimer)
  }
  previewTimer = setTimeout(() => {
    previewChangedComponents(oldSignatures)
  }, 500)
}

/** 为所有页面中新增或数据源变更的组件获取预览数据 */
async function previewChangedComponents(oldSignatures: Map<string, string>): Promise<void> {
  const tasks: Array<Promise<void>> = []
  for (const page of schema.pages) {
    for (const c of page.components ?? []) {
      if (c.type === 'filter' || c.type === 'timeFilter' || c.type === 'aiAnalysis') {
        continue
      }
      if (!c.dataSource?.datasourceId || !c.dataSource?.metrics?.length) {
        continue
      }
      const oldSig = oldSignatures.get(c.id)
      const newSig = componentSignature(c)
      // 未变化则跳过，保留已有预览数据
      if (oldSig !== undefined && oldSig === newSig) {
        continue
      }
      tasks.push((async () => {
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
      })())
    }
  }
  await Promise.allSettled(tasks)
}

/** 选中页面 */
function handleSelectPage(pageId: string): void {
  activePageId.value = pageId
  selectedComponentId.value = ''
}

/** el-tree 节点点击回调 */
function handleTreeNodeClick(data: PageTreeNode): void {
  handleSelectPage(data.id)
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

/** 复制页面（复制为同级页面，不复制子页面） */
function copyPage(page: DashboardPage): void {
  const cloned: DashboardPage = JSON.parse(JSON.stringify(page))
  const oldPageId = page.id
  const newPageId = generateId('page')
  cloned.id = newPageId
  cloned.name = (page.name || '') + t('insight.pageCopySuffix')
  cloned.parentId = undefined

  const componentIdMap = new Map<string, string>()
  if (cloned.components) {
    cloned.components.forEach((component) => {
      const oldComponentId = component.id
      const newComponentId = generateId('comp')
      componentIdMap.set(oldComponentId, newComponentId)
      component.id = newComponentId

      if (component.tabs) {
        component.tabs.forEach((tab) => {
          tab.id = generateId('tab')
        })
      }
    })

    cloned.components.forEach((component) => {
      if (component.boundFilterIds && component.boundFilterIds.length > 0) {
        component.boundFilterIds = component.boundFilterIds.map((oldId) => {
          return componentIdMap.get(oldId) || oldId
        })
      }
    })
  }

  const originalIndex = schema.pages.findIndex((p) => p.id === oldPageId)
  if (originalIndex >= 0) {
    schema.pages.splice(originalIndex + 1, 0, cloned)
  } else {
    schema.pages.push(cloned)
  }

  reorderSiblings(cloned.parentId)
  activePageId.value = newPageId
  selectedComponentId.value = ''
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
    case 'copy':
      copyPage(page)
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
  height: 60px;
  min-height: 60px;
  background: var(--db-card);
  border-bottom: 1px solid var(--db-border);
  gap: 12px;
}

.toolbar-left {
  gap: 10px;
  min-width: 0;
}

.back-btn {
  width: 32px;
  height: 32px;
  border: 1px solid var(--db-border-strong);
  border-radius: 8px;
  background: var(--db-card);
  color: var(--db-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  transition: color var(--transition-fast), border-color var(--transition-fast), background var(--transition-fast);
}

.back-btn:hover {
  color: var(--db-accent);
  border-color: var(--db-accent);
  background: color-mix(in srgb, var(--db-accent) 6%, transparent);
}

.toolbar-title-block {
  display: flex;
  flex-direction: column;
  gap: 0;
  min-width: 0;
}

.toolbar-title {
  margin: 0;
  padding: 1px 8px;
  font-size: 16px;
  font-weight: 700;
  color: var(--db-text);
  line-height: 1.4;
  border-radius: 6px;
  cursor: text;
  max-width: 380px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: background var(--transition-fast);
}

.toolbar-title:hover {
  background: var(--db-muted);
}

.toolbar-title.placeholder {
  color: var(--db-text-quaternary, #bbb);
  font-weight: 500;
}

.toolbar-name-input {
  width: 300px;
  height: 26px;
  border: none;
  outline: none;
  background: var(--db-muted);
  border-radius: 6px;
  padding: 0 8px;
  font-size: 16px;
  font-weight: 700;
  color: var(--db-text);
  box-shadow: 0 0 0 1px var(--db-accent) inset;
}

.toolbar-subtitle {
  padding: 0 8px;
  font-size: 12px;
  color: var(--db-text-muted);
  line-height: 1.7;
  border-radius: 4px;
  cursor: text;
  max-width: 380px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: background var(--transition-fast);
}

.toolbar-subtitle:hover {
  background: var(--db-muted);
}

.toolbar-subtitle.placeholder {
  color: var(--db-text-quaternary, #bbb);
}

.toolbar-desc-input {
  width: 340px;
  height: 22px;
  border: none;
  outline: none;
  background: transparent;
  border-radius: 4px;
  padding: 0 8px;
  font-size: 12px;
  color: var(--db-text-muted);
  box-shadow: 0 0 0 1px var(--db-border-strong) inset;
}

.toolbar-owner-input {
  width: 120px;
  flex-shrink: 0;

  :deep(.el-input__wrapper) {
    background: var(--db-muted);
    box-shadow: none;
    padding: 0 10px;
    height: 30px;
    border-radius: 8px;
  }

  :deep(.el-input__wrapper:hover) {
    background: color-mix(in srgb, var(--db-muted) 80%, var(--db-card));
  }

  :deep(.el-input__wrapper.is-focus) {
    background: var(--db-card);
    box-shadow: 0 0 0 1px var(--db-accent) inset;
  }

  :deep(.el-input__inner) {
    color: var(--db-text-secondary);
    font-size: 12.5px;
  }
}

.toolbar-separator {
  width: 1px;
  height: 20px;
  background: var(--db-border);
  flex-shrink: 0;
}

.toolbar-right {
  gap: 8px;
}

.toolbar-right :deep(.el-button.toolbar-btn) {
  height: 32px;
  border-radius: 8px;
  padding: 0 12px;
  font-size: 13px;
  font-weight: 500;
}

.toolbar-right :deep(.el-button.toolbar-btn:not(.el-button--primary)) {
  background: var(--db-card);
  border-color: var(--db-border-strong);
  color: var(--db-text-secondary);
}

.toolbar-right :deep(.el-button.toolbar-btn:not(.el-button--primary):hover) {
  border-color: var(--db-accent);
  color: var(--db-accent);
}

.toolbar-right :deep(.el-button.ai-assistant-btn.on) {
  background: color-mix(in srgb, var(--db-accent) 8%, transparent);
  border-color: var(--db-accent);
  color: var(--db-accent);
  font-weight: 600;
}

.toolbar-right :deep(.el-button--primary.toolbar-btn) {
  background: var(--main-orange);
  border-color: var(--main-orange);
  box-shadow: var(--shadow-md);
}

.toolbar-right :deep(.el-button--primary.toolbar-btn:hover),
.toolbar-right :deep(.el-button--primary.toolbar-btn:focus) {
  background: var(--main-orange);
  border-color: var(--main-orange);
  filter: brightness(1.08);
}

.editor-body {
  flex: 1;
  display: flex;
  gap: 12px;
  padding: 12px 16px 16px;
  overflow: hidden;
  position: relative;
}

.editor-pages {
  width: 200px;
  flex-shrink: 0;
  overflow: hidden;
  background: var(--db-card);
  border: 1px solid var(--db-border);
  border-radius: 12px;
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  animation: fadeIn var(--transition-base) both;
}

.pages-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 12px 10px;
  border-bottom: 1px solid var(--db-border);
  flex-shrink: 0;
}

.pages-header-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.pages-header :deep(.el-button) {
  width: 24px;
  height: 24px;
  padding: 0;
  border-radius: 6px;
  border: 1px solid var(--db-border-strong);
  color: var(--db-text-secondary);
  font-size: 14px;
  line-height: 1;
  transition: color var(--transition-fast), border-color var(--transition-fast), background var(--transition-fast);
}

.pages-header :deep(.el-button:hover) {
  color: var(--db-accent);
  border-color: var(--db-accent);
  background: color-mix(in srgb, var(--db-accent) 6%, transparent);
}

.pages-title {
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--db-text-muted);
}

.pages-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.pages-list::-webkit-scrollbar {
  width: 6px;
}

.pages-list::-webkit-scrollbar-thumb {
  background: var(--db-border-strong);
  border-radius: 3px;
}

.pages-list::-webkit-scrollbar-thumb:hover {
  background: var(--db-text-quaternary, #bbb);
}

.pages-list::-webkit-scrollbar-track {
  background: transparent;
}

/* ─── el-tree 覆写 ────────────────────────────── */
.pages-list :deep(.el-tree) {
  background: transparent;
  color: var(--db-text-secondary);
  --el-tree-node-hover-bg-color: var(--db-hover);
}

.pages-list :deep(.el-tree-node__content) {
  height: 34px;
  border-radius: 8px;
  padding-right: 4px;
  transition: background var(--transition-fast), color var(--transition-fast);
}

.pages-list :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: color-mix(in srgb, var(--db-accent) 10%, transparent);
  color: var(--db-accent);
  font-weight: 600;
  position: relative;
}

/* 激活节点：左侧指示条 */
.pages-list :deep(.el-tree-node__expand-icon) {
  color: var(--db-text-muted);
  font-size: 12px;
  padding: 4px;
}

.pages-list :deep(.el-tree-node__expand-icon.is-leaf) {
  color: transparent;
}

/* ─── 自定义节点内容 ─────────────────────────── */
.page-node {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
  font-size: 13px;
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
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
  opacity: 0;
  visibility: hidden;
  transition: opacity var(--transition-fast), visibility var(--transition-fast);
}

.pages-list :deep(.el-tree-node__content:hover) .page-actions,
.pages-list :deep(.el-tree-node.is-current > .el-tree-node__content) .page-actions {
  opacity: 1;
  visibility: visible;
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
  background: var(--db-card);
  border: 1px solid var(--db-border);
  border-radius: 12px;
  box-shadow: var(--shadow-card);
  animation: fadeIn var(--transition-base) both;
}

/* 剥离子组件自带的分隔边框，避免与卡片容器双重边框 */
.editor-palette :deep(.component-palette) {
  border-right: none;
  background: transparent;
}

.editor-canvas {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: auto;
  background: var(--db-bg);
  border: 1px solid var(--db-border);
  border-radius: 12px;
}

.component-context-menu {
  position: fixed;
  z-index: 2000;
  min-width: 188px;
  padding: 6px;
  background: var(--db-card);
  border: 1px solid var(--db-border);
  border-radius: 10px;
  box-shadow: var(--shadow-dropdown, 0 12px 32px rgba(24, 39, 75, 0.16));
}

.component-context-menu button {
  width: 100%;
  border: 0;
  border-radius: 7px;
  padding: 8px 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  background: transparent;
  color: var(--db-text);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.component-context-menu button:hover:not(:disabled) {
  background: var(--db-hover);
  color: var(--db-accent);
}

.component-context-menu button:disabled {
  color: var(--db-text-muted);
  cursor: not-allowed;
}

.component-context-menu kbd {
  color: var(--db-text-muted);
  font-size: 11px;
  white-space: nowrap;
}

.editor-right-sidebar {
  width: 340px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--db-card);
  border: 1px solid var(--db-border);
  border-radius: 12px;
  box-shadow: var(--shadow-card);
  animation: fadeIn var(--transition-base) both;
}

.editor-property {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  animation: fadeIn var(--transition-base) both;
}

/* 剥离子组件自带的分隔边框 */
.editor-property :deep(.property-panel) {
  border-left: none;
  background: transparent;
  /* 属性面板与脚本数据集面板同属右侧栏，不能用 100% 高度把后者裁掉。 */
  height: auto;
  min-height: 160px;
  flex: 0 0 auto;
}

.editor-ai-chat {
  flex: 2;
  min-height: 0;
  overflow: hidden;
  border-top: 1px solid var(--db-border);
  animation: fadeIn var(--transition-base) both;
}

/* ─── 面板收起按钮 ─────────────────────────────── */
.panel-collapse-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  color: var(--db-text-muted);
  transition: color var(--transition-fast), background var(--transition-fast);
}

.panel-collapse-btn:hover {
  color: var(--db-accent);
  background: color-mix(in srgb, var(--db-accent) 8%, transparent);
}

/* ─── 悬浮折叠按钮（样式在 PanelFloatButton.vue 内） ─────────────────────────────────── */

@media (max-width: 1279px) {
  .editor-canvas {
    min-width: 0;
    overflow: auto;
  }

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

  .pages-list :deep(.el-tree-node__content) {
    justify-content: center;
    padding-left: 0 !important;
    padding-right: 0;
  }

  .page-node {
    justify-content: center;
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
    right: 16px;
    top: 72px;
    bottom: 16px;
    z-index: 100;
    border-radius: 12px;
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
  .editor-canvas {
    width: 100%;
    min-width: 0;
    overflow-x: hidden;
  }

  .editor-toolbar {
    padding: 0 var(--space-md);
    min-height: auto;
    height: auto;
    flex-wrap: wrap;
    gap: 8px;
    padding-top: 10px;
    padding-bottom: 10px;
  }

  .toolbar-left,
  .toolbar-right {
    width: 100%;
  }

  .toolbar-right {
    justify-content: flex-end;
  }

  .toolbar-separator {
    display: none;
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
    background: var(--db-card);
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
