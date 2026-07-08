<template>
  <div class="dashboard-preview-view">
    <!-- 顶部工具栏 -->
    <div class="preview-toolbar">
      <div class="toolbar-left">
        <el-button :icon="ArrowLeft" text @click="handleBack">{{ t('common.back') }}</el-button>
        <span class="toolbar-title">{{ dashboard?.name ?? t('insight.preview') }}</span>
      </div>
      <div class="toolbar-right">
        <el-button type="primary" :icon="Document" :loading="generatingReport" @click="handleAiReport">
          {{ t('insight.aiReport') }}
        </el-button>
      </div>
    </div>

    <!-- 仪表盘预览区 -->
    <div class="preview-body">
      <div v-if="schema.components.length === 0" class="preview-empty">
        <div class="empty-icon">📭</div>
        <div class="empty-text">{{ t('insight.previewEmpty') }}</div>
      </div>
      <DashboardCanvas
        v-else
        :components="schema.components"
        :component-data-map="componentDataMap"
        :editable="false"
      />
    </div>

    <!-- AI 报告抽屉 -->
    <el-drawer
      v-model="reportDrawerVisible"
      :title="t('insight.aiReport')"
      direction="rtl"
      size="50%"
      @close="handleDrawerClose"
    >
      <div class="report-content">
        <div v-if="generatingReport" class="report-loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>{{ t('insight.generatingReport') }}</span>
        </div>
        <div v-else-if="reportContent" class="report-markdown" v-html="renderedReport"></div>
        <div v-else class="report-placeholder">{{ t('insight.reportPlaceholder') }}</div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Document, Loading } from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import type { InsightDashboardSchema, InsightComponentData } from '@/types'
import { useInsightDashboardStore } from '@/stores/useInsightDashboardStore'
import { preview } from '@/api/insight-dashboard'
import { streamReport } from '@/api/insight-report'
import DashboardCanvas from './components/DashboardCanvas.vue'

defineOptions({
  name: 'DashboardPreviewView',
})

const props = defineProps<{
  /** 仪表盘 ID */
  dashboardId: string
}>()

const emit = defineEmits<{
  (e: 'back'): void
}>()

const { t } = useI18n()
const store = useInsightDashboardStore()

const dashboard = computed(() => store.currentDashboard)
const schema = reactive<InsightDashboardSchema>({ version: '1.0', components: [] })
const componentDataMap = ref<Record<string, InsightComponentData>>({})

/** AI 报告相关 */
const reportDrawerVisible = ref(false)
const generatingReport = ref(false)
const reportContent = ref<string>('')
let abortController: AbortController | null = null

/** 渲染 Markdown 报告（含 XSS 过滤） */
const renderedReport = computed(() => {
  if (!reportContent.value) {
    return ''
  }
  const raw = marked.parse(reportContent.value, { async: false }) as string
  return DOMPurify.sanitize(raw)
})

onMounted(async () => {
  await loadDashboard()
})

watch(
  () => props.dashboardId,
  async () => {
    await loadDashboard()
  }
)

/** 加载仪表盘 */
async function loadDashboard(): Promise<void> {
  if (!props.dashboardId) {
    return
  }
  await store.selectDashboard(props.dashboardId)
  if (dashboard.value) {
    try {
      const parsed = JSON.parse(dashboard.value.schemaJson) as InsightDashboardSchema
      schema.version = parsed.version ?? '1.0'
      schema.components = parsed.components ?? []
    } catch {
      schema.components = []
    }
    // 调用预览接口获取组件渲染数据
    try {
      const dataList = await preview(props.dashboardId) as unknown as InsightComponentData[]
      const dataMap: Record<string, InsightComponentData> = {}
      for (const item of dataList ?? []) {
        dataMap[item.componentId] = item
      }
      componentDataMap.value = dataMap
    } catch {
      ElMessage.warning(t('insight.previewDataFailed'))
    }
  }
}

/** 生成 AI 解读报告（SSE 流式） */
async function handleAiReport(): Promise<void> {
  reportDrawerVisible.value = true
  generatingReport.value = true
  reportContent.value = ''

  abortController = streamReport(
    props.dashboardId,
    (chunk) => {
      reportContent.value += chunk
    },
    (err) => {
      ElMessage.error(t('insight.reportGenerateFailed') + ': ' + err)
      generatingReport.value = false
    },
    () => {
      generatingReport.value = false
    }
  )
}

/** 抽屉关闭时取消请求 */
function handleDrawerClose(): void {
  if (abortController) {
    abortController.abort()
    abortController = null
  }
  generatingReport.value = false
}

/** 返回列表 */
function handleBack(): void {
  emit('back')
}
</script>

<style scoped>
.dashboard-preview-view {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--theme-bg);
  overflow: hidden;
}

.preview-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: var(--theme-surface);
  border-bottom: 1px solid var(--theme-border);
  flex-shrink: 0;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--theme-text);
}

.toolbar-right {
  display: flex;
  gap: 8px;
}

.preview-body {
  flex: 1;
  overflow: hidden;
}

.preview-empty {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--theme-text-muted);
}

.empty-icon {
  font-size: 48px;
}

.empty-text {
  font-size: 14px;
}

.report-content {
  padding: 16px;
  height: 100%;
  overflow-y: auto;
}

.report-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--theme-text-secondary);
  font-size: 14px;
}

.report-markdown {
  font-size: 14px;
  line-height: 1.7;
  color: var(--theme-text);
}

.report-markdown :deep(h1),
.report-markdown :deep(h2),
.report-markdown :deep(h3) {
  margin-top: 16px;
  margin-bottom: 8px;
  color: var(--theme-text);
}

.report-markdown :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
}

.report-markdown :deep(th),
.report-markdown :deep(td) {
  border: 1px solid var(--theme-border);
  padding: 8px 12px;
  text-align: left;
}

.report-markdown :deep(th) {
  background: var(--theme-surface-hover);
  font-weight: 600;
}

.report-placeholder {
  color: var(--theme-text-muted);
  font-size: 14px;
  text-align: center;
  padding: 40px 0;
}
</style>
