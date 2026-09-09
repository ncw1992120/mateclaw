<template>
  <div class="member-manage-page">
    <div class="page-header">
      <div class="page-header-left">
        <h1 class="page-title">{{ t('memberManage.title') }}</h1>
        <p class="page-desc">{{ t('memberManage.desc') }}</p>
      </div>
      <div v-if="canManage" class="page-header-actions">
        <button class="btn-create-pill" @click="openAddModal">
          <el-icon :size="14"><Plus /></el-icon>
          {{ t('memberManage.addMember') }}
        </button>
      </div>
    </div>

    <div class="page-body surface-card">
      <el-table v-loading="loading" :data="members" class="mc-table">
        <el-table-column prop="username" :label="t('memberManage.colUsername')" min-width="140" />
        <el-table-column prop="nickname" :label="t('memberManage.colNickname')" min-width="140" />
        <el-table-column prop="role" :label="t('memberManage.colRole')" width="120">
          <template #default="{ row }">
            <span class="mc-tag" :class="row.role">{{ row.role }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('memberManage.colJoinTime')" width="170" />
        <el-table-column v-if="canManage" :label="t('common.action')" width="80" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-dropdown trigger="click" size="small" @command="(role: string) => handleChangeRole(row, role)">
                <el-icon :size="14" class="action-icon" :class="{ 'is-disabled': row.role === 'owner' }" @click="handleChangeRole(row, 'admin')">
                  <Edit />
                </el-icon>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="admin">admin</el-dropdown-item>
                    <el-dropdown-item command="member">member</el-dropdown-item>
                    <el-dropdown-item command="viewer">viewer</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-icon :size="14" class="action-icon danger" :class="{ 'is-disabled': row.role === 'owner' }" @click="handleRemove(row)">
                <Delete />
              </el-icon>
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
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
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
  gap: 16px;
}

.page-header-left {
  min-width: 0;
  flex: 1;
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
