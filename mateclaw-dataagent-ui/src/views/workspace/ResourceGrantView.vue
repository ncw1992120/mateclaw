<template>
  <div class="grant-page">
    <div class="page-header">
      <div class="page-header-left">
        <h1 class="page-title">资源授权管理</h1>
        <p class="page-desc">将数据源、知识库等私有资源授权给其他成员使用</p>
      </div>
      <div class="page-header-actions">
        <button class="btn-create-pill" @click="openGrantDialog">
          <el-icon :size="14"><Plus /></el-icon>
          新增授权
        </button>
      </div>
    </div>

    <!-- 授权记录表格 -->
    <div class="page-body">
      <div class="grant-toolbar">
        <el-select v-model="filterResourceType" placeholder="按资源类型筛选" clearable style="width: 160px" @change="loadGrants">
          <el-option label="数据源" value="datasource" />
          <el-option label="知识库" value="knowledge" />
          <el-option label="技能" value="skill" />
          <el-option label="智能体" value="agent" />
          <el-option label="词典" value="business_term" />
        </el-select>
        <span class="grant-count">共 {{ filteredGrants.length }} 条</span>
      </div>

      <el-table :data="filteredGrants" v-loading="loading" class="mc-table">
        <el-table-column prop="resourceType" label="资源类型" width="110">
          <template #default="{ row }">
            <span class="mc-tag" :class="row.resourceType">{{ resourceTypeLabel(row.resourceType) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="资源名称" min-width="160">
          <template #default="{ row }">
            {{ resolveResourceName(row.resourceType, row.resourceId) }}
          </template>
        </el-table-column>
        <el-table-column label="授权对象" width="180">
          <template #default="{ row }">
            <span class="mc-tag" :class="row.grantType === 'role' ? 'agent' : 'text'">{{ grantTypeLabel(row.grantType) }}</span>
            <span class="grantee-name">{{ resolveGranteeName(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="授权人" width="120">
          <template #default="{ row }">
            {{ resolveGrantedByName(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="permission" label="权限" width="100">
          <template #default="{ row }">
            <span class="mc-tag" :class="permissionClass(row.permission)">{{ permissionLabel(row.permission) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="mc-tag" :class="row.status === 1 ? 'delivered' : 'revoked'">{{ row.status === 1 ? '生效' : '已撤销' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="过期时间" width="150">
          <template #default="{ row }">
            {{ row.expireTime ? formatDateTime(row.expireTime) : '永久' }}
          </template>
        </el-table-column>
        <el-table-column label="授权时间" width="150">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-tooltip content="编辑" placement="top" :disabled="row.status !== 1">
                <el-icon :size="14" class="action-icon" :class="{ 'is-disabled': row.status !== 1 }" @click="row.status === 1 && openEditDialog(row)">
                  <Edit />
                </el-icon>
              </el-tooltip>
              <el-tooltip content="撤销" placement="top" :disabled="row.status !== 1">
                <el-icon :size="14" class="action-icon danger" :class="{ 'is-disabled': row.status !== 1 }" @click="row.status === 1 && handleRevoke(row)">
                  <CircleClose />
                </el-icon>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增授权对话框 -->
    <el-dialog v-model="grantDialogVisible" title="新增授权" width="520px" :close-on-click-modal="false">
      <el-form :model="grantForm" label-width="80px" size="small">
        <el-form-item label="资源类型">
          <el-select v-model="grantForm.resourceType" placeholder="选择资源类型" style="width: 100%" @change="handleResourceTypeChange">
            <el-option label="数据源" value="datasource" />
            <el-option label="知识库" value="knowledge" />
            <el-option label="技能" value="skill" />
            <el-option label="智能体" value="agent" />
            <el-option label="词典" value="business_term" />
          </el-select>
        </el-form-item>
        <el-form-item label="资源">
          <el-select
            v-model="grantForm.resourceId"
            placeholder="选择资源"
            filterable
            style="width: 100%"
            :loading="resourcesLoading"
          >
            <el-option
              v-for="r in currentResources"
              :key="r.id"
              :label="r.name"
              :value="String(r.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="授权方式">
          <el-select v-model="grantForm.grantType" placeholder="选择授权方式" style="width: 100%">
            <el-option label="按用户" value="user" />
            <el-option label="按角色" value="role" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="grantForm.grantType === 'user'" label="授权用户">
          <el-select v-model="grantForm.granteeId" placeholder="选择用户" filterable style="width: 100%">
            <el-option
              v-for="member in workspaceMembers"
              :key="member.userId"
              :label="`${member.nickname || member.username}（${member.role}）`"
              :value="String(member.userId)"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="grantForm.grantType === 'role'" label="授权角色">
          <el-select v-model="grantForm.granteeId" placeholder="选择角色" style="width: 100%">
            <el-option label="admin" value="admin" />
            <el-option label="member" value="member" />
            <el-option label="viewer" value="viewer" />
          </el-select>
        </el-form-item>
        <el-form-item label="权限">
          <el-select v-model="grantForm.permission" placeholder="选择权限" style="width: 100%">
            <el-option label="查看" value="view" />
            <el-option label="使用" value="use" />
            <el-option label="编辑" value="edit" />
          </el-select>
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker
            v-model="grantForm.expireTime"
            type="datetime"
            placeholder="留空表示永久"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="grantDialogVisible = false">取消</el-button>
        <el-button size="small" type="primary" :loading="grantSubmitting" @click="handleGrant">确认授权</el-button>
      </template>
    </el-dialog>

    <!-- 编辑授权对话框 -->
    <el-dialog v-model="editDialogVisible" title="编辑授权" width="480px" :close-on-click-modal="false">
      <el-form :model="editForm" label-width="80px" size="small">
        <el-form-item label="资源">
          <span>{{ editForm.resourceLabel }}</span>
        </el-form-item>
        <el-form-item label="授权对象">
          <span>{{ editForm.granteeLabel }}</span>
        </el-form-item>
        <el-form-item label="权限">
          <el-select v-model="editForm.permission" placeholder="选择权限" style="width: 100%">
            <el-option label="查看" value="view" />
            <el-option label="使用" value="use" />
            <el-option label="编辑" value="edit" />
          </el-select>
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker
            v-model="editForm.expireTime"
            type="datetime"
            placeholder="留空表示永久"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="editDialogVisible = false">取消</el-button>
        <el-button size="small" type="primary" :loading="editSubmitting" @click="handleEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, CircleClose } from '@element-plus/icons-vue'
import { formatDateTime } from '@/utils/time'
import { useUserStore } from '@/stores/useUserStore'
import { useDatasourceStore } from '@/stores/useDatasourceStore'
import { useAgentStore } from '@/stores/useAgentStore'
import * as grantApi from '@/api/resource-grant'
import * as knowledgeApi from '@/api/knowledge'
import * as skillApi from '@/api/skill'
import * as businessTermApi from '@/api/business-term'
import { listWorkspaceMembers } from '@/api/workspace'
import type { WorkspaceMember } from '@/types'

const userStore = useUserStore()
const datasourceStore = useDatasourceStore()
const agentStore = useAgentStore()

/** 统一资源选项（id + name） */
interface ResourceOption {
  id: string | number
  name: string
}

/** 授权记录列表 */
const grants = ref<grantApi.ResourceGrant[]>([])
const loading = ref(false)

/** 筛选条件 */
const filterResourceType = ref<string>('')

/** 工作区成员列表 */
const workspaceMembers = ref<WorkspaceMember[]>([])

/** 当前选中资源类型对应的资源列表 */
const currentResources = ref<ResourceOption[]>([])
const resourcesLoading = ref(false)

/** 新增授权对话框 */
const grantDialogVisible = ref(false)
const grantSubmitting = ref(false)
const grantForm = ref({
  resourceType: 'datasource',
  resourceId: '',
  grantType: 'user',
  granteeId: '',
  permission: 'use',
  expireTime: null as string | null,
})

/** 缓存各资源类型的资源列表 */
const resourceCache = ref<Record<string, ResourceOption[]>>({})

/** 编辑授权对话框 */
const editDialogVisible = ref(false)
const editSubmitting = ref(false)
const editForm = ref({
  id: '',
  resourceLabel: '',
  granteeLabel: '',
  permission: 'use',
  expireTime: null as string | null,
})

/** 前端筛选后的授权记录 */
const filteredGrants = computed(() => {
  if (!filterResourceType.value) return grants.value
  return grants.value.filter((g) => g.resourceType === filterResourceType.value)
})

/** 资源类型中文映射 */
function resourceTypeLabel(type: string): string {
  const map: Record<string, string> = {
    datasource: '数据源',
    knowledge: '知识库',
    skill: '技能',
    agent: '智能体',
    business_term: '业务词典',
  }
  return map[type] || type
}

/** 授权方式中文映射 */
function grantTypeLabel(type: string): string {
  const map: Record<string, string> = { user: '用户', role: '角色', group: '用户组' }
  return map[type] || type
}

/** 权限中文映射 */
function permissionLabel(perm: string): string {
  const map: Record<string, string> = { view: '查看', use: '使用', edit: '编辑' }
  return map[perm] || perm
}

/** 权限胶囊样式映射：查看蓝 / 使用绿 / 编辑琥珀 */
function permissionClass(perm: string): string {
  const map: Record<string, string> = { view: 'text', use: 'delivered', edit: 'pending' }
  return map[perm] || ''
}

/** 根据资源类型和 ID 解析资源名称 */
function resolveResourceName(resourceType: string, resourceId: string): string {
  const list = resourceCache.value[resourceType] || []
  const found = list.find((r) => String(r.id) === String(resourceId))
  return found ? found.name : `${resourceType}#${resourceId}`
}

/** 解析被授权者名称 */
function resolveGranteeName(row: grantApi.ResourceGrant): string {
  if (row.grantType === 'user') {
    const member = workspaceMembers.value.find((m) => String(m.userId) === row.granteeId)
    return member ? (member.nickname || member.username) : row.granteeId
  }
  return row.granteeId
}

/** 解析授权人名称 */
function resolveGrantedByName(row: grantApi.ResourceGrant): string {
  if (!row.grantedBy) return '-'
  const member = workspaceMembers.value.find((m) => String(m.userId) === String(row.grantedBy))
  return member ? (member.nickname || member.username) : String(row.grantedBy)
}

/** 加载指定资源类型的资源列表 */
async function loadResourcesByType(type: string): Promise<void> {
  if (resourceCache.value[type]?.length) {
    currentResources.value = resourceCache.value[type]
    return
  }
  resourcesLoading.value = true
  try {
    let list: ResourceOption[] = []
    const wsId = userStore.currentWorkspaceId ?? 1

    switch (type) {
      case 'datasource': {
        // 优先用 store 缓存
        if (datasourceStore.datasources.length === 0) {
          await datasourceStore.fetchDatasources()
        }
        list = datasourceStore.datasources.map((d: any) => ({ id: d.id, name: d.name }))
        break
      }
      case 'agent': {
        if (agentStore.agents.length === 0) {
          await agentStore.fetchAgents(Number(wsId))
        }
        list = agentStore.agents.map((a: any) => ({ id: a.id, name: a.name }))
        break
      }
      case 'knowledge': {
        const data = await knowledgeApi.listKBs(Number(wsId))
        const items = Array.isArray(data) ? data : []
        list = items.map((kb: any) => ({ id: kb.id, name: kb.name }))
        break
      }
      case 'skill': {
        const data = await skillApi.list(Number(wsId))
        const items = Array.isArray(data) ? data : []
        list = items.map((s: any) => ({ id: s.id, name: s.nameZh || s.name || s.slug }))
        break
      }
      case 'business_term': {
        const codes = await businessTermApi.listTenantCodes()
        const items = (codes || []) as unknown as string[]
        list = items.map((code: string) => ({ id: code, name: code }))
        break
      }
    }

    resourceCache.value[type] = list
    currentResources.value = list
  } catch {
    currentResources.value = []
  } finally {
    resourcesLoading.value = false
  }
}

/** 资源类型切换时加载对应资源列表 */
function handleResourceTypeChange(type: string): void {
  grantForm.value.resourceId = ''
  loadResourcesByType(type)
}

/** 加载授权记录（工作区维度） */
async function loadGrants(): Promise<void> {
  loading.value = true
  try {
    const data = await grantApi.listGrantsByWorkspace(filterResourceType.value || undefined, undefined)
    grants.value = Array.isArray(data) ? data : []
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '加载授权记录失败')
  } finally {
    loading.value = false
  }
}

/** 加载工作区成员 */
async function loadMembers(): Promise<void> {
  const wsId = userStore.currentWorkspaceId
  if (!wsId) return
  try {
    const data = await listWorkspaceMembers(wsId)
    workspaceMembers.value = Array.isArray(data) ? data : []
  } catch {
    // 静默失败
  }
}

/** 打开新增授权对话框 */
function openGrantDialog(): void {
  grantForm.value = {
    resourceType: 'datasource',
    resourceId: '',
    grantType: 'user',
    granteeId: '',
    permission: 'use',
    expireTime: null,
  }
  grantDialogVisible.value = true
  // 默认加载第一个资源类型的列表
  loadResourcesByType('datasource')
}

/** 提交授权 */
async function handleGrant(): Promise<void> {
  const form = grantForm.value
  if (!form.resourceType || !form.resourceId || !form.grantType || !form.granteeId || !form.permission) {
    ElMessage.warning('请填写完整的授权信息')
    return
  }
  grantSubmitting.value = true
  try {
    await grantApi.grantResource({
      resourceType: form.resourceType,
      resourceId: form.resourceId,
      grantType: form.grantType,
      granteeId: form.granteeId,
      permission: form.permission,
      expireTime: form.expireTime || undefined,
    })
    ElMessage.success('授权成功')
    grantDialogVisible.value = false
    await loadGrants()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '授权失败')
  } finally {
    grantSubmitting.value = false
  }
}

/** 撤销授权 */
async function handleRevoke(row: grantApi.ResourceGrant): Promise<void> {
  try {
    await ElMessageBox.confirm('确认撤销该授权？撤销后被授权者将无法继续使用该资源。', '撤销授权', { type: 'warning' })
  } catch {
    return
  }
  try {
    await grantApi.revokeGrant(row.id)
    ElMessage.success('已撤销')
    await loadGrants()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '撤销失败')
  }
}

/** 打开编辑授权对话框 */
function openEditDialog(row: grantApi.ResourceGrant): void {
  editForm.value = {
    id: row.id,
    resourceLabel: `${resourceTypeLabel(row.resourceType)} - ${resolveResourceName(row.resourceType, row.resourceId)}`,
    granteeLabel: `${grantTypeLabel(row.grantType)} - ${resolveGranteeName(row)}`,
    permission: row.permission,
    expireTime: row.expireTime || null,
  }
  editDialogVisible.value = true
}

/** 提交编辑 */
async function handleEdit(): Promise<void> {
  if (!editForm.value.permission) {
    ElMessage.warning('请选择权限')
    return
  }
  editSubmitting.value = true
  try {
    await grantApi.updateGrant(editForm.value.id, {
      permission: editForm.value.permission,
      expireTime: editForm.value.expireTime || null,
    })
    ElMessage.success('更新成功')
    editDialogVisible.value = false
    await loadGrants()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '更新失败')
  } finally {
    editSubmitting.value = false
  }
}

onMounted(() => {
  loadGrants()
  loadMembers()
  // 预加载各资源类型的列表（用于表格中解析资源名称）
  loadResourcesByType('datasource')
  loadResourcesByType('agent')
  loadResourcesByType('knowledge')
  loadResourcesByType('skill')
  loadResourcesByType('business_term')
})
</script>

<style scoped>
.grant-page {
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
  min-height: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.grant-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.grant-count {
  font-size: 12.5px;
  color: var(--db-text-muted, var(--theme-text-muted));
}

.grantee-name {
  margin-left: 6px;
  color: var(--db-text, var(--theme-text));
}

/* row-actions + action-icon：与工作区管理页同约定 */
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
</style>
