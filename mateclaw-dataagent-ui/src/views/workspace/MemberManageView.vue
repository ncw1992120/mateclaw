<template>
  <div class="member-manage-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">{{ t('memberManage.title') }}</h1>
        <p class="page-desc">{{ t('memberManage.desc') }}</p>
      </div>
      <button v-if="canManage" class="btn-primary" @click="openAddModal">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="12" y1="5" x2="12" y2="19" />
          <line x1="5" y1="12" x2="19" y2="12" />
        </svg>
        {{ t('memberManage.addMember') }}
      </button>
    </div>

    <div class="page-body surface-card">
      <el-table v-loading="loading" :data="members" stripe class="member-table">
        <el-table-column prop="username" :label="t('memberManage.colUsername')" min-width="140" />
        <el-table-column prop="nickname" :label="t('memberManage.colNickname')" min-width="140" />
        <el-table-column prop="role" :label="t('memberManage.colRole')" width="120">
          <template #default="{ row }">
            <span class="role-tag" :class="row.role">{{ row.role }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('memberManage.colJoinTime')" width="170" />
        <el-table-column v-if="canManage" :label="t('common.action')" width="160" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-dropdown trigger="click" size="small" @command="(role: string) => handleChangeRole(row, role)">
                <button class="action-link" :disabled="row.role === 'owner'">
                  {{ t('memberManage.changeRole') }}
                </button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="admin">admin</el-dropdown-item>
                    <el-dropdown-item command="member">member</el-dropdown-item>
                    <el-dropdown-item command="viewer">viewer</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <button class="action-link danger" :disabled="row.role === 'owner'" @click="handleRemove(row)">
                {{ t('memberManage.remove') }}
              </button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 添加成员弹窗 -->
    <el-dialog
      v-model="showModal"
      :title="t('memberManage.addTitle')"
      width="520px"
      destroy-on-close
      :close-on-click-modal="false"
    >
      <div class="form-body">
        <div class="form-group">
          <label class="form-label">{{ t('memberManage.fieldUsername') }} *</label>
          <el-input v-model="form.username" :placeholder="t('memberManage.usernamePlaceholder')" />
        </div>
        <div class="form-group">
          <label class="form-label">{{ t('memberManage.fieldNickname') }}</label>
          <el-input v-model="form.nickname" :placeholder="t('memberManage.nicknamePlaceholder')" />
        </div>
        <div class="form-group">
          <label class="form-label">{{ t('memberManage.fieldPassword') }}</label>
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="t('memberManage.passwordPlaceholder')"
          />
          <span class="form-hint">{{ t('memberManage.passwordHint') }}</span>
        </div>
        <div class="form-group">
          <label class="form-label">{{ t('memberManage.fieldRole') }}</label>
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="admin" value="admin" />
            <el-option label="member" value="member" />
            <el-option label="viewer" value="viewer" />
          </el-select>
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="showModal = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            {{ t('common.create') }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/useUserStore'
import * as workspaceApi from '@/api/workspace'
import type { WorkspaceMember } from '@/types'

const { t } = useI18n()
const userStore = useUserStore()

const loading = ref(false)
const members = ref<WorkspaceMember[]>([])
const showModal = ref(false)
const submitting = ref(false)

const form = reactive({
  username: '',
  nickname: '',
  password: '',
  role: 'member',
})

const canManage = computed(() => {
  const ws = userStore.currentWorkspace
  return ws && (ws.effectiveRole === 'owner' || ws.effectiveRole === 'admin' || userStore.isAdmin)
})

onMounted(() => {
  loadMembers()
})

async function loadMembers(): Promise<void> {
  const workspaceId = userStore.currentWorkspaceId
  if (!workspaceId) {
    return
  }
  loading.value = true
  try {
    members.value = await workspaceApi.listWorkspaceMembers(workspaceId)
  } catch {
    // 错误已由 axios 拦截器提示
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  form.username = ''
  form.nickname = ''
  form.password = ''
  form.role = 'member'
}

function openAddModal(): void {
  resetForm()
  showModal.value = true
}

async function handleSubmit(): Promise<void> {
  const username = form.username.trim()
  if (!username) {
    ElMessage.warning(t('memberManage.usernameRequired'))
    return
  }

  submitting.value = true
  try {
    const workspaceId = userStore.currentWorkspaceId
    if (!workspaceId) {
      return
    }
    await workspaceApi.addWorkspaceMember(workspaceId, {
      username,
      nickname: form.nickname.trim() || undefined,
      password: form.password || undefined,
      role: form.role,
    })
    ElMessage.success(t('memberManage.addSuccess'))
    showModal.value = false
    await loadMembers()
  } catch {
    // 错误已由 axios 拦截器提示
  } finally {
    submitting.value = false
  }
}

async function handleChangeRole(row: WorkspaceMember, role: string): Promise<void> {
  if (row.role === role) {
    return
  }
  const workspaceId = userStore.currentWorkspaceId
  if (!workspaceId) {
    return
  }
  try {
    await workspaceApi.updateWorkspaceMemberRole(workspaceId, row.userId, role)
    ElMessage.success(t('memberManage.updateRoleSuccess'))
    await loadMembers()
  } catch {
    // 错误已由 axios 拦截器提示
  }
}

async function handleRemove(row: WorkspaceMember): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('memberManage.removeConfirm', { name: row.username }),
      t('common.confirm'),
      { type: 'warning' },
    )
  } catch {
    return
  }
  const workspaceId = userStore.currentWorkspaceId
  if (!workspaceId) {
    return
  }
  try {
    await workspaceApi.removeWorkspaceMember(workspaceId, row.userId)
    ElMessage.success(t('memberManage.removeSuccess'))
    await loadMembers()
  } catch {
    // 错误已由 axios 拦截器提示
  }
}
</script>

<style scoped>
.member-manage-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 24px;
  gap: 16px;
  box-sizing: border-box;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-shrink: 0;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--theme-text);
  margin: 0;
}

.page-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--theme-text-secondary);
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

.member-table {
  width: 100%;
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

.form-hint {
  font-size: 12px;
  color: var(--theme-text-muted);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
