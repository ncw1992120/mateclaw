<template>
  <div class="cron-job-page">
    <div class="page-header">
      <h1 class="page-title">{{ t('cronJob.title') }}</h1>
      <button class="btn-primary" @click="openCreateModal">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="12" y1="5" x2="12" y2="19" />
          <line x1="5" y1="12" x2="19" y2="12" />
        </svg>
        {{ t('cronJob.create') }}
      </button>
    </div>

    <div class="page-body surface-card">
      <el-table v-loading="loading" :data="cronJobs" stripe class="cron-job-table">
        <el-table-column prop="name" :label="t('cronJob.colName')" min-width="140">
          <template #default="{ row }">
            <span class="job-name" :title="row.name">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="agentName" :label="t('cronJob.colAgent')" min-width="120">
          <template #default="{ row }">
            {{ row.agentName || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="taskType" :label="t('cronJob.colTaskType')" width="100">
          <template #default="{ row }">
            <span class="task-type-tag" :class="row.taskType">{{ taskTypeLabel(row.taskType) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="cronExpression" :label="t('cronJob.colCron')" min-width="120" />
        <el-table-column prop="enabled" :label="t('cronJob.colStatus')" width="90">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled"
              size="small"
              :disabled="!canManage"
              @change="(val: boolean) => handleToggle(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="nextRunTime" :label="t('cronJob.colNextRun')" width="170" />
        <el-table-column prop="lastRunTime" :label="t('cronJob.colLastRun')" width="170" />
        <el-table-column prop="lastDeliveryStatus" :label="t('cronJob.colDelivery')" width="100">
          <template #default="{ row }">
            <span class="delivery-tag" :class="deliveryClass(row.lastDeliveryStatus)">
              {{ deliveryLabel(row.lastDeliveryStatus) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.action')" width="180" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <button class="action-link" :disabled="!canManage" @click="handleRunNow(row)">
                {{ t('cronJob.runNow') }}
              </button>
              <button class="action-link" :disabled="!canManage" @click="openEditModal(row)">
                {{ t('common.edit') }}
              </button>
              <button class="action-link danger" :disabled="!canManage" @click="handleDelete(row)">
                {{ t('common.delete') }}
              </button>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
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
  padding: 24px;
  gap: 16px;
  box-sizing: border-box;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--theme-text);
  margin: 0;
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 16px;
  border: none;
  border-radius: 8px;
  background: var(--main-orange);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-primary:hover {
  background: var(--dark-orange);
}

.page-body {
  flex: 1;
  overflow: hidden;
  border-radius: 12px;
  padding: 16px;
}

.surface-card {
  background: var(--theme-surface);
  border: 1px solid var(--theme-border);
}

.cron-job-table {
  width: 100%;
}

.job-name {
  font-weight: 600;
  color: var(--theme-text);
}

.task-type-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
  background: var(--theme-surface-hover);
  color: var(--theme-text-secondary);
}

.task-type-tag.text {
  background: rgba(59, 130, 246, 0.12);
  color: #3b82f6;
}

.task-type-tag.agent {
  background: rgba(139, 92, 246, 0.12);
  color: #8b5cf6;
}

.task-type-tag.reminder {
  background: rgba(16, 185, 129, 0.12);
  color: #10b981;
}

.delivery-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
  background: var(--theme-surface-hover);
  color: var(--theme-text-secondary);
}

.delivery-tag.pending {
  background: rgba(245, 158, 11, 0.12);
  color: #f59e0b;
}

.delivery-tag.delivered {
  background: rgba(16, 185, 129, 0.12);
  color: #10b981;
}

.delivery-tag.failed {
  background: rgba(239, 68, 68, 0.12);
  color: #ef4444;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.action-link {
  border: none;
  background: transparent;
  color: var(--main-orange);
  font-size: 13px;
  cursor: pointer;
  padding: 0;
}

.action-link:hover:not(:disabled) {
  text-decoration: underline;
}

.action-link:disabled {
  color: var(--theme-text-muted);
  cursor: not-allowed;
}

.action-link.danger {
  color: #e53e3e;
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
</style>
