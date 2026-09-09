<template>
  <div class="workspace-manage-page">
    <div class="page-header">
      <div class="page-header-left">
        <h1 class="page-title">{{ t('workspaceManage.title') }}</h1>
      </div>
      <div class="page-header-actions">
        <button class="btn-create-pill" @click="openCreateModal">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true">
            <line x1="12" y1="5" x2="12" y2="19" />
            <line x1="5" y1="12" x2="19" y2="12" />
          </svg>
          {{ t('workspaceManage.create') }}
        </button>
      </div>
    </div>

    <div class="page-body surface-card">
      <div class="table-grid-scroll">
        <table class="data-grid" v-loading="loading">
          <thead>
            <tr>
              <th>{{ t('workspaceManage.colName') }}</th>
              <th>{{ t('workspaceManage.colSlug') }}</th>
              <th>{{ t('workspaceManage.colDescription') }}</th>
              <th>{{ t('workspaceManage.colMyRole') }}</th>
              <th>{{ t('workspaceManage.colCreateTime') }}</th>
              <th>{{ t('common.action') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in workspaces" :key="row.id">
              <td><span class="cell-name cell-text" :title="row.name">{{ row.name }}</span></td>
              <td><span class="cell-text">{{ row.slug || '-' }}</span></td>
              <td><span class="cell-text" :title="row.description">{{ row.description || '-' }}</span></td>
              <td>
                <span class="role-tag" :class="row.effectiveRole || row.memberRole">
                  {{ row.effectiveRole || row.memberRole || '-' }}
                </span>
              </td>
              <td><span class="cell-text">{{ row.createTime || '-' }}</span></td>
              <td>
                <div class="row-actions">
                  <button v-if="canEdit(row)" class="icon-btn" :title="t('common.edit')" @click="openEditModal(row)">
                    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                    </svg>
                  </button>
                  <button v-if="canDelete(row)" class="icon-btn danger" :title="t('common.delete')" @click="handleDelete(row)">
                    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                      <polyline points="3 6 5 6 21 6"/>
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                      <line x1="10" y1="11" x2="10" y2="17"/>
                      <line x1="14" y1="11" x2="14" y2="17"/>
                    </svg>
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
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
  padding: 0 20px;
  gap: 16px;
  box-sizing: border-box;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  padding: 2px 2px 14px;
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
  padding: 16px;
}

.surface-card {
  background: transparent;
}

.table-grid-scroll {
  flex: 1;
  overflow-y: auto;
  overflow-x: auto;
  border: 1px solid var(--db-border, var(--theme-border));
  border-radius: 10px;
  background: var(--db-card, var(--theme-surface));
}

.data-grid {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.data-grid thead tr {
  background: var(--theme-bg, #fafafa);
}

.data-grid th {
  padding: 11px 12px;
  text-align: center;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--db-text-muted, var(--theme-text-muted));
  border-bottom: 1px solid var(--db-border, var(--theme-border));
  white-space: nowrap;
}

.data-grid td {
  padding: 10px 12px;
  font-size: 13px;
  color: var(--db-text-secondary, var(--theme-text-secondary));
  border-bottom: 1px solid var(--db-border, var(--theme-border));
  vertical-align: middle;
  text-align: center;
}

.data-grid tbody tr:last-child td {
  border-bottom: none;
}

.data-grid tbody tr:hover {
  background: var(--db-hover, var(--theme-surface-hover));
}

.cell-text {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.cell-name {
  color: var(--db-text, var(--theme-text));
  font-weight: 500;
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
  justify-content: center;
  gap: 2px;
}

.icon-btn {
  background: none;
  border: none;
  cursor: pointer;
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: var(--db-text-secondary, var(--theme-text-secondary));
  opacity: 0.65;
  padding: 0;
  transition: background-color 120ms ease, color 120ms ease, opacity 120ms ease;
  line-height: 1;
}

.icon-btn:hover {
  opacity: 1;
  background: var(--db-hover, var(--theme-surface-hover));
  color: var(--db-text, var(--theme-text));
}

.icon-btn.danger:hover {
  background: rgba(245, 63, 63, 0.1);
  color: #f53f3f;
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
