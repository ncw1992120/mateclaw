<template>
  <section class="dataset-input-panel">
    <div class="panel-heading">
      <div>
        <h3>脚本结果数据集输入</h3>
        <p>这是独立于“直接指标绑定”的脚本模式；脚本通过别名调用 datasets.read。</p>
        <p class="binding-target-hint">{{ targetComponentId ? `当前目标组件：${targetComponentId}` : '当前未选择目标组件，执行结果不会覆盖画布。' }}</p>
        <div class="binding-target-selector">
          <span>结果绑定组件</span>
          <el-select
            :model-value="targetComponentId"
            clearable
            filterable
            aria-label="选择要接收脚本结果的组件"
            placeholder="选择要接收脚本结果的组件"
            @change="(value: string) => emit('update:target-component-id', value || '')"
          >
            <el-option
              v-for="component in targetComponents"
              :key="component.id"
              :label="component.title || component.id"
              :value="component.id"
            />
          </el-select>
        </div>
      </div>
      <el-button size="small" type="primary" plain @click="addInput">添加</el-button>
    </div>

    <el-empty v-if="inputs.length === 0" description="暂未配置数据集输入" :image-size="56" />

    <div v-for="(input, index) in inputs" :key="`${input.datasetId}-${index}`" class="dataset-input-row">
      <div class="row-header">
        <span>输入 {{ index + 1 }}</span>
        <el-button text type="danger" size="small" @click="removeInput(index)">移除</el-button>
      </div>
      <el-select
        :model-value="input.datasetId"
        filterable
        clearable
        aria-label="选择已授权数据集"
        placeholder="选择已授权数据集"
        class="full-width"
        @change="(value: string) => changeDataset(index, value)"
      >
        <el-option v-for="dataset in datasets" :key="dataset.id" :label="dataset.name" :value="dataset.id" />
      </el-select>
      <el-input
        :model-value="input.inputName"
        class="alias-input"
        :aria-describedby="aliasStatus(input.inputName) === 'ok' ? undefined : `dataset-alias-error-${index}`"
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

    <div class="script-draft">
      <div class="row-header">
        <span>Python 脚本草稿</span>
        <div class="script-actions">
          <el-button text size="small" @click="insertTemplate">插入读取模板</el-button>
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
      <el-input
        :model-value="script"
        type="textarea"
        :rows="7"
        resize="vertical"
        placeholder="在此维护脚本草稿；平台不会自动生成或直接执行。"
        @update:model-value="(value: string) => emit('update:script', value)"
      />
      <el-alert v-if="executionError" class="execution-alert" type="error" :closable="false" :title="executionError" />
      <div v-if="executionRows.length > 0" class="result-table-wrap">
        <div v-if="executionOutputRef" class="result-limit-notice">
          仅展示受限预览；完整结果已保存为 ObjectRef：{{ executionOutputRef.uri || executionOutputRef.objectId || '受控引用' }}
        </div>
        <table class="result-table">
          <thead><tr><th v-for="column in executionColumns" :key="column">{{ column }}</th></tr></thead>
          <tbody>
            <tr v-for="(row, rowIndex) in executionRows" :key="rowIndex">
              <td v-for="column in executionColumns" :key="column">{{ formatCell(row[column]) }}</td>
            </tr>
          </tbody>
        </table>
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
          placeholder="参数名"
          @update:model-value="(value: string) => updateParameter(index, { name: value })"
        />
        <el-select
          :model-value="parameter.type"
          placeholder="类型"
          @update:model-value="(value: DashboardScriptParameter['type']) => updateParameter(index, { type: value })"
        >
          <el-option v-for="type in parameterTypes" :key="type" :label="type" :value="type" />
        </el-select>
        <el-select
          :model-value="parameter.scope"
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
import type { Dataset, DatasetInputDescriptor, DatasetInputColumn, DashboardDatasetInput, DatasetBatch, DashboardScriptParameter, DatasetObjectRef, InsightComponent } from '@/types'
import * as datasetApi from '@/api/dataset'
import * as insightDashboardApi from '@/api/insight-dashboard'
import {
  assertUniqueDatasetInputAliases,
  assertValidScriptParameters,
  isValidDatasetInputAlias,
} from '@/utils/dataset-inputs'

const props = defineProps<{
  inputs: DashboardDatasetInput[]
  script?: string
  dashboardId?: string
  parameters?: DashboardScriptParameter[]
  targetComponentId?: string
  targetComponents?: InsightComponent[]
}>()
const emit = defineEmits<{
  (e: 'update:inputs', value: DashboardDatasetInput[]): void
  (e: 'update:script', value: string): void
  (e: 'update:parameters', value: DashboardScriptParameter[]): void
  (e: 'update:target-component-id', value: string): void
  (e: 'apply-result', value: Record<string, unknown>[]): void
}>()

const datasets = ref<Dataset[]>([])
const descriptors = reactive<Record<string, DatasetInputDescriptor>>({})
const previewResults = reactive<Record<string, DatasetBatch>>({})
const loadingDatasetId = ref('')
const previewingKey = ref('')
const executionLoading = ref(false)
const executionError = ref('')
const executionResult = ref<unknown | null>(null)
const executionOutputRef = ref<DatasetObjectRef | null>(null)
const executionId = ref('')
const cancelRequested = ref(false)
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
const targetComponents = computed(() => (props.targetComponents ?? []).filter((component) => ['kpi', 'chart', 'table'].includes(component.type)))

onMounted(async () => {
  try {
    datasets.value = await datasetApi.list() as unknown as Dataset[]
  } catch {
    // API 层已经展示错误；面板保留空状态，避免影响旧仪表盘编辑。
  }
  for (const input of props.inputs) {
    if (input.datasetId) {
      void loadDescriptor(input.datasetId)
    }
  }
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

function removeInput(index: number): void {
  emit('update:inputs', props.inputs.filter((_, itemIndex) => itemIndex !== index))
}

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
  const first = props.inputs.find((input) => input.datasetId && isValidDatasetInputAlias(input.inputName))
  if (!first) {
    ElMessage.warning('请先添加一个合法的数据集输入别名')
    return
  }
  const template = `# datasets 由 Runner 注入，读取条件会在 DataAgent 侧下推\nresult = datasets.read(\n    input_name="${first.inputName}",\n    columns=[],\n    filters=[],\n).to_polars()\n`
  emit('update:script', props.script?.trim() ? `${props.script.trim()}\n\n${template}` : template)
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
    const created = await insightDashboardApi.execute(props.dashboardId)
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
</style>
