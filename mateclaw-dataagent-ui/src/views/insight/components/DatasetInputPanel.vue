<template>
  <section class="dataset-input-panel">
    <div class="panel-heading">
      <div>
        <h3>数据集配置</h3>
      </div>
      <div class="add-dataset-entry">
        <el-button size="small" type="primary" plain aria-label="添加数据集" @click="pickerOpen = !pickerOpen">+ 添加数据集</el-button>
        <div v-show="pickerOpen" class="dataset-picker-popover">
          <DatasetSourcePicker :available-datasets="datasets" :available-datasources="datasources" @select="handleSourceSelection" />
        </div>
      </div>
      <DatasetSourceDialog
        v-if="sourceSelection"
        v-model="sourceDialogOpen"
        :selection="sourceSelection"
        @confirm="confirmSource"
      />
    </div>

    <el-empty v-if="inputs.length === 0" description="暂未配置数据集输入" :image-size="56" />

    <div v-for="(input, index) in inputs" :key="`${input.datasetId}-${index}`" class="dataset-input-row dataset-source-card">
      <div class="row-header">
        <div class="dataset-card-title"><span>输入 {{ index + 1 }}</span><span class="dataset-source-badge">{{ sourceLabel(input) }}</span></div>
        <el-button text type="danger" size="small" @click="removeInput(index)">移除</el-button>
      </div>
      <div v-if="input.datasetId" class="dataset-source-display">数据源：{{ sourceLabel(input) }}<span v-if="datasetName(input)"> · {{ datasetName(input) }}</span></div>
      <el-select v-else
        :model-value="input.datasetId"
        filterable
        clearable
        aria-label="选择已授权数据集"
        placeholder="选择已授权数据集"
        class="full-width"
        @change="(value: string) => changeDataset(index, value)"
      >
        <el-option-group v-for="group in datasetGroups" :key="group.category" :label="group.label">
          <el-option v-for="dataset in group.datasets" :key="dataset.id" :label="dataset.name" :value="dataset.id" />
        </el-option-group>
      </el-select>
      <div v-if="datasetGroups.length" class="dataset-category-hint" aria-label="数据集来源分类">
        来源分类：{{ datasetGroups.map(group => group.label).join('、') }}
      </div>
      <el-input
        :model-value="input.inputName"
        class="alias-input"
        :aria-describedby="aliasStatus(input.inputName) === 'ok' ? undefined : `dataset-alias-error-${index}`"
        :aria-label="`输入 ${index + 1} 脚本别名`"
        placeholder="脚本别名，如 orders"
        :status="aliasStatus(input.inputName) === 'ok' ? '' : 'error'"
        @update:model-value="(value: string) => changeAlias(index, value)"
      />
      <div v-if="aliasStatus(input.inputName) !== 'ok'" :id="`dataset-alias-error-${index}`" class="field-error" role="alert">
        {{ aliasStatus(input.inputName) }}
      </div>
      <div v-if="descriptors[input.datasetId]?.schema?.length" class="descriptor-summary">
        <span>{{ descriptors[input.datasetId].schema.length }} 个字段</span>
        <el-tooltip placement="top" effect="light">
          <template #content>
            <div v-for="column in descriptors[input.datasetId].schema" :key="column.name">
              {{ column.name }}（{{ column.dataType }}）
            </div>
          </template>
          <el-button text size="small">查看字段</el-button>
        </el-tooltip>
        <el-button
          text
          size="small"
          :loading="previewingKey === previewKey(input)"
          @click="previewInput(input)"
        >
          输入预览
        </el-button>
      </div>
      <div v-else-if="descriptors[input.datasetId]" class="descriptor-summary descriptor-pending">
        <span>字段探测中…</span>
        <el-button
          text
          size="small"
          :loading="loadingDatasetId === input.datasetId"
          @click="loadDescriptor(input.datasetId)"
        >
          刷新字段
        </el-button>
      </div>
      <el-button
        v-else-if="input.datasetId"
        text
        size="small"
        :loading="loadingDatasetId === input.datasetId"
        @click="loadDescriptor(input.datasetId)"
      >
        查看字段
      </el-button>
      <div v-if="input.datasetId || input.sourceType" class="dataset-config-body">
        <div v-if="input.datasetId" class="configured-source-hint">查询配置已保存；可通过“筛选预览”查看当前输入结果。</div>
        <div v-if="sourceType(input) === 'JDBC_SQL' || sourceType(input) === 'JDBC_TABLE'" class="source-config-block">
          <label class="form-label">JDBC 数据集模式</label>
          <el-select :model-value="sourceType(input)" aria-label="JDBC 数据集模式" @change="(value: string) => updateInput(index, { sourceType: value })">
            <el-option label="已有表数据集" value="JDBC_TABLE" /><el-option label="自定义 SQL" value="JDBC_SQL" />
          </el-select>
        </div>
        <div v-if="sourceType(input) === 'JDBC_SQL'" class="source-config-block">
          <label class="form-label">JDBC SQL</label>
          <textarea :value="input.sourceConfig?.sql || ''" class="dataset-sql-editor" aria-label="数据集 SQL 查询" rows="4" placeholder="select ... from ..." @input="updateSourceConfig(index, { sql: ($event.target as HTMLTextAreaElement).value })" />
          <span v-if="input.sourceConfig?.sql && !isReadonlySql(input.sourceConfig.sql)" class="field-error" role="alert">SQL 仅允许单条只读 SELECT/WITH 查询</span>
          <span class="form-hint">参数使用绑定方式；预览时由后端再次执行只读校验。</span>
        </div>
        <div v-else-if="sourceType(input) === 'ALOUDATA_ANALYSIS_VIEW' || sourceType(input) === 'ALOUDATA_METRICS'" class="source-config-block">
          <label class="form-label">Aloudata 数据获取模式</label>
          <el-select :model-value="sourceType(input)" aria-label="Aloudata 数据获取模式" @change="(value: string) => updateInput(index, { sourceType: value })">
            <el-option label="指标视图" value="ALOUDATA_ANALYSIS_VIEW" /><el-option label="指标&维度" value="ALOUDATA_METRICS" />
          </el-select>
          <span class="form-hint">指标视图使用已配置的分析视图，不在此处重复创建。</span>
        </div>
        <div v-else-if="sourceType(input) === 'HTTP_API' || sourceType(input) === 'FILE'" class="source-config-block unified-source-hint">接口/文件数据集使用已创建的数据集；本页负责选择、字段预览和筛选，不直接访问外部地址。</div>
        <div class="dataset-card-actions"><el-button text size="small" @click="toggleMapping(index)">字段名称</el-button><el-button text size="small" @click="toggleFilters(index)">筛选预览</el-button></div>
        <el-dialog v-model="mappingOpen[index]" title="修改字段名称" width="520px" aria-label="字段映射编辑器">
          <div class="inline-editor-title">字段映射（原字段 → 目标字段）</div>
          <div v-for="column in descriptors[input.datasetId]?.schema || []" :key="column.name" class="mapping-row"><span>{{ column.name }}</span><input :value="mappingTarget(index, input, column.name)" :aria-label="`${column.name} 目标字段`" placeholder="目标字段" @input="updateMapping(index, column.name, ($event.target as HTMLInputElement).value)" /></div>
          <span v-if="!descriptors[input.datasetId]?.schema?.length" class="form-hint">先点击“查看字段”获取字段结构。</span>
          <template #footer><el-button @click="cancelMapping(index)">取消</el-button><el-button type="primary" @click="commitMapping(index)">确定</el-button></template>
        </el-dialog>
        <el-dialog v-model="filtersOpen[index]" title="输入筛选" width="520px" aria-label="输入筛选编辑器">
          <div class="inline-editor-title">输入筛选（在源数据查询阶段执行）</div>
          <div v-for="(filter, filterIndex) in (filterDrafts[index] || input.filters || [])" :key="`${filter.field}-${filterIndex}`" class="filter-row"><input :value="filter.field" aria-label="筛选字段" placeholder="字段" @input="updateFilter(index, filterIndex, { field: ($event.target as HTMLInputElement).value })" /><select :value="filter.operator" aria-label="筛选操作符" @change="updateFilter(index, filterIndex, { operator: ($event.target as HTMLSelectElement).value as any })"><option value="eq">等于</option><option value="in">包含</option><option value="between">范围</option></select><input :value="String(filter.value ?? '')" aria-label="筛选值" placeholder="筛选值" @input="updateFilter(index, filterIndex, { value: ($event.target as HTMLInputElement).value })" /></div>
          <el-button text size="small" @click="addFilter(index)">添加筛选条件</el-button>
          <template #footer><el-button @click="cancelFilters(index)">取消</el-button><el-button type="primary" @click="commitFilters(index)">确定</el-button></template>
        </el-dialog>
      </div>
      <div v-if="previewResults[previewKey(input)]" class="input-preview-card">
        <div class="preview-meta">输入预览 · {{ previewResults[previewKey(input)].rows.length }} 行</div>
        <div class="preview-table-scroll">
          <table class="preview-table">
            <thead><tr><th v-for="column in previewColumns(previewResults[previewKey(input)])" :key="column">{{ column }}</th></tr></thead>
            <tbody><tr v-for="(row, rowIndex) in previewResults[previewKey(input)].rows" :key="rowIndex">
              <td v-for="column in previewColumns(previewResults[previewKey(input)])" :key="column">{{ formatCell(row[column]) }}</td>
            </tr></tbody>
          </table>
        </div>
      </div>
    </div>

    <div v-if="filterComponents.length && inputs.length" class="filter-binding-panel">
      <div class="row-header"><div><span>筛选器绑定</span><small>选择筛选器作用于哪些输入数据集</small></div></div>
      <div v-for="filterComponent in filterComponents" :key="filterComponent.id" class="filter-binding-row">
        <span class="filter-binding-name">{{ filterComponent.title || filterComponent.id }}</span>
        <label v-for="input in inputs" :key="`${filterComponent.id}-${input.inputName}`" class="filter-binding-check">
          <input type="checkbox" :checked="filterBindings.find(binding => binding.filterComponentId === filterComponent.id)?.inputNames.includes(input.inputName)" @change="toggleFilterInput(filterBindings.find(binding => binding.filterComponentId === filterComponent.id), filterComponent.id, input.inputName, ($event.target as HTMLInputElement).checked)" />
          {{ input.inputName || '未命名输入' }}
        </label>
      </div>
    </div>

    <div v-if="inputs.length >= 2 || Boolean(script?.trim())" class="script-draft">
      <div class="row-header">
        <span>Python 脚本草稿</span>
        <div class="script-actions">
          <el-button text size="small" aria-label="生成 Python Base Script" @click="insertTemplate">生成 Base Script</el-button>
          <el-button
            type="primary"
            plain
            size="small"
            :loading="executionLoading"
            :disabled="!dashboardId || !script?.trim()"
            @click="executeScriptPreview"
          >
            最终结果预览
          </el-button>
          <el-button
            v-if="executionLoading && executionId"
            size="small"
            @click="cancelScriptPreview"
          >
            取消执行
          </el-button>
          <el-button
            v-if="!executionLoading && executionError"
            size="small"
            @click="executeScriptPreview"
          >
            重试
          </el-button>
          <el-button
            v-if="executionRows.length > 0 && targetComponentId"
            size="small"
            @click="emit('apply-result', executionRows)"
          >
            应用到组件
          </el-button>
        </div>
      </div>
      <div class="system-script-block">
        <div class="inline-editor-title">系统生成区域（只读）</div>
        <textarea :value="systemScript" class="system-script-preview" aria-label="系统生成脚本" rows="8" readonly />
      </div>
      <div class="user-script-block">
        <div class="inline-editor-title">用户处理区域（可编辑）</div>
        <textarea :value="userScript" class="user-script-editor" aria-label="用户处理脚本" rows="8" placeholder="在此维护 Join、合并、计算和业务规则。" @input="updateUserScript(($event.target as HTMLTextAreaElement).value)" />
      </div>
      <el-alert v-if="executionError" class="execution-alert" type="error" :closable="false" :title="executionError" />
      <div v-if="executionRows.length > 0" class="result-table-wrap">
        <div v-if="executionOutputRef" class="result-limit-notice">
          仅展示受限预览；完整结果已保存为 ObjectRef：{{ executionOutputRef.uri || executionOutputRef.objectId || '受控引用' }}
        </div>
        <div class="preview-tabs" role="tablist" aria-label="预览反馈">
          <button v-for="tab in resultTabs" :key="tab.key" class="preview-tab" :class="{ active: resultTab === tab.key }" role="tab" :aria-selected="resultTab === tab.key" @click="resultTab = tab.key">{{ tab.label }}</button>
        </div>
        <div v-if="resultTab === 'data'" role="tabpanel" aria-label="数据预览">
          <table class="result-table">
            <thead><tr><th v-for="column in executionColumns" :key="column">{{ column }}</th></tr></thead>
            <tbody>
              <tr v-for="(row, rowIndex) in executionRows" :key="rowIndex">
                <td v-for="column in executionColumns" :key="column">{{ formatCell(row[column]) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else-if="resultTab === 'schema'" class="execution-feedback" role="tabpanel" aria-label="字段结构">
          <div v-for="column in executionColumns" :key="column" class="feedback-row"><span>{{ column }}</span><span>{{ inferDataType(executionRows[0]?.[column]) }}</span></div>
        </div>
        <div v-else-if="resultTab === 'execution'" class="execution-feedback" role="tabpanel" aria-label="执行信息">
          <div class="feedback-row"><span>执行 ID</span><span>{{ executionId || '—' }}</span></div><div class="feedback-row"><span>输入行数</span><span>{{ inputs.length }} 个数据集</span></div><div class="feedback-row"><span>输出行数</span><span>{{ executionRows.length }}</span></div><div class="feedback-row"><span>扫描量</span><span>来源未提供</span></div>
        </div>
        <div v-else class="execution-feedback" role="tabpanel" aria-label="处理日志"><div class="feedback-log">已完成数据集读取、Python 预处理和结果绑定。</div><div v-if="executionOutputRef" class="feedback-log">结果已写入受控 ObjectRef。</div></div>
      </div>
      <div v-else-if="executionResult !== null" class="input-preview-card execution-result">
        <div class="preview-meta">最终结果预览</div>
        <pre class="preview-result">{{ JSON.stringify(executionResult, null, 2) }}</pre>
      </div>
    </div>

    <div class="parameters-panel">
      <div class="row-header">
        <div>
          <span>脚本参数</span>
          <small>只配置作用范围，不绑定数据集字段</small>
        </div>
        <el-button text size="small" @click="addParameter">添加参数</el-button>
      </div>
      <div v-for="(parameter, index) in parameters" :key="`${parameter.name}-${index}`" class="parameter-row">
        <el-input
          :model-value="parameter.name"
          :aria-label="`脚本参数 ${index + 1} 名称`"
          placeholder="参数名"
          @update:model-value="(value: string) => updateParameter(index, { name: value })"
        />
        <el-select
          :model-value="parameter.type"
          :aria-label="`脚本参数 ${index + 1} 类型`"
          placeholder="类型"
          @update:model-value="(value: DashboardScriptParameter['type']) => updateParameter(index, { type: value })"
        >
          <el-option v-for="type in parameterTypes" :key="type" :label="type" :value="type" />
        </el-select>
        <el-select
          :model-value="parameter.scope"
          :aria-label="`脚本参数 ${index + 1} 作用范围`"
          placeholder="作用范围"
          @update:model-value="(value: DashboardScriptParameter['scope']) => updateParameter(index, { scope: value })"
        >
          <el-option label="仪表盘" value="dashboard" />
          <el-option label="页面" value="page" />
          <el-option label="组件" value="component" />
        </el-select>
        <el-button text type="danger" @click="removeParameter(index)">移除</el-button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { Dataset, DatasetInputDescriptor, DatasetInputColumn, DashboardDatasetInput, DatasetBatch, DashboardScriptParameter, DatasetObjectRef, InsightComponent, DatasetFilter, DashboardScriptFilterBinding } from '@/types'
import * as datasetApi from '@/api/dataset'
import * as datasourceApi from '@/api/datasource'
import * as insightDashboardApi from '@/api/insight-dashboard'
import {
  assertUniqueDatasetInputAliases,
  assertValidScriptParameters,
  isValidDatasetInputAlias,
} from '@/utils/dataset-inputs'
import { groupDatasets } from '@/utils/data-binding'
import { buildSystemScript, mergeBaseScript, SYSTEM_SCRIPT_END, SYSTEM_SCRIPT_START, USER_SCRIPT_START } from '@/utils/script-template'
import { isReadonlySql, normalizeDatasetInput } from '@/utils/dataset-composer'
import DatasetSourcePicker, { type DatasetSourceSelection } from './DatasetSourcePicker.vue'
import DatasetSourceDialog from './DatasetSourceDialog.vue'

const props = defineProps<{
  inputs: DashboardDatasetInput[]
  script?: string
  dashboardId?: string
  parameters?: DashboardScriptParameter[]
  targetComponentId?: string
  targetComponents?: InsightComponent[]
  filterBindings?: DashboardScriptFilterBinding[]
}>()
const emit = defineEmits<{
  (e: 'update:inputs', value: DashboardDatasetInput[]): void
  (e: 'update:script', value: string): void
  (e: 'update:parameters', value: DashboardScriptParameter[]): void
  (e: 'update:target-component-id', value: string): void
  (e: 'update:filter-bindings', value: DashboardScriptFilterBinding[]): void
  (e: 'apply-result', value: Record<string, unknown>[]): void
}>()

const datasets = ref<Dataset[]>([])
const datasources = ref<import('@/types').Datasource[]>([])
const datasetGroups = computed(() => groupDatasets(datasets.value))
const descriptors = reactive<Record<string, DatasetInputDescriptor>>({})
const previewResults = reactive<Record<string, DatasetBatch>>({})
const loadingDatasetId = ref('')
const previewingKey = ref('')
const executionLoading = ref(false)
const executionError = ref('')
const executionResult = ref<unknown | null>(null)
const executionOutputRef = ref<DatasetObjectRef | null>(null)
const executionId = ref('')
const resultTab = ref<'data' | 'schema' | 'execution' | 'log'>('data')
const resultTabs = [
  { key: 'data', label: '数据预览' },
  { key: 'schema', label: '字段结构' },
  { key: 'execution', label: '执行信息' },
  { key: 'log', label: '处理日志' },
] as const
const cancelRequested = ref(false)
const mappingOpen = reactive<Record<number, boolean>>({})
const filtersOpen = reactive<Record<number, boolean>>({})
const mappingDrafts = reactive<Record<number, DashboardDatasetInput['fieldMappings']>>({})
const filterDrafts = reactive<Record<number, DatasetFilter[]>>({})
const executionRows = computed<Record<string, unknown>[]>(() => {
  if (!Array.isArray(executionResult.value)) return []
  return executionResult.value.filter((row): row is Record<string, unknown> => Boolean(row) && typeof row === 'object')
})
const executionColumns = computed(() => {
  const columns = new Set<string>()
  executionRows.value.forEach((row) => Object.keys(row).forEach((key) => columns.add(key)))
  return [...columns]
})
const parameterTypes: DashboardScriptParameter['type'][] = ['string', 'number', 'boolean', 'date', 'datetime', 'enum', 'date_range', 'string[]', 'number[]']
const parameters = computed(() => props.parameters ?? [])
const systemScript = computed(() => {
  const value = props.script || ''
  const start = value.indexOf(SYSTEM_SCRIPT_START)
  const end = value.indexOf(SYSTEM_SCRIPT_END)
  return start >= 0 && end > start ? value.slice(start, end + SYSTEM_SCRIPT_END.length) : ''
})
const userScript = computed(() => {
  const value = props.script || ''
  const end = value.indexOf(SYSTEM_SCRIPT_END)
  if (end >= 0) return value.slice(end + SYSTEM_SCRIPT_END.length).replace(USER_SCRIPT_START, '').trim()
  return value.trim()
})
const targetComponents = computed(() => (props.targetComponents ?? []).filter((component) => ['kpi', 'chart', 'table'].includes(component.type)))
const filterComponents = computed(() => (props.targetComponents ?? []).filter((component) => ['filter', 'timeFilter'].includes(component.type)))
const filterBindings = computed(() => props.filterBindings ?? [])
const pickerOpen = ref(false)
const sourceDialogOpen = ref(false)
const sourceSelection = ref<DatasetSourceSelection | null>(null)

onMounted(async () => {
  // 字段探测不依赖来源目录，先启动，避免目录接口慢时卡住已有输入的字段状态。
  for (const input of props.inputs) {
    if (input.datasetId) void loadDescriptor(input.datasetId)
  }
  try {
    datasets.value = await datasetApi.list() as unknown as Dataset[]
  } catch {
    // API 层已经展示错误；面板保留空状态，避免影响旧仪表盘编辑。
  }
  try { datasources.value = await datasourceApi.list() as unknown as import('@/types').Datasource[] } catch { datasources.value = [] }
})

function addInput(): void {
  const used = new Set(props.inputs.map((input) => input.inputName))
  let suffix = props.inputs.length + 1
  let inputName = `dataset_${suffix}`
  while (used.has(inputName)) {
    suffix += 1
    inputName = `dataset_${suffix}`
  }
  emit('update:inputs', [...props.inputs, { datasetId: '', inputName }])
}

function handleSourceSelection(selection: DatasetSourceSelection): void {
  pickerOpen.value = false
  if (!selection.datasetId) {
    sourceSelection.value = selection
    sourceDialogOpen.value = true
    return
  }
  const used = new Set(props.inputs.map((input) => input.inputName))
  let suffix = props.inputs.length + 1
  let inputName = `dataset_${suffix}`
  while (used.has(inputName)) { suffix += 1; inputName = `dataset_${suffix}` }
  emit('update:inputs', [...props.inputs, {
    datasetId: selection.datasetId || '',
    inputName,
    sourceType: selection.datasetId ? selection.sourceType : (selection.sourceType === 'JDBC_TABLE' ? 'JDBC_SQL' : selection.sourceType),
    sourceConfig: selection.datasourceId ? { datasourceId: selection.datasourceId } : undefined,
  }])
}

function confirmSource(value: DatasetSourceSelection & { sourceConfig?: Record<string, unknown>; displayName?: string }): void {
  const used = new Set(props.inputs.map((input) => input.inputName))
  let suffix = props.inputs.length + 1
  let inputName = `dataset_${suffix}`
  while (used.has(inputName)) { suffix += 1; inputName = `dataset_${suffix}` }
  emit('update:inputs', [...props.inputs, {
    datasetId: value.datasetId || '',
    inputName,
    sourceType: value.sourceType,
    displayName: value.displayName,
    sourceConfig: value.sourceConfig,
  }])
  sourceDialogOpen.value = false
  sourceSelection.value = null
}

function removeInput(index: number): void {
  emit('update:inputs', props.inputs.filter((_, itemIndex) => itemIndex !== index))
}

function sourceType(input: DashboardDatasetInput): string {
  if (input.sourceType) return input.sourceType
  const dataset = datasets.value.find(item => String(item.id) === String(input.datasetId))
  const type = String(dataset?.sourceType || 'JDBC_TABLE').toUpperCase()
  if (/MYSQL|POSTGRES|ORACLE|SQLSERVER|JDBC/.test(type)) return 'JDBC_TABLE'
  if (type === 'ALOUDATA') return 'ALOUDATA_METRICS'
  if (type === 'API' || type === 'HTTP') return 'HTTP_API'
  return type
}

function sourceLabel(input: DashboardDatasetInput): string {
  const type = sourceType(input)
  return ({ JDBC_SQL: 'JDBC SQL', JDBC_TABLE: 'JDBC', ALOUDATA_ANALYSIS_VIEW: 'Aloudata · 指标视图', ALOUDATA_METRICS: 'Aloudata · 指标&维度', HTTP_API: '接口', FILE: '文件' } as Record<string, string>)[type] || type
}

function datasetName(input: DashboardDatasetInput): string {
  return datasets.value.find(item => String(item.id) === String(input.datasetId))?.name || input.displayName || ''
}

function toggleFilterInput(binding: DashboardScriptFilterBinding | undefined, filterComponentId: string, inputName: string, checked: boolean): void {
  const current = binding || { filterComponentId, inputNames: [], fieldMappings: {} }
  const inputNames = checked ? [...new Set([...current.inputNames, inputName])] : current.inputNames.filter(name => name !== inputName)
  const next = [...filterBindings.value.filter(item => item.filterComponentId !== filterComponentId), { ...current, filterComponentId, inputNames }]
  emit('update:filter-bindings', next)
}

function updateInput(index: number, patch: Partial<DashboardDatasetInput>): void {
  emit('update:inputs', props.inputs.map((input, itemIndex) => itemIndex === index ? { ...normalizeDatasetInput(input), ...patch } : input))
}

function updateSourceConfig(index: number, patch: DashboardDatasetInput['sourceConfig']): void {
  updateInput(index, { sourceType: 'JDBC_SQL', sourceConfig: { ...(props.inputs[index].sourceConfig || {}), ...patch } })
}

function toggleMapping(index: number): void {
  mappingDrafts[index] = [...(props.inputs[index].fieldMappings || [])].map(item => ({ ...item }))
  mappingOpen[index] = !mappingOpen[index]
}
function toggleFilters(index: number): void {
  filterDrafts[index] = [...(props.inputs[index].filters || [])].map(item => ({ ...item }))
  filtersOpen[index] = !filtersOpen[index]
}

function mappingTarget(index: number, input: DashboardDatasetInput, source: string): string {
  return mappingDrafts[index]?.find(item => item.source === source)?.target || input.fieldMappings?.find(item => item.source === source)?.target || source
}

function updateMapping(index: number, source: string, target: string): void {
  const current = [...(mappingDrafts[index] || props.inputs[index].fieldMappings || [])].filter(item => item.source !== source)
  mappingDrafts[index] = [...current, { source, target: target || source }]
}

function updateFilter(index: number, filterIndex: number, patch: Partial<DatasetFilter>): void {
  const filters = [...(filterDrafts[index] || props.inputs[index].filters || [])]
  filters[filterIndex] = { ...filters[filterIndex], role: filters[filterIndex].role || 'dimension', ...patch } as DatasetFilter
  filterDrafts[index] = filters
}

function addFilter(index: number): void {
  filterDrafts[index] = [...(filterDrafts[index] || props.inputs[index].filters || []), { field: '', role: 'dimension', operator: 'eq', value: '' }]
}

function commitMapping(index: number): void {
  const mappings = mappingDrafts[index] || []
  const sourceFields = new Set((descriptors[props.inputs[index].datasetId]?.schema || []).map(column => column.name))
  const targets = mappings.map(item => item.target.trim())
  if (mappings.some(item => !item.source.trim() || !sourceFields.has(item.source) || !item.target.trim())) { ElMessage.warning('字段映射必须引用当前数据集字段，目标名称不能为空'); return }
  if (new Set(targets).size !== targets.length) { ElMessage.warning('目标字段名称不能重复'); return }
  updateInput(index, { fieldMappings: mappings }); mappingOpen[index] = false
}
function cancelMapping(index: number): void { mappingOpen[index] = false; delete mappingDrafts[index] }
function commitFilters(index: number): void {
  const filters = filterDrafts[index] || []
  const fields = new Set((descriptors[props.inputs[index].datasetId]?.schema || []).map(column => column.name))
  if (filters.some(filter => !filter.field.trim() || !fields.has(filter.field))) { ElMessage.warning('筛选字段必须属于当前数据集'); return }
  if (filters.some(filter => (filter.operator === 'between' || filter.operator === 'in') && (!Array.isArray(filter.value) && !String(filter.value ?? '').trim()))) { ElMessage.warning('多选或范围筛选需要填写值'); return }
  updateInput(index, { filters }); filtersOpen[index] = false
}
function cancelFilters(index: number): void { filtersOpen[index] = false; delete filterDrafts[index] }

function changeDataset(index: number, datasetId: string): void {
  const next = props.inputs.map((input, itemIndex) => itemIndex === index ? { ...input, datasetId } : input)
  emit('update:inputs', next)
  if (datasetId) {
    void loadDescriptor(datasetId)
  }
}

function changeAlias(index: number, inputName: string): void {
  emit('update:inputs', props.inputs.map((input, itemIndex) => itemIndex === index ? { ...input, inputName } : input))
}

function aliasStatus(inputName: string): string {
  if (!isValidDatasetInputAlias(inputName)) {
    return '别名须以字母开头，仅允许字母、数字和下划线（最多 64 个字符）'
  }
  const duplicate = props.inputs.filter((input) => input.inputName === inputName).length > 1
  return duplicate ? '别名不能重复' : 'ok'
}

function previewKey(input: DashboardDatasetInput): string {
  return `${input.datasetId}:${input.inputName}`
}

async function previewInput(input: DashboardDatasetInput): Promise<void> {
  if (!input.datasetId || aliasStatus(input.inputName) !== 'ok') {
    ElMessage.warning('请先选择数据集并填写唯一合法别名')
    return
  }
  const key = previewKey(input)
  previewingKey.value = key
  try {
    const batch = await datasetApi.previewInput({
      datasetId: input.datasetId,
      inputName: input.inputName,
      columns: input.fieldMappings?.map(mapping => mapping.source),
      filters: input.filters,
      limit: 20,
    }) as unknown as DatasetBatch
    previewResults[key] = batch
    hydrateDescriptor(input.datasetId, batch)
  } catch {
    ElMessage.warning('数据集输入预览失败')
  } finally {
    if (previewingKey.value === key) {
      previewingKey.value = ''
    }
  }
}

function insertTemplate(): void {
  const validInputs = props.inputs.filter((input) => input.datasetId && isValidDatasetInputAlias(input.inputName))
  if (!validInputs.length) {
    ElMessage.warning('请先添加一个合法的数据集输入别名')
    return
  }
  const generated = buildSystemScript(validInputs, parameters.value, filterBindings.value)
  emit('update:script', mergeBaseScript(props.script, generated))
  ElMessage.success('系统区域已生成，用户处理区域保持不变')
}

function updateUserScript(value: string): void {
  if (systemScript.value) {
    emit('update:script', `${systemScript.value}\n\n${USER_SCRIPT_START}\n${value}`)
  } else {
    emit('update:script', value)
  }
}

function addParameter(): void {
  const used = new Set(parameters.value.map((parameter) => parameter.name))
  let suffix = parameters.value.length + 1
  let name = `parameter_${suffix}`
  while (used.has(name)) {
    suffix += 1
    name = `parameter_${suffix}`
  }
  emit('update:parameters', [...parameters.value, { name, type: 'string', scope: 'dashboard' }])
}

function updateParameter(index: number, patch: Partial<DashboardScriptParameter>): void {
  emit('update:parameters', parameters.value.map((parameter, itemIndex) => itemIndex === index ? { ...parameter, ...patch } : parameter))
}

function removeParameter(index: number): void {
  emit('update:parameters', parameters.value.filter((_, itemIndex) => itemIndex !== index))
}

function formatCell(value: unknown): string {
  if (value == null) return ''
  return typeof value === 'object' ? JSON.stringify(value) : String(value)
}

function previewColumns(batch: DatasetBatch): string[] {
  const columns = new Set<string>()
  batch.rows.forEach((row) => Object.keys(row).forEach((key) => columns.add(key)))
  return [...columns]
}

function inferDataType(value: unknown): string {
  if (typeof value === 'number') return 'NUMBER'
  if (typeof value === 'boolean') return 'BOOLEAN'
  if (value instanceof Date) return 'DATETIME'
  return 'STRING'
}

function hydrateDescriptor(datasetId: string, batch: DatasetBatch): void {
  const current = descriptors[datasetId]
  if (current?.schema?.length || !batch.rows.length) return
  const first = batch.rows[0]
  const schema: DatasetInputColumn[] = previewColumns(batch).map((name) => ({
    name,
    title: name,
    dataType: inferDataType(first[name]),
    nullable: batch.rows.some((row) => row[name] == null),
  }))
  descriptors[datasetId] = { ...current, schema }
}

async function executeScriptPreview(): Promise<void> {
  if (!props.dashboardId || !props.script?.trim()) return
  if (props.inputs.length >= 2 && !userScript.value.trim()) {
    executionError.value = '请在用户处理区域补充 Join、合并或计算逻辑后再预览'
    return
  }
  executionLoading.value = true
  executionError.value = ''
  executionResult.value = null
  executionOutputRef.value = null
  executionId.value = ''
  cancelRequested.value = false
  try {
    assertUniqueDatasetInputAliases(props.inputs.map((input) => input.inputName))
    if (props.inputs.some((input) => !input.datasetId)) {
      throw new Error('每个数据集输入都必须选择数据集')
    }
    assertValidScriptParameters(parameters.value)
    const created = props.targetComponentId
      ? await insightDashboardApi.executeComponent(props.dashboardId, props.targetComponentId)
      : await insightDashboardApi.execute(props.dashboardId)
    executionId.value = (created as unknown as { executionId: string }).executionId
    if (!executionId.value) throw new Error('未获取到执行 ID')
    for (let attempt = 0; attempt < 120; attempt += 1) {
      if (cancelRequested.value) return
      const status = await insightDashboardApi.getExecutionStatus(executionId.value) as unknown as { status?: string; result?: string; outputRef?: unknown; error?: string }
      if (status.status === 'SUCCEEDED') {
        executionResult.value = status.result ? JSON.parse(status.result) : null
        return
      }
      if (status.status === 'RESULT_REF') {
        const result = await insightDashboardApi.getExecutionResult(executionId.value)
        const resolved = result as unknown as { rows: unknown; outputRef?: DatasetObjectRef }
        executionResult.value = resolved.rows
        executionOutputRef.value = resolved.outputRef ?? null
        return
      }
      if (status.status && status.status !== 'RUNNING') {
        throw new Error(status.error || `执行未成功：${status.status}`)
      }
      await new Promise((resolve) => setTimeout(resolve, 500))
    }
    throw new Error('执行超时，请到任务状态查看')
  } catch (error: any) {
    if (!cancelRequested.value) executionError.value = error?.message || '最终结果预览失败'
  } finally {
    executionLoading.value = false
  }
}

async function cancelScriptPreview(): Promise<void> {
  if (!executionId.value) return
  cancelRequested.value = true
  try {
    await insightDashboardApi.cancelExecution(executionId.value)
    executionError.value = '执行已取消，可点击“重试”重新运行'
  } catch (error: any) {
    cancelRequested.value = false
    executionError.value = error?.message || '取消执行失败'
  } finally {
    executionLoading.value = false
  }
}

async function loadDescriptor(datasetId: string): Promise<void> {
  loadingDatasetId.value = datasetId
  try {
    descriptors[datasetId] = await datasetApi.getInputDescriptor(datasetId) as unknown as DatasetInputDescriptor
    if (!descriptors[datasetId].schema?.length) {
      const batch = await datasetApi.previewInput({ datasetId, inputName: 'dataset', limit: 20 }) as unknown as DatasetBatch
      hydrateDescriptor(datasetId, batch)
    }
  } catch {
    ElMessage.warning('数据集字段加载失败')
  } finally {
    if (loadingDatasetId.value === datasetId) {
      loadingDatasetId.value = ''
    }
  }
}

defineExpose({
  validate(): boolean {
    try {
      assertUniqueDatasetInputAliases(props.inputs.map((input) => input.inputName))
      return props.inputs.every((input) => Boolean(input.datasetId))
    } catch {
      return false
    }
  },
})
</script>

<style scoped>
.dataset-input-panel {
  padding: 14px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.panel-heading,
.row-header,
.descriptor-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.script-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.panel-heading h3 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
}

.panel-heading p {
  margin: 4px 0 12px;
  color: var(--el-text-color-secondary);
  font-size: 11px;
  line-height: 1.4;
}

.dataset-input-row {
  padding: 10px 0;
  border-top: 1px solid var(--el-border-color-extra-light);
}

.row-header {
  margin-bottom: 7px;
  color: var(--el-text-color-regular);
  font-size: 12px;
}

.full-width,
.alias-input {
  width: 100%;
  margin-bottom: 7px;
}

.field-error {
  margin: -3px 0 6px;
  color: var(--el-color-danger);
  font-size: 11px;
  line-height: 1.4;
}

.descriptor-summary {
  justify-content: flex-start;
  color: var(--el-text-color-secondary);
  font-size: 11px;
}

.script-draft {
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.preview-result {
  max-height: 160px;
  margin: 8px 0 0;
  overflow: auto;
  padding: 8px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-size: 10px;
  white-space: pre-wrap;
}

.input-preview-card {
  max-width: 100%;
  margin-top: 8px;
  padding: 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-fill-color-light);
}

.preview-meta {
  margin-bottom: 6px;
  color: var(--el-text-color-secondary);
  font-size: 11px;
}

.preview-table-scroll {
  max-width: 100%;
  overflow-x: auto;
}

.preview-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 11px;
  white-space: nowrap;
}

.preview-table th,
.preview-table td {
  max-width: 180px;
  padding: 5px 7px;
  overflow: hidden;
  text-align: left;
  text-overflow: ellipsis;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.preview-table th {
  color: var(--el-text-color-secondary);
  font-weight: 600;
}

.result-limit-notice {
  padding: 7px 9px;
  color: var(--el-color-warning-dark);
  font-size: 11px;
  background: var(--el-color-warning-light-9);
  border-bottom: 1px solid var(--el-color-warning-light-5);
}

.result-table-wrap {
  max-width: 100%;
  margin-top: 8px;
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.preview-tabs { display: flex; gap: 2px; padding: 4px; background: var(--el-fill-color-light); border-bottom: 1px solid var(--el-border-color-lighter); }
.preview-tab { border: 0; padding: 5px 9px; color: var(--el-text-color-secondary); background: transparent; cursor: pointer; font-size: 11px; border-radius: 3px; }
.preview-tab.active { color: var(--el-color-primary); background: var(--el-bg-color); font-weight: 600; box-shadow: 0 0 0 1px var(--el-color-primary-light-8); }
.execution-feedback { padding: 8px 10px; min-height: 70px; background: var(--el-bg-color); font-size: 11px; }
.feedback-row { display: flex; justify-content: space-between; gap: 12px; padding: 4px 0; border-bottom: 1px solid var(--el-border-color-extra-light); }
.feedback-row span:last-child { color: var(--el-text-color-secondary); text-align: right; word-break: break-all; }
.feedback-log { padding: 4px 0; color: var(--el-text-color-secondary); }

.result-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 11px;
  white-space: nowrap;
}

.result-table th,
.result-table td {
  padding: 5px 7px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  text-align: left;
}

.result-table th {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  font-weight: 600;
}

.execution-alert {
  margin-top: 8px;
}

.parameters-panel {
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.row-header small {
  display: block;
  margin-top: 3px;
  color: var(--el-text-color-secondary);
  font-size: 10px;
}

.parameter-row {
  display: grid;
  grid-template-columns: 1fr 0.8fr 0.8fr auto;
  gap: 5px;
  margin-top: 7px;
}

.binding-target-hint { margin: 4px 0 0; color: var(--el-text-color-secondary); font-size: 11px; }

.dataset-source-card { padding: 10px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; background: var(--el-bg-color); }
.add-dataset-entry { position: relative; display: flex; align-items: center; gap: 4px; }
.dataset-picker-popover { position: absolute; z-index: 20; top: 34px; right: 0; width: 300px; padding: 10px; border: 1px solid var(--el-border-color-light); border-radius: 8px; background: var(--el-bg-color-overlay); box-shadow: var(--el-box-shadow-light); }
.dataset-card-title { display: flex; align-items: center; gap: 8px; }
.dataset-source-badge { padding: 2px 6px; border-radius: 999px; color: var(--el-color-primary); background: var(--el-color-primary-light-9); font-size: 10px; }
.dataset-config-body { margin-top: 8px; padding-top: 8px; border-top: 1px dashed var(--el-border-color-lighter); }
.source-config-block { display: flex; flex-direction: column; gap: 5px; }
.source-config-block .form-label { color: var(--el-text-color-secondary); font-size: 11px; font-weight: 600; }
.dataset-sql-editor { width: 100%; min-height: 84px; padding: 8px; box-sizing: border-box; resize: vertical; border: 1px solid var(--el-border-color); border-radius: 5px; background: var(--el-fill-color-blank); color: var(--el-text-color-primary); font: 12px/1.5 ui-monospace, SFMono-Regular, Menlo, monospace; }
.dataset-sql-editor:focus { outline: none; border-color: var(--el-color-primary); box-shadow: 0 0 0 2px var(--el-color-primary-light-8); }
.dataset-card-actions { display: flex; gap: 4px; margin-top: 6px; }
.inline-editor { display: flex; flex-direction: column; gap: 6px; margin-top: 6px; padding: 8px; border-radius: 6px; background: var(--el-fill-color-light); }
.inline-editor-title { color: var(--el-text-color-secondary); font-size: 11px; font-weight: 600; }
.mapping-row, .filter-row { display: grid; grid-template-columns: 1fr 1fr; align-items: center; gap: 6px; font-size: 11px; }
.filter-row { grid-template-columns: 1fr .8fr 1fr; }
.mapping-row input, .filter-row input, .filter-row select { width: 100%; min-width: 0; padding: 5px 6px; border: 1px solid var(--el-border-color); border-radius: 4px; background: var(--el-bg-color); color: var(--el-text-color-primary); font-size: 11px; box-sizing: border-box; }
.unified-source-hint { padding: 7px 8px; color: var(--el-text-color-secondary); background: var(--el-fill-color-light); border-radius: 5px; font-size: 11px; line-height: 1.4; }
.system-script-block, .user-script-block { display: flex; flex-direction: column; gap: 5px; margin-top: 8px; }
.system-script-preview, .user-script-editor { width: 100%; box-sizing: border-box; padding: 8px; resize: vertical; border: 1px solid var(--el-border-color); border-radius: 5px; color: var(--el-text-color-primary); font: 12px/1.5 ui-monospace, SFMono-Regular, Menlo, monospace; }
.system-script-preview { background: var(--el-fill-color-light); color: var(--el-text-color-secondary); }
.user-script-editor { background: var(--el-fill-color-blank); }
.system-script-preview:focus, .user-script-editor:focus { outline: none; border-color: var(--el-color-primary); box-shadow: 0 0 0 2px var(--el-color-primary-light-8); }
.filter-binding-panel { padding: 12px 0; border-top: 1px solid var(--el-border-color-lighter); }
.filter-binding-row { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 7px; font-size: 11px; }
.filter-binding-name { min-width: 72px; color: var(--el-text-color-primary); font-weight: 600; }
.filter-binding-check { display: inline-flex; align-items: center; gap: 3px; color: var(--el-text-color-secondary); }
</style>
