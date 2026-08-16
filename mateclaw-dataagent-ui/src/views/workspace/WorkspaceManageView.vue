<template>
  <div class="workspace-manage-page">
    <div class="page-header">
      <h1 class="page-title">{{ t('workspaceManage.title') }}</h1>
      <button class="btn-primary" @click="openCreateModal">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="12" y1="5" x2="12" y2="19" />
          <line x1="5" y1="12" x2="19" y2="12" />
        </svg>
        {{ t('workspaceManage.create') }}
      </button>
    </div>

    <div class="page-body surface-card">
      <el-table v-loading="loading" :data="workspaces" stripe class="workspace-table">
        <el-table-column prop="name" :label="t('workspaceManage.colName')" min-width="160">
          <template #default="{ row }">
            <span class="workspace-name" :title="row.name">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="slug" :label="t('workspaceManage.colSlug')" min-width="140" />
        <el-table-column prop="description" :label="t('workspaceManage.colDescription')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="memberRole" :label="t('workspaceManage.colMyRole')" width="120">
          <template #default="{ row }">
            <span class="role-tag" :class="row.effectiveRole || row.memberRole">
              {{ row.effectiveRole || row.memberRole || '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('workspaceManage.colCreateTime')" width="170" />
        <el-table-column :label="t('common.action')" width="160" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <button class="action-link" :disabled="!canEdit(row)" @click="openEditModal(row)">
                {{ t('common.edit') }}
              </button>
              <button class="action-link danger" :disabled="!canDelete(row)" @click="handleDelete(row)">
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
      :title="isEdit ? t('workspaceManage.editTitle') : t('workspaceManage.createTitle')"
      width="520px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <div class="form-body">
        <div class="form-group">
          <label class="form-label">{{ t('workspaceManage.fieldName') }} *</label>
          <el-input v-model="form.name" :placeholder="t('workspaceManage.namePlaceholder')" />
        </div>
        <div class="form-group">
          <label class="form-label">{{ t('workspaceManage.fieldSlug') }}</label>
          <el-input v-model="form.slug" :placeholder="t('workspaceManage.slugPlaceholder')" :disabled="isEdit" />
        </div>
        <div class="form-group">
          <label class="form-label">{{ t('workspaceManage.fieldDescription') }}</label>
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            :placeholder="t('workspaceManage.descriptionPlaceholder')"
          />
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
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/useUserStore'
import * as workspaceApi from '@/api/workspace'
import type { Workspace } from '@/types'

const { t } = useI18n()
const userStore = useUserStore()

const loading = ref(false)
const workspaces = ref<Workspace[]>([])
const showModal = ref(false)
const submitting = ref(false)
const isEdit = ref(false)
const editingId = ref<number | string | null>(null)

const form = reactive({
  name: '',
  slug: '',
  description: '',
})

onMounted(() => {
  loadWorkspaces()
})

async function loadWorkspaces(): Promise<void> {
  loading.value = true
  try {
    workspaces.value = await workspaceApi.listWorkspaces()
  } catch {
    // 错误已由 axios 拦截器提示
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  form.name = ''
  form.slug = ''
  form.description = ''
  editingId.value = null
  isEdit.value = false
}

function openCreateModal(): void {
  resetForm()
  showModal.value = true
}

function openEditModal(row: Workspace): void {
  resetForm()
  isEdit.value = true
  editingId.value = row.id
  form.name = row.name || ''
  form.slug = row.slug || ''
  form.description = row.description || ''
  showModal.value = true
}

function canEdit(row: Workspace): boolean {
  return row.effectiveRole === 'owner' || row.effectiveRole === 'admin' || userStore.isAdmin
}

function canDelete(row: Workspace): boolean {
  return row.effectiveRole === 'owner' || userStore.isAdmin
}

async function handleSubmit(): Promise<void> {
  const name = form.name.trim()
  if (!name) {
    ElMessage.warning(t('workspaceManage.nameRequired'))
    return
  }

  submitting.value = true
  try {
    const payload: Partial<Workspace> = {
      name,
      slug: form.slug.trim() || undefined,
      description: form.description.trim() || undefined,
    }
    if (isEdit.value && editingId.value) {
      await workspaceApi.updateWorkspace(editingId.value, payload)
      ElMessage.success(t('workspaceManage.updateSuccess'))
    } else {
      await workspaceApi.createWorkspace(payload)
      ElMessage.success(t('workspaceManage.createSuccess'))
    }
    showModal.value = false
    await loadWorkspaces()
  } catch {
    // 错误已由 axios 拦截器提示
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: Workspace): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('workspaceManage.deleteConfirm', { name: row.name }),
      t('common.confirm'),
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await workspaceApi.deleteWorkspace(row.id)
    ElMessage.success(t('workspaceManage.deleteSuccess'))
    await loadWorkspaces()
  } catch {
    // 错误已由 axios 拦截器提示
  }
}
</script>

<style scoped>
.workspace-manage-page {
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

.workspace-table {
  width: 100%;
}

.workspace-name {
  font-weight: 600;
  color: var(--theme-text);
}

.role-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
  text-transform: capitalize;
  background: var(--theme-surface-hover);
  color: var(--theme-text-secondary);
}

.role-tag.owner {
  background: rgba(65, 118, 230, 0.12);
  color: var(--main-orange);
}

.role-tag.admin {
  background: rgba(65, 118, 230, 0.12);
  color: var(--main-orange);
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
