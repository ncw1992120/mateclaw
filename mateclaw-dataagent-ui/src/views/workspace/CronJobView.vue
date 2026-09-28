<template>
  <div class="cron-job-page">
    <div class="page-header">
      <div class="page-header-left">
        <h1 class="page-title">{{ t('cronJob.title') }}</h1>
        <p class="page-desc">{{ t('cronJob.desc') }}</p>
      </div>
      <div v-if="canManage" class="page-header-actions">
        <button class="btn-create-pill" @click="openCreateModal">
          <el-icon :size="14"><Plus /></el-icon>
          {{ t('cronJob.create') }}
        </button>
      </div>
    </div>

    <div class="page-body surface-card">
      <el-table v-loading="loading" :data="cronJobs" class="mc-table">
        <el-table-column prop="name" :label="t('cronJob.colName')" min-width="140">
          <template #default="{ row }">
            <span class="cell-name">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="agentName" :label="t('cronJob.colAgent')" min-width="120" />
        <el-table-column prop="taskType" :label="t('cronJob.colTaskType')" width="120">
          <template #default="{ row }">
            <span class="mc-tag" :class="row.taskType">{{ taskTypeLabel(row.taskType) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="cronExpression" :label="t('cronJob.colCron')" min-width="120" />
        <el-table-column prop="enabled" :label="t('cronJob.colStatus')" width="80">
          <template #default="{ row }">
            <el-switch
              v-if="canManage"
              :model-value="row.enabled"
              size="small"
              @change="(val: boolean) => handleToggle(row, val)"
            />
            <span v-else class="status-text" :class="row.enabled ? 'on' : 'off'">
              {{ row.enabled ? t('cronJob.statusEnabled') : t('cronJob.statusDisabled') }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="nextRunTime" :label="t('cronJob.colNextRun')" width="150">
          <template #default="{ row }">{{ formatDateTime(row.nextRunTime) }}</template>
        </el-table-column>
        <el-table-column prop="lastRunTime" :label="t('cronJob.colLastRun')" width="150">
          <template #default="{ row }">{{ formatDateTime(row.lastRunTime) }}</template>
        </el-table-column>
        <el-table-column prop="lastDeliveryStatus" :label="t('cronJob.colDelivery')" width="100">
          <template #default="{ row }">
            <span class="mc-tag" :class="deliveryClass(row.lastDeliveryStatus)">{{ deliveryLabel(row.lastDeliveryStatus) }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.action')" width="110" fixed="right">
          <template #default="{ row }">
            <div v-if="!canManage" class="row-actions">
              <el-icon :size="14" class="action-icon" @click="openDetail(row)">
                <View />
              </el-icon>
            </div>
            <div v-else class="row-actions">
              <el-icon :size="14" class="action-icon" @click="handleRunNow(row)" :title="t('cronJob.runNow')">
                <VideoPlay />
              </el-icon>
              <el-icon :size="14" class="action-icon" @click="openEditModal(row)" :title="t('common.edit')">
                <Edit />
              </el-icon>
              <el-icon :size="14" class="action-icon danger" @click="handleDelete(row)" :title="t('common.delete')">
                <Delete />
              </el-icon>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新建/编辑弹窗 -->
    <el-dialog
      v-model="showModal"
      :title="isEdit ? t('cronJob.editTitle') : t('cronJob.createTitle')"
      width="600px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <div class="form-body">
        <div class="form-group">
          <label class="form-label">{{ t('cronJob.fieldName') }} *</label>
          <el-input v-model="form.name" :placeholder="t('cronJob.namePlaceholder')" />
        </div>
        <div class="form-group">
          <label class="form-label">{{ t('cronJob.fieldAgent') }} *</label>
          <el-select v-model="form.agentId" :placeholder="t('cronJob.agentPlaceholder')" filterable style="width: 100%">
            <el-option
              v-for="agent in agents"
              :key="agent.id"
              :label="agent.name"
              :value="agent.id"
            />
          </el-select>
        </div>
        <div class="form-group">
          <label class="form-label">{{ t('cronJob.fieldTaskType') }} *</label>
          <el-select v-model="form.taskType" :placeholder="t('cronJob.taskTypePlaceholder')" style="width: 100%">
            <el-option label="LLM 对话" value="text" />
            <el-option label="Plan-Execute" value="agent" />
            <el-option label="直接推送" value="reminder" />
          </el-select>
        </div>
        <div class="form-group">
          <label class="form-label">{{ t('cronJob.fieldCron') }} *</label>
          <CronExpressionField v-model="form.cronExpression" />
        </div>
        <div class="form-group" v-if="form.taskType === 'text' || form.taskType === 'reminder'">
          <label class="form-label">{{ t('cronJob.fieldTrigger') }} *</label>
          <el-input
            v-model="form.triggerMessage"
            type="textarea"
            :rows="3"
            :placeholder="t('cronJob.triggerPlaceholder')"
          />
        </div>
        <div class="form-group" v-if="form.taskType === 'agent'">
          <label class="form-label">{{ t('cronJob.fieldTarget') }} *</label>
          <el-input
            v-model="form.requestBody"
            type="textarea"
            :rows="3"
            :placeholder="t('cronJob.targetPlaceholder')"
          />
        </div>
        <div class="form-group">
          <label class="form-label">{{ t('cronJob.fieldTimezone') }}</label>
          <el-select v-model="form.timezone" :placeholder="t('cronJob.timezonePlaceholder')" filterable style="width: 100%">
            <el-option
              v-for="tz in timezoneOptions"
              :key="tz"
              :label="tz"
              :value="tz"
            />
          </el-select>
        </div>
        <div class="form-group" v-if="!isEdit">
          <el-switch v-model="form.enabled" :active-text="t('cronJob.enabledOn')" :inactive-text="t('cronJob.enabledOff')" />
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="showModal = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            {{ t('common.save') }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 查看详情弹窗（只读） -->
    <el-dialog
      v-model="showDetail"
      :title="t('cronJob.detailTitle')"
      width="560px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <div v-loading="detailLoading" class="detail-body">
        <template v-if="detail">
          <div class="detail-row">
            <span class="detail-label">{{ t('cronJob.fieldName') }}</span>
            <span class="detail-value">{{ detail.name || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">{{ t('cronJob.fieldAgent') }}</span>
            <span class="detail-value">{{ detail.agentName || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">{{ t('cronJob.fieldTaskType') }}</span>
            <span class="detail-value">{{ taskTypeLabel(detail.taskType) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">{{ t('cronJob.fieldCron') }}</span>
            <span class="detail-value detail-value--mono">{{ detail.cronExpression || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">{{ t('cronJob.fieldTimezone') }}</span>
            <span class="detail-value">{{ detail.timezone || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">{{ t('cronJob.colStatus') }}</span>
            <span class="detail-value" :class="detail.enabled ? 'status-on' : 'status-off'">
              {{ detail.enabled ? t('cronJob.statusEnabled') : t('cronJob.statusDisabled') }}
            </span>
          </div>
          <div class="detail-row">
            <span class="detail-label">{{ t('cronJob.colNextRun') }}</span>
            <span class="detail-value">{{ formatDateTime(detail.nextRunTime) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">{{ t('cronJob.colLastRun') }}</span>
            <span class="detail-value">{{ formatDateTime(detail.lastRunTime) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">{{ t('cronJob.colDelivery') }}</span>
            <span class="detail-value">{{ deliveryLabel(detail.lastDeliveryStatus) }}</span>
          </div>
          <template v-if="detail.taskType === 'text' || detail.taskType === 'reminder'">
            <div class="detail-row detail-row--block">
              <span class="detail-label">{{ t('cronJob.fieldTrigger') }}</span>
              <span class="detail-value detail-value--pre">{{ detail.triggerMessage || '-' }}</span>
            </div>
          </template>
          <template v-else-if="detail.taskType === 'agent'">
            <div class="detail-row detail-row--block">
              <span class="detail-label">{{ t('cronJob.fieldTarget') }}</span>
              <span class="detail-value detail-value--pre">{{ detail.requestBody || '-' }}</span>
            </div>
          </template>
        </template>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="showDetail = false">{{ t('common.close') }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, VideoPlay, Edit, Delete, View } from '@element-plus/icons-vue'
import { formatDateTime } from '@/utils/time'
import { usePermission, PERMISSION } from '@/composables/usePermission'
import * as cronJobApi from '@/api/cron-job'
import * as agentApi from '@/api/agent'
import type { CronJob, CronJobForm } from '@/api/cron-job'
import type { Agent } from '@/types'
import CronExpressionField from '@/components/CronExpressionField.vue'

const { t } = useI18n()
const { hasPermission } = usePermission()

const loading = ref(false)
const cronJobs = ref<CronJob[]>([])
const agents = ref<Agent[]>([])
const showModal = ref(false)
const submitting = ref(false)
const isEdit = ref(false)
const editingId = ref<number | string | null>(null)

/** 查看详情弹窗状态 */
const showDetail = ref(false)
const detailLoading = ref(false)
const detail = ref<CronJob | null>(null)

const canManage = computed(() => hasPermission(PERMISSION.CRON_JOB_MANAGE))

const form = reactive<CronJobForm>({
  name: '',
  cronExpression: '',
  timezone: 'Asia/Shanghai',
  agentId: '',
  taskType: 'text',
  triggerMessage: '',
  requestBody: '',
  enabled: true,
})

/** 常用时区选项 */
const timezoneOptions = [
  'Asia/Shanghai',
  'Asia/Tokyo',
  'Asia/Singapore',
  'Asia/Kolkata',
  'America/New_York',
  'America/Chicago',
  'America/Los_Angeles',
  'Europe/London',
  'Europe/Berlin',
  'UTC',
]

onMounted(() => {
  loadCronJobs()
  loadAgents()
})

async function loadCronJobs(): Promise<void> {
  loading.value = true
  try {
    cronJobs.value = await cronJobApi.listCronJobs()
  } catch {
    // 错误已由 axios 拦截器提示
  } finally {
    loading.value = false
  }
}

async function loadAgents(): Promise<void> {
  try {
    agents.value = await agentApi.list()
  } catch {
    // 错误已由 axios 拦截器提示
  }
}

function resetForm(): void {
  form.name = ''
  form.cronExpression = ''
  form.timezone = 'Asia/Shanghai'
  form.agentId = ''
  form.taskType = 'text'
  form.triggerMessage = ''
  form.requestBody = ''
  form.enabled = true
  editingId.value = null
  isEdit.value = false
}

function openCreateModal(): void {
  resetForm()
  showModal.value = true
}

function openEditModal(row: CronJob): void {
  resetForm()
  isEdit.value = true
  editingId.value = row.id
  form.name = row.name || ''
  form.cronExpression = row.cronExpression || ''
  form.timezone = row.timezone || 'Asia/Shanghai'
  form.agentId = row.agentId || ''
  form.taskType = row.taskType || 'text'
  form.triggerMessage = row.triggerMessage || ''
  form.requestBody = row.requestBody || ''
  form.enabled = row.enabled ?? true
  showModal.value = true
}

/** 查看详情（只读）：优先拉取详情接口，失败时回退列表数据 */
async function openDetail(row: CronJob): Promise<void> {
  showDetail.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await cronJobApi.getCronJob(row.id)
  } catch {
    detail.value = { ...row }
  } finally {
    detailLoading.value = false
  }
}

function taskTypeLabel(type: string): string {
  const map: Record<string, string> = {
    text: 'LLM 对话',
    agent: 'Plan-Execute',
    reminder: '直接推送',
  }
  return map[type] || type
}

function deliveryLabel(status: string): string {
  const map: Record<string, string> = {
    NONE: '-',
    PENDING: t('cronJob.deliveryPending'),
    DELIVERED: t('cronJob.deliveryDelivered'),
    NOT_DELIVERED: t('cronJob.deliveryFailed'),
  }
  return map[status] || status || '-'
}

function deliveryClass(status: string): string {
  const map: Record<string, string> = {
    NONE: '',
    PENDING: 'pending',
    DELIVERED: 'delivered',
    NOT_DELIVERED: 'failed',
  }
  return map[status] || ''
}

async function handleToggle(row: CronJob, enabled: boolean): Promise<void> {
  try {
    await cronJobApi.toggleCronJob(row.id, enabled)
    ElMessage.success(enabled ? t('cronJob.enableSuccess') : t('cronJob.disableSuccess'))
    await loadCronJobs()
  } catch {
    // 错误已由 axios 拦截器提示
  }
}

async function handleRunNow(row: CronJob): Promise<void> {
  try {
    await cronJobApi.runCronJobNow(row.id)
    ElMessage.success(t('cronJob.runNowSuccess'))
  } catch {
    // 错误已由 axios 拦截器提示
  }
}

async function handleSubmit(): Promise<void> {
  const name = form.name.trim()
  if (!name) {
    ElMessage.warning(t('cronJob.nameRequired'))
    return
  }
  if (!form.cronExpression.trim()) {
    ElMessage.warning(t('cronJob.cronRequired'))
    return
  }
  if (!form.agentId) {
    ElMessage.warning(t('cronJob.agentRequired'))
    return
  }

  submitting.value = true
  try {
    const payload: CronJobForm = {
      name,
      cronExpression: form.cronExpression.trim(),
      timezone: form.timezone || 'Asia/Shanghai',
      agentId: form.agentId,
      taskType: form.taskType,
      enabled: form.enabled,
    }
    if (form.taskType === 'text' || form.taskType === 'reminder') {
      payload.triggerMessage = form.triggerMessage?.trim() || ''
    }
    if (form.taskType === 'agent') {
      payload.requestBody = form.requestBody?.trim() || ''
    }

    if (isEdit.value && editingId.value) {
      await cronJobApi.updateCronJob(editingId.value, payload)
      ElMessage.success(t('cronJob.updateSuccess'))
    } else {
      await cronJobApi.createCronJob(payload)
      ElMessage.success(t('cronJob.createSuccess'))
    }
    showModal.value = false
    await loadCronJobs()
  } catch {
    // 错误已由 axios 拦截器提示
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: CronJob): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('cronJob.deleteConfirm', { name: row.name }),
      t('common.confirm'),
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await cronJobApi.deleteCronJob(row.id)
    ElMessage.success(t('cronJob.deleteSuccess'))
    await loadCronJobs()
  } catch {
    // 错误已由 axios 拦截器提示
  }
}
</script>

<style scoped>
.cron-job-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: 16px;
  box-sizing: border-box;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.page-header-left {
  min-width: 0;
}

.page-header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-shrink: 0;
}

.page-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--db-text);
  margin: 0;
  line-height: 1.3;
}

.page-desc {
  margin: 3px 0 0;
  font-size: 12.5px;
  color: var(--db-text-secondary, var(--theme-text-secondary));
  line-height: 1.4;
}

/* 胶囊按钮：主题色实心 + 白字 + 阴影 */
.btn-create-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 16px;
  border: none;
  border-radius: 999px;
  background: var(--main-orange);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  font-family: inherit;
  box-shadow: var(--shadow-md);
  transition: filter var(--transition-fast, 0.15s);
}

.btn-create-pill:hover {
  filter: brightness(1.08);
}

.page-body {
  flex: 1;
  overflow: hidden;
  border-radius: 12px;
}

/* row-actions + action-icon */
.row-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.action-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  color: var(--db-text-secondary);
  opacity: 0.7;
  cursor: pointer;
  transition: background-color 120ms ease, color 120ms ease, opacity 120ms ease;
}

.action-icon:hover {
  opacity: 1;
  background: var(--db-hover);
  color: var(--db-text);
}

.action-icon.danger:hover {
  background: rgba(245, 63, 63, 0.1);
  color: #f53f3f;
}

.cell-name {
  color: var(--db-text);
  font-weight: 500;
}

.form-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 8px 4px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  font-size: 13px;
  color: var(--theme-text-secondary);
  font-weight: 500;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

/* 详情弹窗 */
.detail-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 8px 4px;
  min-height: 120px;
}

.detail-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.detail-row--block {
  flex-direction: column;
  gap: 6px;
}

.detail-label {
  flex-shrink: 0;
  width: 90px;
  font-size: 13px;
  color: var(--theme-text-muted);
}

.detail-value {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: var(--theme-text);
  word-break: break-all;
}

.detail-value--mono {
  font-family: 'Consolas', 'Monaco', monospace;
}

.detail-value--pre {
  white-space: pre-wrap;
  background: var(--theme-bg);
  border: 1px solid var(--theme-border);
  border-radius: 6px;
  padding: 8px 10px;
  font-size: 12.5px;
  line-height: 1.6;
  max-height: 200px;
  overflow-y: auto;
  width: 100%;
  box-sizing: border-box;
}

.status-on {
  color: var(--db-positive);
}

.status-off {
  color: var(--db-text-muted);
}
</style>
