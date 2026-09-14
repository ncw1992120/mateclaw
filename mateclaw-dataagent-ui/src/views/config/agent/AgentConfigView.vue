<template>
  <div class="agents-page">
    <!-- 固定头部：身份页头（标题+启停统计+描述） + 主操作，与技能页同一版式语言 -->
    <div class="agents-fixed">
      <div class="agent-content-header">
        <div class="agent-content-title">
          <div class="agent-content-name-row">
            <h3 class="agent-content-name">{{ t('configCenter.tabAgent') }}</h3>
            <!-- 启停数量标识：常驻标题旁，色彩编码与卡片状态 chip 一致 -->
            <span v-if="statsReady" class="agent-title-stats">
              <span class="summary-item">
                <span class="summary-dot on" aria-hidden="true"></span>
                {{ t('skillManage.sectionEnabled') }}
                <b class="summary-num">{{ statusCounts.enabled }}</b>
              </span>
              <span class="summary-item off">
                <span class="summary-dot" aria-hidden="true"></span>
                {{ t('skillManage.sectionAvailable') }}
                <b class="summary-num">{{ statusCounts.disabled }}</b>
              </span>
            </span>
          </div>
          <p class="agent-content-desc">{{ t('configCenter.agentDesc') }}</p>
        </div>
        <div class="header-actions">
          <el-button v-if="canManage" type="primary" :icon="Plus" @click="openCreateDialog">
            {{ t('configCenter.agentNew') }}
          </el-button>
        </div>
      </div>
    </div>

    <!-- 可滚动列表区 -->
    <div class="agents-scroll">
      <div v-if="loading && agentStore.agents.length === 0" class="page-loading">
        <span>{{ t('common.loading') || '加载中…' }}</span>
      </div>

      <!-- 全局空态：线型 SVG chip + 文案 + 新建按钮 -->
      <div v-else-if="agentStore.agents.length === 0" class="global-empty surface-card">
        <div class="global-empty-icon">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <rect x="4" y="8" width="16" height="12" rx="2" />
            <circle cx="9" cy="14" r="1" />
            <circle cx="15" cy="14" r="1" />
            <path d="M12 8V4" />
            <circle cx="12" cy="3" r="1" />
          </svg>
        </div>
        <h3>{{ t('configCenter.agentEmpty') }}</h3>
        <el-button v-if="canManage" type="primary" size="small" @click="openCreateDialog">
          {{ t('configCenter.agentNew') }}
        </el-button>
      </div>

      <div v-else class="agent-grid">
        <div
          v-for="agent in agentStore.agents"
          :key="agent.id"
          class="agent-card surface-card"
          :class="{ disabled: !agent.enabled }"
        >
          <div class="agent-header">
            <div class="agent-icon-wrap">
              <PiIcon v-if="agent.icon?.startsWith('pi:')" :name="agent.icon" :size="20" />
              <span v-else class="agent-icon">{{ agent.icon || '🤖' }}</span>
            </div>
            <div class="agent-meta">
              <h3 class="agent-name" :title="resolveAgentName(agent)">{{ resolveAgentName(agent) }}</h3>
              <div v-if="agent.agentType" class="agent-slug">{{ agent.agentType }}</div>
            </div>
            <label v-if="canManage" class="toggle-switch" :title="t('skillManage.toggleTitle')">
              <input
                type="checkbox"
                :checked="!!agent.enabled"
                @change="(e) => handleToggle(agent, (e.target as HTMLInputElement).checked)"
              />
              <span class="toggle-slider"></span>
            </label>
          </div>

          <p class="agent-desc">{{ agent.description || t('skillManage.noDescription') }}</p>

          <div v-if="parseTags(agent.tags).length > 0" class="agent-tags">
            <span v-for="tag in parseTags(agent.tags)" :key="tag" class="agent-tag">
              {{ tag }}
            </span>
          </div>

          <!-- 底栏：状态 chip 领衔 + 元信息合并行（模式·模型） + 右侧轻量操作按钮 -->
          <div class="agent-footer">
            <span class="agent-status" :class="{ off: !agent.enabled }">
              <span class="agent-status-dot" aria-hidden="true"></span>
              {{ agent.enabled ? t('skillManage.sectionEnabled') : t('skillManage.sectionAvailable') }}
            </span>
            <span v-if="metaLabel(agent)" class="agent-meta-line" :title="metaLabel(agent)">{{ metaLabel(agent) }}</span>
            <div class="agent-actions">
              <button v-if="!canManage" type="button" class="agent-action-btn" :title="t('configCenter.agentViewDesc')" @click="handleView(agent)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                  <circle cx="12" cy="12" r="3" />
                </svg>
                {{ t('configCenter.agentView') }}
              </button>
              <template v-if="canManage">
                <button type="button" class="agent-action-btn" @click="handleEdit(agent)">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
                  </svg>
                  {{ t('configCenter.agentEdit') }}
                </button>
                <button type="button" class="agent-action-btn action-delete" @click="handleDelete(agent)">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M3 6h18" />
                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                  </svg>
                  {{ t('configCenter.agentDelete') }}
                </button>
              </template>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 查看 / 编辑 / 新建弹窗 -->
    <AgentFormDialog
      v-model:visible="dialogVisible"
      :edit-id="editingId"
      :readonly="dialogReadonly"
      @saved="handleSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useAgentStore } from '@/stores/useAgentStore'
