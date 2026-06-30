<template>
  <div class="grant-page">
    <header class="grant-header">
      <h2 class="page-title">资源授权管理</h2>
      <p class="page-desc">将数据源、知识库等私有资源授权给其他成员使用</p>
    </header>

    <!-- 授权记录表格 -->
    <div class="grant-table-wrapper">
      <div class="table-toolbar">
        <el-select v-model="filterResourceType" placeholder="资源类型" clearable size="small" style="width: 140px" @change="loadGrants">
          <el-option label="数据源" value="datasource" />
          <el-option label="知识库" value="knowledge" />
          <el-option label="技能" value="skill" />
          <el-option label="智能体" value="agent" />
          <el-option label="语义模型" value="semantic_model" />
        </el-select>
        <el-button type="primary" size="small" @click="openGrantDialog">
          新增授权
        </el-button>
      </div>

      <el-table :data="filteredGrants" v-loading="loading" stripe size="small" style="width: 100%">
        <el-table-column prop="resourceType" label="资源类型" width="120">
          <template #default="{ row }">
            {{ resourceTypeLabel(row.resourceType) }}
          </template>
        </el-table-column>
        <el-table-column label="资源名称" min-width="160">
          <template #default="{ row }">
            {{ resolveResourceName(row.resourceType, row.resourceId) }}
          </template>
        </el-table-column>
        <el-table-column label="授权对象" width="180">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ grantTypeLabel(row.grantType) }}</el-tag>
            <span style="margin-left: 6px">{{ resolveGranteeName(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="授权人" width="120">
          <template #default="{ row }">
            {{ resolveGrantedByName(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="permission" label="权限" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.permission === 'edit' ? 'warning' : row.permission === 'use' ? 'success' : 'info'">
              {{ permissionLabel(row.permission) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '生效' : '已撤销' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="过期时间" width="170">
          <template #default="{ row }">
            {{ row.expireTime ? new Date(row.expireTime).toLocaleString() : '永久' }}
          </template>
        </el-table-column>
        <el-table-column label="授权时间" width="170">
          <template #default="{ row }">
            {{ new Date(row.createTime).toLocaleString() }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 1"
              size="small"
              link
              type="danger"
              @click="handleRevoke(row)"
            >
              撤销
            </el-button>
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
            <el-option label="语义模型" value="semantic_model" />
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
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/useUserStore'
import { useDatasourceStore } from '@/stores/useDatasourceStore'
import { useAgentStore } from '@/stores/useAgentStore'
import * as grantApi from '@/api/resource-grant'
import * as knowledgeApi from '@/api/knowledge'
import * as skillApi from '@/api/skill'
import * as semanticModelApi from '@/api/semantic-model'
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
    semantic_model: '语义模型',
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
      case 'semantic_model': {
        if (datasourceStore.datasources.length === 0) {
          await datasourceStore.fetchDatasources()
        }
        const dsId = datasourceStore.datasources[0]?.id
        if (!dsId) break
        const data = await semanticModelApi.list(String(dsId))
        const items = Array.isArray(data) ? data : []
        list = items.map((m: any) => ({ id: m.id, name: m.businessName || `${m.tableName}.${m.columnName}` }))
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

onMounted(() => {
  loadGrants()
  loadMembers()
  // 预加载各资源类型的列表（用于表格中解析资源名称）
  loadResourcesByType('datasource')
  loadResourcesByType('agent')
  loadResourcesByType('knowledge')
  loadResourcesByType('skill')
})
</script>

<style scoped>
.grant-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
  height: 100%;
  overflow: auto;
}

.grant-header {
  flex-shrink: 0;
}

.page-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  margin: 0 0 4px;
}

.page-desc {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 0;
}

.grant-table-wrapper {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.table-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
</style>
