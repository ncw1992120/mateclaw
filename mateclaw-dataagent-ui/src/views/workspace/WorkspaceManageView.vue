<template>
  <div class="workspace-manage-page">
    <div class="page-header">
      <div class="page-header-left">
        <h1 class="page-title">{{ t('workspaceManage.title') }}</h1>
        <p class="page-desc">{{ t('workspaceManage.desc') }}</p>
      </div>
      <div class="page-header-actions">
        <button class="btn-create-pill" @click="openCreateModal">
          <el-icon :size="14"><Plus /></el-icon>
          {{ t('workspaceManage.create') }}
        </button>
      </div>
    </div>

    <div class="page-body surface-card">
      <el-table v-loading="loading" :data="workspaces" class="mc-table">
        <el-table-column prop="name" :label="t('workspaceManage.colName')" min-width="160">
          <template #default="{ row }">
            <span class="cell-name">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="slug" :label="t('workspaceManage.colSlug')" min-width="140" />
        <el-table-column prop="description" :label="t('workspaceManage.colDescription')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="memberRole" :label="t('workspaceManage.colMyRole')" width="120">
          <template #default="{ row }">
            <span class="mc-tag" :class="row.effectiveRole || row.memberRole">
              {{ row.effectiveRole || row.memberRole || '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('workspaceManage.colCreateTime')" width="170" />
        <el-table-column :label="t('common.action')" width="80" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-tooltip :content="t('common.edit')" placement="top" :disabled="!canEdit(row)">
                <el-icon :size="14" class="action-icon" :class="{ 'is-disabled': !canEdit(row) }" @click="openEditModal(row)">
                  <Edit />
                </el-icon>
              </el-tooltip>
              <el-tooltip :content="t('common.delete')" placement="top" :disabled="!canDelete(row)">
                <el-icon :size="14" class="action-icon danger" :class="{ 'is-disabled': !canDelete(row) }" @click="handleDelete(row)">
                  <Delete />
                </el-icon>
              </el-tooltip>
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
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
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
  overflow: auto;
  border-radius: 12px;
  padding: 6px 12px 12px;
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

.action-icon.is-disabled {
  opacity: 0.3;
  cursor: not-allowed;
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
</style>