import { usePermission, PERMISSION } from '@/composables/usePermission'
import AgentFormDialog from '@/views/dialog/AgentFormDialog.vue'
import PiIcon from '@/components/PiIcon.vue'
import type { Agent } from '@/types'

const { t } = useI18n()
const agentStore = useAgentStore()
const { hasPermission } = usePermission()

/** 是否可管理智能体（管理员/工作区 admin+owner，全局管理员自动放行），否则只读 */
const canManage = computed(() => hasPermission(PERMISSION.AGENT_MANAGE))

/** 当前工作区 ID：优先取已有 agent 的 workspaceId，否则用 1 */
const currentWorkspaceId = computed(() => {
  return agentStore.currentAgent?.workspaceId
    ?? agentStore.agents[0]?.workspaceId
    ?? 1
})

const loading = ref(false)
/** 首次加载完成后才显示标题旁启停统计，避免首帧 0/0 闪烁 */
const statsReady = ref(false)

/** 启停统计：与卡片状态 chip 同源 */
const statusCounts = computed(() => ({
  enabled: agentStore.agents.filter(a => a.enabled).length,
  disabled: agentStore.agents.filter(a => !a.enabled).length,
}))

/** 新建/编辑弹窗状态 */
const dialogVisible = ref(false)
/** 正在编辑的 Agent ID（新建时为 null） */
const editingId = ref<number | string | null>(null)
/** 弹窗是否为只读（查看详情）模式 */
const dialogReadonly = ref(false)

/** 智能体名：兼容后端下发的中英文名（Agent 接口未声明，运行时可能携带，同 AgentContextView 的访问模式） */
function resolveAgentName(agent: Agent): string {
  const a = agent as Agent & { nameZh?: string; nameEn?: string }
  return a.nameZh || a.nameEn || a.name || `#${a.id}`
}

/** 标签：逗号分隔字符串 → 数组 */
function parseTags(tags: string | undefined | null): string[] {
  if (!tags) return []
  return tags.split(/[,，]/).map(s => s.trim()).filter(Boolean)
}

/** 底栏合并元信息行：模式已由卡头 slug 表达，此处只携带模型（无模型时整行不渲染，title 显示全文） */
function metaLabel(agent: Agent): string {
  return agent.modelName || ''
}

/** 加载智能体列表 */
async function loadAgents(): Promise<void> {
  loading.value = true
  try {
    await agentStore.fetchAgents(currentWorkspaceId.value)
  } catch (err) {
    console.error('[AgentConfigView] loadAgents failed:', err)
  } finally {
    loading.value = false
    statsReady.value = true
  }
}

/** 启停切换 */
async function handleToggle(agent: Agent, enabled: boolean): Promise<void> {
  try {
    await agentStore.updateAgent(agent.id, { ...agent, enabled })
    ElMessage.success(enabled ? t('configCenter.agentEnableSuccess') : t('configCenter.agentDisableSuccess'))
  } catch (err) {
    console.error('[AgentConfigView] handleToggle failed:', err)
  }
}

/** 查看 - 弹出只读详情弹窗 */
function handleView(agent: Agent): void {
  editingId.value = agent.id
  dialogReadonly.value = true
  dialogVisible.value = true
}

/** 编辑 - 弹出编辑智能体弹窗 */
function handleEdit(agent: Agent): void {
  editingId.value = agent.id
  dialogReadonly.value = false
  dialogVisible.value = true
}

/** 新建 - 弹出新建智能体弹窗 */
function openCreateDialog(): void {
  editingId.value = null
  dialogReadonly.value = false
  dialogVisible.value = true
}

/** 弹窗保存成功 */
function handleSaved(): void {
  // 列表由 store 自动刷新，无需额外操作
  editingId.value = null
}

/** 删除 */
async function handleDelete(agent: Agent): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('configCenter.agentDeleteConfirm', { name: resolveAgentName(agent) }),
      t('skillManage.deleteTitle'),
      { type: 'warning' },
    )
    await agentStore.deleteAgent(agent.id, agent.workspaceId)
    ElMessage.success(t('skillManage.deleteSuccess'))
  } catch {
    // 用户取消或失败
  }
}

onMounted(loadAgents)
</script>

<style scoped>
.agents-page {
  display: flex;
  flex-direction: column;
  gap: 0;
  width: 100%;
  /* 父容器为块级滚动容器，用 height:100% 取得确定高度，内部 .agents-scroll 才能独立滚动、头部保持固定 */
  height: 100%;
  min-height: 0;
  /* 透明底：配置壳纸面卡片已提供工作区层级，子视图不再自带灰底与外边距 */
  background: transparent;
  box-sizing: border-box;
  overflow: hidden;
}

/* 固定头部：身份页头，滚动时保持可见 */
.agents-fixed {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-bottom: 14px;
}

/* 可滚动列表区：横向负 margin + 等值内 padding，为卡片 hover 阴影/焦点环留出溢出余量，同时保持与固定头部对齐 */
.agents-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 10px 8px 24px;
  margin: 0 -8px;
}

/* 内容区身份页头：标题/描述靠左，主操作靠右，单行对齐 */
.agent-content-header {
  display: flex;
  align-items: center;
  gap: 16px;
}

.agent-content-title {
  min-width: 0;
}

.agent-content-name {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--db-text);
  line-height: 1.3;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 标题行：页面名称 + 启停数量标识同一基线 */
.agent-content-name-row {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

/* 描述：上下间距用 margin（line-clamp 元素加垂直 padding 会漏绘裁切行） */
.agent-content-desc {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--db-text-muted);
  line-height: 18px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 启停数量标识：紧凑内联，色彩编码与卡片状态 chip 一致 */
.agent-title-stats {
  display: inline-flex;
  align-items: center;
  gap: 14px;
  flex-shrink: 0;
}
.summary-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--db-text-secondary);
}
.summary-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--db-text-muted);
}
.summary-dot.on {
  background: var(--el-color-success);
}
.summary-num {
  font-size: 12px;
  font-weight: 700;
  color: var(--db-text);
  margin-left: 1px;
}
.summary-item.off .summary-num {
  color: var(--db-text-secondary);
}

/* 页头主操作：与技能页同一套 Element 胶囊按钮皮肤 */
.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  margin-left: auto;
  flex-shrink: 0;
}

.header-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.header-actions :deep(.el-button) {
  height: 36px;
  border-radius: 999px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 500;
}

.header-actions :deep(.el-button--primary) {
  background: var(--main-orange);
  border-color: var(--main-orange);
  box-shadow: var(--shadow-md);
}

.header-actions :deep(.el-button--primary:hover),
.header-actions :deep(.el-button--primary:focus) {
  background: var(--main-orange);
  border-color: var(--main-orange);
  filter: brightness(1.08);
}

/* 卡片面：系统统一变量（白面 + 细边 + 圆角，默认无阴影） */
.surface-card {
  background: var(--db-card);
  border: 1px solid var(--db-border);
  border-radius: var(--radius-lg, 12px);
}

.page-loading {
  text-align: center;
  padding: 60px 0;
  color: var(--db-text-muted);
  font-size: 13px;
}

/* 网格：280px 起配，适配侧栏占用后的窄内容区 */
.agent-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

/* 卡片：系统卡片家族 token（shadow-card 默认 / hover 上浮 + shadow-card-hover / 底栏沉底） */
.agent-card {
  padding: 16px 16px 14px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 168px;
  box-shadow: var(--shadow-card);
  overflow: hidden;
  transition:
    box-shadow var(--transition-base),
    border-color var(--transition-fast),
    transform var(--transition-fast);
}
.agent-card:hover {
  border-color: var(--db-border-strong);
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}
.agent-card.disabled {
  opacity: 0.62;
}

.agent-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 图标 chip：36px 锚点尺寸，蓝色主题 tint（智能体无类型维度，统一编码） */
.agent-icon-wrap {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--db-card-blue-bg);
  color: var(--db-card-blue-fg);
}
.agent-icon {
  font-size: 18px;
  line-height: 1;
}

.agent-meta {
  flex: 1;
  overflow: hidden;
  min-width: 0;
}
/* 名称：卡片唯一焦点，与 12px 正文 / 11px 元信息拉开层级 */
.agent-name {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--db-text);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.agent-slug {
  font-size: 11px;
  color: var(--db-text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 自定义 toggle 开关（与技能页同形态） */
.toggle-switch {
  position: relative;
  display: inline-block;
  width: 36px;
  height: 20px;
  cursor: pointer;
  flex-shrink: 0;
}
.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}
.toggle-slider {
  position: absolute;
  inset: 0;
  background: var(--db-border);
  border-radius: 20px;
  transition: 0.2s;
}
.toggle-slider::before {
  content: '';
  position: absolute;
  width: 14px;
  height: 14px;
  left: 3px;
  top: 3px;
  background: var(--db-card);
  border-radius: 50%;
  transition: 0.2s;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
}
.toggle-switch input:checked + .toggle-slider {
  background: var(--main-orange);
}
.toggle-switch input:checked + .toggle-slider::before {
  transform: translateX(16px);
}

/* 描述：两行截断；min-height 预留两行，单行描述卡与双行卡内容基线对齐 */
.agent-desc {
  font-size: 12px;
  color: var(--db-text-secondary);
  margin: 0;
  line-height: 20px;
  min-height: 40px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 标签：内容描述的一部分，紧跟描述 */
.agent-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin: 0;
}
.agent-tag {
  padding: 3px 8px;
  background: color-mix(in srgb, var(--db-text-muted) 12%, transparent);
  color: var(--db-text-secondary);
  border-radius: 6px;
  font-size: 10px;
}

/* 状态 chip：元信息行领衔，圆点+文字；启用用 Element success 绿，未启用降级为安静灰 */
.agent-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 600;
  color: var(--el-color-success);
}
.agent-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}
.agent-status.off {
  color: var(--db-text-muted);
  font-weight: 500;
}

/* 元信息合并行：模式·模型单行安静文本，flex:1 + 省略号收纳窄卡 */
.agent-meta-line {
  flex: 1;
  min-width: 0;
  font-size: 11px;
  color: var(--db-text-muted);
  letter-spacing: 0.02em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 底栏：margin-top:auto 沉底，等高卡的弹性空白只落在分隔线上方；状态 chip（左） + 元信息 + 操作（右） */
.agent-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  border-top: 1px solid var(--db-border);
  padding-top: 10px;
  margin-top: auto;
}
.agent-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
  margin-left: auto;
}
/* 操作按钮：图标+文字轻量按钮（透明底、悬停着色），删除红色 */
.agent-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border: none;
  background: transparent;
  font-size: 11.5px;
  color: var(--db-text-secondary);
  cursor: pointer;
  border-radius: 4px;
  padding: 4px 6px;
  font-family: inherit;
  transition: color var(--transition-fast, 0.15s), background var(--transition-fast, 0.15s);
  white-space: nowrap;
}
.agent-action-btn:hover {
  color: var(--db-text);
  background: var(--db-hover);
}
.agent-action-btn.action-delete {
  color: #ef4444;
}
.agent-action-btn.action-delete:hover {
  color: #dc2626;
  background: var(--db-danger-bg);
}

/* 全局空态：一整块接管列表区，图标为线型 SVG chip */
.global-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 56px 20px;
  text-align: center;
}
.global-empty-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--db-text-muted) 10%, transparent);
  color: var(--db-text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 14px;
}
.global-empty h3 {
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text);
  margin: 0 0 10px;
}

/* ===== 响应式适配 ===== */

/* 中等屏幕：grid 缩小最小宽度，页头换行 */
@media (max-width: 1024px) {
  .agent-grid {
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    gap: 12px;
  }
  .agent-content-header {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }
  .header-actions {
    margin-left: 0;
    justify-content: flex-end;
  }
}

/* 小屏幕：单列布局 */
@media (max-width: 720px) {
  /* 标题行允许换行：标题占满整行，启停数量落到第二行 */
  .agent-content-name-row {
    flex-wrap: wrap;
    gap: 6px 14px;
  }
  .agent-grid {
    grid-template-columns: 1fr;
    gap: 10px;
  }
  .agent-card {
    min-height: auto;
    padding: 14px;
  }
  .agent-header {
    gap: 10px;
  }
  .agent-icon-wrap {
    width: 26px;
    height: 26px;
  }
  .agent-icon {
    font-size: 14px;
  }
  .agent-name {
    font-size: 13px;
  }
  .agent-footer {
    flex-wrap: wrap;
    gap: 6px;
  }
}
</style>
