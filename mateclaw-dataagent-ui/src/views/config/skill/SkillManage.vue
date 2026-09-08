<template>
  <div class="skills-page">
    <!-- 固定头部（红框区域） -->
    <div class="skills-fixed">
      <!-- 页面头部 -->
      <div class="page-header">
        <div>
          <div class="page-kicker">Capabilities</div>
          <h1 class="page-title">{{ t('skillManage.title') }}</h1>
          <p class="page-desc">{{ t('skillManage.desc') }}</p>
        </div>
        <div class="header-actions">
          <button class="btn-secondary" :disabled="loading" @click="loadAll">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="23 4 23 10 17 10" />
              <polyline points="1 20 1 14 7 14" />
              <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
            </svg>
            {{ loading ? t('skillManage.refreshing') : t('skillManage.refresh') }}
          </button>
          <button v-if="hasPermission(PERMISSION.SKILL_MANAGE)" class="btn-secondary" @click="openImportDialog">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
              <polyline points="7 10 12 15 17 10" />
              <line x1="12" y1="15" x2="12" y2="3" />
            </svg>
            {{ t('skillManage.import') }}
          </button>
          <button v-if="hasPermission(PERMISSION.SKILL_MANAGE)" class="btn-primary" @click="openCreateModal">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19" />
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
            {{ t('skillManage.newSkill') }}
          </button>
        </div>
      </div>

      <!-- 分类 Tab -->
      <div class="category-tabs surface-card">
        <button
          v-for="tab in categoryTabs"
          :key="tab.value"
          class="cat-tab"
          :class="{ active: isTabActive(tab) }"
          @click="onTabChange(tab)"
        >
          <span class="cat-icon">{{ tab.icon }}</span>
          <span>{{ tab.label }}</span>
          <span class="cat-count">{{ getCategoryCount(tab) }}</span>
        </button>
      </div>

      <!-- 搜索 + 排序 -->
      <div class="skill-filter-bar surface-card">
        <input
          v-model="query.keyword"
          class="skill-search-input"
          type="search"
          :placeholder="t('skillManage.searchPlaceholder')"
          @keyup.enter="onFilterChange"
        />
        <select v-model="query.sort" class="skill-status-filter" @change="onFilterChange">
          <option value="recommended">{{ t('skillManage.sort.recommended') }}</option>
          <option value="name">{{ t('skillManage.sort.name') }}</option>
          <option value="status">{{ t('skillManage.sort.status') }}</option>
          <option value="type">{{ t('skillManage.sort.source') }}</option>
          <option value="updated">{{ t('skillManage.sort.updated') }}</option>
        </select>
      </div>
    </div>

    <!-- 可滚动列表区 -->
    <div class="skills-scroll">
      <!-- 已启用 / 未启用 两段式列表 -->
      <div v-for="section in skillSections" :key="section.key" class="skill-section">
        <div class="skill-section-head">
          <span class="skill-section-title">{{ section.label }}</span>
          <span class="skill-section-count">
            {{ t('skillManage.sectionCount', { n: section.state.total }) }}
          </span>
        </div>

      <div v-if="section.state.items.length > 0" class="skill-grid">
        <div
          v-for="skill in section.state.items"
          :key="skill.id"
          class="skill-card surface-card"
          :class="{ disabled: !!skill.enabled === false }"
          role="button"
          tabindex="0"
          @click="hasPermission(PERMISSION.SKILL_MANAGE) && openEditFromCard(skill)"
          @keydown.enter="hasPermission(PERMISSION.SKILL_MANAGE) && openEditFromCard(skill)"
        >
          <div class="skill-header">
            <div class="skill-icon-wrap" :class="getSkillIconBg(skill.skillType)">
              <span class="skill-icon">{{ skill.icon || getSkillIcon(skill.skillType) }}</span>
            </div>
            <div class="skill-meta">
              <h3 class="skill-name" :title="resolveSkillName(skill)">
                {{ resolveSkillName(skill) }}
              </h3>
              <div v-if="hasI18nName(skill)" class="skill-slug">{{ skill.name }}</div>
            </div>
            <label
              v-if="skill.builtin !== true && hasPermission(PERMISSION.SKILL_MANAGE)"
              class="toggle-switch"
              :title="t('skillManage.toggleTitle')"
              @click.stop
            >
              <input
                type="checkbox"
                :checked="!!skill.enabled"
                :disabled="togglingId === skill.id"
                @change="(e) => handleToggle(skill, (e.target as HTMLInputElement).checked)"
              />
              <span class="toggle-slider"></span>
            </label>
          </div>

          <p class="skill-desc" :title="skill.description">
            {{ skill.description || t('skillManage.noDescription') }}
          </p>

          <!-- 状态行：状态徽标 + 源 + 版本 + 标签 -->
          <div class="skill-status-row">
            <span class="status-pill" :class="getStatusPill(skill).cls">
              {{ getStatusPill(skill).label }}
            </span>
            <span class="source-label" :class="getSourceClass(skill)">
              {{ getSourceLabel(skill) }}
            </span>
            <span v-if="skill.version" class="skill-version">v{{ skill.version }}</span>
          </div>

          <!-- 标签 -->
          <div v-if="parseTags(skill.tags).length > 0" class="skill-tags">
            <span v-for="tag in parseTags(skill.tags)" :key="tag" class="skill-tag">
              {{ tag }}
            </span>
          </div>

          <!-- 底栏：作者 + 操作按钮 -->
          <div class="skill-footer" @click.stop>
            <span v-if="skill.author" class="skill-author">by {{ skill.author }}</span>
            <span v-else class="skill-author">—</span>
            <div class="skill-actions">
              <button
                v-if="hasPermission(PERMISSION.SKILL_MANAGE)"
                class="skill-btn"
                :title="t('skillManage.edit')"
                @click.stop="openEditFromCard(skill)"
              >
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                  <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
                </svg>
                <span>{{ t('skillManage.edit') }}</span>
              </button>
              <button
                v-if="skill.builtin !== true && hasPermission(PERMISSION.SKILL_MANAGE)"
                class="skill-btn danger"
                :title="t('skillManage.delete')"
                @click.stop="handleDelete(skill)"
              >
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="3 6 5 6 21 6" />
                  <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" />
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="empty-state surface-card">
        <div class="empty-icon">{{ section.key === 'enabled' ? '🧩' : '🪧' }}</div>
        <h3>
          {{
            section.key === 'enabled'
              ? t('skillManage.emptyEnabled')
              : t('skillManage.emptyAvailable')
          }}
        </h3>
        <p v-if="section.key === 'enabled'">{{ t('skillManage.emptyEnabledDesc') }}</p>
      </div>

      <!-- 每段独立分页 -->
      <div v-if="section.state.items.length > 0" class="skill-pagination">
        <el-pagination
          v-model:current-page="section.state.page"
          v-model:page-size="section.state.size"
          :page-sizes="[12, 20, 50]"
          :total="section.state.total"
          layout="prev, pager, next, sizes, total"
          background
          size="small"
          @current-change="() => loadSegment(section.key)"
          @size-change="() => loadSegment(section.key)"
        />
      </div>
      </div>
    </div>

    <!-- 新建 / 编辑技能弹窗 -->
    <div v-if="showModal" class="modal-overlay" @click.self="closeModal">
      <div class="modal" :class="{ 'modal-wide': editingSkillId != null }">
        <div class="modal-header">
          <h2>{{ editingSkillId != null ? t('skillManage.edit') : t('skillManage.create') }}</h2>
          <button class="modal-close" :disabled="submitting" @click="closeModal">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        <!-- 编辑模式：三 Tab 切换 -->
        <div v-if="editingSkillId != null" class="edit-tabs">
          <button
            class="edit-tab"
            :class="{ active: activeEditTab === 'basic' }"
            @click="activeEditTab = 'basic'"
          >
            {{ t('skillManage.tabBasic') }}
          </button>
          <button
            class="edit-tab"
            :class="{ active: activeEditTab === 'skillmd' }"
            @click="switchEditTab('skillmd')"
          >
            SKILL.md
          </button>
          <button
            class="edit-tab"
            :class="{ active: activeEditTab === 'files' }"
            @click="switchEditTab('files')"
          >
            {{ t('skillManage.tabFiles') }}
          </button>
          <button
            class="edit-tab"
            :class="{ active: activeEditTab === 'security' }"
            @click="switchEditTab('security')"
          >
            {{ t('skillManage.tabSecurity') }}
          </button>
        </div>

        <div class="modal-body">
          <!-- ===== Tab: 基础信息 ===== -->
          <div v-if="activeEditTab === 'basic'" class="edit-section">
            <p class="modal-hint">
              {{ editingSkillId != null ? t('skillManage.editHint') : t('skillManage.createHint') }}
            </p>
            <div class="form-grid">
              <div class="form-group">
                <label class="form-label">{{ t('skillManage.fieldName') }} *</label>
                <input
                  v-model="form.name"
                  class="form-input"
                  :placeholder="t('skillManage.fieldNamePlaceholder')"
                  :disabled="submitting"
                />
              </div>
              <div class="form-group">
                <label class="form-label">{{ t('skillManage.fieldType') }}</label>
                <select v-model="form.skillType" class="form-input" :disabled="submitting">
                  <option v-for="opt in SKILL_TYPE_OPTIONS.filter(o => o.value !== 'all')" :key="opt.value" :value="opt.value">
                    {{ t('skillManage.type_' + opt.value) }}
                  </option>
                </select>
              </div>
              <div class="form-group">
                <label class="form-label">{{ t('skillManage.fieldNameZh') }}</label>
                <input
                  v-model="form.nameZh"
                  class="form-input"
                  :placeholder="t('skillManage.fieldNameZhPlaceholder')"
                  :disabled="submitting"
                />
              </div>
              <div class="form-group">
                <label class="form-label">{{ t('skillManage.fieldNameEn') }}</label>
                <input
                  v-model="form.nameEn"
                  class="form-input"
                  :placeholder="t('skillManage.fieldNameEnPlaceholder')"
                  :disabled="submitting"
                />
              </div>
              <div class="form-group">
                <label class="form-label">{{ t('skillManage.fieldIcon') }}</label>
                <input
                  v-model="form.icon"
                  class="form-input"
                  :placeholder="t('skillManage.fieldIconPlaceholder')"
                  :disabled="submitting"
                />
              </div>
              <div class="form-group">
                <label class="form-label">{{ t('skillManage.fieldAuthor') }}</label>
                <input
                  v-model="form.author"
                  class="form-input"
                  :placeholder="t('skillManage.fieldAuthorPlaceholder')"
                  :disabled="submitting"
                />
              </div>
              <div class="form-group full-width">
                <label class="form-label">{{ t('skillManage.fieldDescription') }}</label>
                <textarea
                  v-model="form.description"
                  class="form-input form-textarea"
                  rows="3"
                  :placeholder="t('skillManage.fieldDescriptionPlaceholder')"
                  :disabled="submitting"
                />
              </div>
              <div class="form-group full-width">
                <label class="form-label">{{ t('skillManage.fieldTags') }}</label>
                <input
                  v-model="form.tags"
                  class="form-input"
                  :placeholder="t('skillManage.fieldTagsPlaceholder')"
                  :disabled="submitting"
                />
              </div>
              <div class="form-group full-width form-row-inline">
                <label class="form-label">{{ t('skillManage.fieldEnabled') }}</label>
                <el-switch v-model="form.enabled" :disabled="submitting" />
              </div>
            </div>
          </div>

          <!-- ===== Tab: SKILL.md ===== -->
          <div v-else-if="activeEditTab === 'skillmd'" class="edit-section">
            <p class="modal-hint">{{ t('skillManage.skillMdHint') }}</p>
            <textarea
              v-model="skillMdContent"
              class="form-input form-textarea code-textarea"
              rows="18"
              :placeholder="t('skillManage.skillMdPlaceholder')"
              :disabled="savingSkillMd"
            />
            <div class="edit-footer">
              <span class="char-count">{{ t('skillManage.charCount', { n: skillMdContent.length }) }}</span>
              <button class="btn-primary" :disabled="savingSkillMd" @click="saveSkillMd">
                {{ savingSkillMd ? t('common.loading') : t('common.save') }}
              </button>
            </div>
          </div>

          <!-- ===== Tab: 相关文件 ===== -->
          <div v-else-if="activeEditTab === 'files'" class="edit-section">
            <div class="files-layout">
              <!-- 左侧文件列表 -->
              <div class="files-list">
                <div class="files-list-head">
                  <span>{{ t('skillManage.filesTitle') }}</span>
                  <button class="skill-btn" :disabled="filesLoading" @click="startNewFile">
                    + {{ t('skillManage.fileNew') }}
                  </button>
                </div>
                <div v-if="filesLoading" class="files-hint">{{ t('common.loading') }}</div>
                <div v-else-if="skillFiles.length === 0" class="files-hint">{{ t('skillManage.filesEmpty') }}</div>
                <button
                  v-for="f in skillFiles"
                  :key="f.filePath"
                  class="file-item"
                  :class="{ active: !creatingFile && activeFilePath === f.filePath }"
                  @click="openSkillFile(f.filePath)"
                >
                  <span class="file-name" :title="f.filePath">{{ f.filePath }}</span>
                  <span class="file-size">{{ formatFileSize(f.contentSize) }}</span>
                </button>
              </div>

              <!-- 右侧文件编辑区 -->
              <div class="file-editor">
                <!-- 新建文件 -->
                <template v-if="creatingFile">
                  <input
                    v-model="newFilePath"
                    class="form-input file-path-input"
                    :placeholder="t('skillManage.filePathPlaceholder')"
                    :disabled="savingFile"
                  />
                  <textarea
                    v-model="activeFileContent"
                    class="form-input form-textarea code-textarea"
                    rows="14"
                    :disabled="savingFile"
                  />
                  <div class="edit-footer">
                    <button class="btn-secondary" :disabled="savingFile" @click="closeFileEditor">
                      {{ t('common.cancel') }}
                    </button>
                    <button class="btn-primary" :disabled="savingFile" @click="saveActiveFile">
                      {{ savingFile ? t('common.loading') : t('common.save') }}
                    </button>
                  </div>
                </template>

                <!-- 编辑已有文件 -->
                <template v-else-if="activeFilePath">
                  <div class="file-editor-head">
                    <span class="file-path" :title="activeFilePath">{{ activeFilePath }}</span>
                    <button class="modal-close" :disabled="savingFile" @click="closeFileEditor">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <line x1="18" y1="6" x2="6" y2="18" />
                        <line x1="6" y1="6" x2="18" y2="18" />
                      </svg>
                    </button>
                  </div>
                  <div v-if="fileContentLoading" class="files-hint">{{ t('common.loading') }}</div>
                  <template v-else>
                    <textarea
                      v-model="activeFileContent"
                      class="form-input form-textarea code-textarea"
                      rows="14"
                      :disabled="savingFile"
                    />
                    <div class="edit-footer">
                      <span class="char-count">{{ t('skillManage.charCount', { n: activeFileContent.length }) }}</span>
                      <button class="btn-primary" :disabled="savingFile" @click="saveActiveFile">
                        {{ savingFile ? t('common.loading') : t('common.save') }}
                      </button>
                    </div>
                  </template>
                </template>

                <!-- 未选择文件 -->
                <div v-else class="file-editor-empty">{{ t('skillManage.fileSelectHint') }}</div>
              </div>
            </div>
          </div>

          <!-- ===== Tab: 安全扫描 ===== -->
          <div v-else-if="activeEditTab === 'security'" class="edit-section">
            <div class="scan-head">
              <span class="status-pill" :class="scanStatusPill.cls">{{ scanStatusPill.label }}</span>
              <span v-if="scanTimeText" class="scan-time">{{ t('skillManage.securityScanTime') }}: {{ scanTimeText }}</span>
              <button
                v-if="canRescan"
                class="btn-secondary scan-rescan-btn"
                :disabled="rescanning"
                @click="handleRescan"
              >
                {{ rescanning ? t('skillManage.securityRescanning') : t('skillManage.securityRescan') }}
              </button>
            </div>

            <ul v-if="parsedFindings.length > 0" class="scan-findings-list">
              <li
                v-for="(f, idx) in parsedFindings"
                :key="`scan-f-${idx}`"
                class="scan-finding-item"
                :class="`sev-${(f.severity || 'info').toLowerCase()}`"
              >
                <div class="scan-finding-head">
                  <span class="scan-finding-sev">[{{ f.severity || 'INFO' }}]</span>
                  <span class="scan-finding-id">{{ f.ruleId || f.category || '—' }}</span>
                  <span v-if="f.filePath" class="scan-finding-loc">
                    {{ f.filePath }}<span v-if="f.lineNumber">:{{ f.lineNumber }}</span>
                  </span>
                </div>
                <div v-if="f.title" class="scan-finding-title">{{ f.title }}</div>
                <div v-if="f.description" class="scan-finding-desc">{{ f.description }}</div>
                <div v-if="f.remediation" class="scan-finding-fix">
                  {{ t('skillManage.securityFix') }}: {{ f.remediation }}
                </div>
              </li>
            </ul>
            <div v-else-if="editingSkill?.securityScanStatus === 'FAILED'" class="scan-empty">
              {{ t('skillManage.securityNoFindings') }}
            </div>
            <div v-else-if="editingSkill?.securityScanStatus === 'PASSED'" class="scan-empty">
              {{ t('skillManage.securityPassed') }}
            </div>
            <div v-else class="scan-empty">{{ t('skillManage.securityNotScanned') }}</div>
          </div>
        </div>

        <!-- 弹窗底栏：仅基础信息 Tab 使用 -->
        <div v-if="activeEditTab === 'basic'" class="modal-footer">
          <button class="btn-secondary" :disabled="submitting" @click="closeModal">
            {{ t('common.cancel') }}
          </button>
          <button class="btn-primary" :disabled="!form.name || submitting" @click="handleSubmit">
            {{ submitting ? t('common.loading') : t('common.confirm') }}
          </button>
        </div>
      </div>
    </div>

    <!-- 导入技能弹窗（URL / 市场 / ZIP） -->
    <ImportSkillDialog
      v-model:visible="importDialogVisible"
      :workspace-id="currentWorkspaceId"
      @installed="onSkillInstalled"
      @removed="onSkillRemoved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, ElPagination } from 'element-plus'
import * as skillApi from '@/api/skill'
import { SKILL_TYPE_OPTIONS, type Skill, type SkillFileView, type SkillSecurityFinding } from '@/types'
import { useAgentStore } from '@/stores/useAgentStore'
import { usePermission, PERMISSION } from '@/composables/usePermission'
import ImportSkillDialog from './ImportSkillDialog.vue'

const { t, locale } = useI18n()
const agentStore = useAgentStore()
const { hasPermission } = usePermission()

/** 当前工作区 ID：优先从当前 Agent 获取，否则取列表中第一个 Agent 的，最后兜底 1 */
const currentWorkspaceId = computed<number>(() => {
  const id = agentStore.currentAgent?.workspaceId
    ?? agentStore.agents[0]?.workspaceId
    ?? 1
  // 确保返回 number 类型
  return typeof id === 'number' ? id : Number(id) || 1
})

/** 每段分页状态：已启用 / 未启用 */
interface SkillSegment {
  items: Skill[]
  total: number
  page: number
  size: number
}
const segments = reactive<{ enabled: SkillSegment; available: SkillSegment }>({
  enabled: { items: [], total: 0, page: 1, size: 12 },
  available: { items: [], total: 0, page: 1, size: 12 },
})
const skillSections = computed(() => [
  { key: 'enabled' as const, label: t('skillManage.sectionEnabled'), state: segments.enabled },
  { key: 'available' as const, label: t('skillManage.sectionAvailable'), state: segments.available },
])

/** 分类 Tab 计数（按后端 page 总数） */
const counts = ref<Record<string, number>>({})

/** 整体加载态 */
const loading = ref(false)
/** 提交中 */
const submitting = ref(false)
/** 切换中 */
const togglingId = ref<number | null>(null)

/** 筛选条件 */
const query = reactive({
  keyword: '',
  skillType: 'all' as string,
  sort: 'recommended' as string,
  lifecycleState: '' as string,
})

/** 弹窗 */
const showModal = ref(false)

/** 当前编辑的技能 ID（null 表示新建模式） */
const editingSkillId = ref<number | null>(null)

/** 编辑弹窗当前 Tab：basic / skillmd / files / security */
const activeEditTab = ref<'basic' | 'skillmd' | 'files' | 'security'>('basic')

/** 当前编辑的技能实体（含扫描状态等详情字段） */
const editingSkill = ref<Skill | null>(null)

/** 安全扫描重扫中 */
const rescanning = ref(false)

/** 最近一次扫描的发现列表（解析 securityScanResult JSON） */
const parsedFindings = computed<SkillSecurityFinding[]>(() => {
  const raw = editingSkill.value?.securityScanResult
  if (!raw) return []
  try {
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? (arr as SkillSecurityFinding[]) : []
  } catch {
    return []
  }
})

/** 扫描状态徽标：PASSED 绿 / FAILED 红 / 其余视为未扫描 */
const scanStatusPill = computed<{ label: string; cls: string }>(() => {
  const status = editingSkill.value?.securityScanStatus
  if (status === 'FAILED') {
    return { label: t('skillManage.securityScanFailed'), cls: 'st-blocked' }
  }
  if (status === 'PASSED') {
    return { label: t('skillManage.securityScanPassed'), cls: 'st-ready' }
  }
  return { label: t('skillManage.securityNotScanned'), cls: 'st-disabled' }
})

/** 扫描时间展示文本 */
const scanTimeText = computed<string>(() => {
  const raw = editingSkill.value?.securityScanTime
  if (!raw) return ''
  const d = new Date(raw)
  return Number.isNaN(d.getTime()) ? raw : d.toLocaleString()
})

/** 是否允许重扫：MCP 协议技能固定标记 PASSED，且虚拟 MCP 技能服务端会拒绝重扫 */
const canRescan = computed(() => editingSkill.value?.skillType !== 'mcp')

/** 重新执行安全扫描 */
async function handleRescan(): Promise<void> {
  if (editingSkill.value == null || rescanning.value) return
  rescanning.value = true
  try {
    const updated = await skillApi.rescan(editingSkill.value.id)
    if (updated) {
      editingSkill.value = updated
    }
    ElMessage.success(t('skillManage.securityRescanDone'))
  } catch {
    // 错误由拦截器处理
  } finally {
    rescanning.value = false
  }
}

/** SKILL.md 协议正文 */
const skillMdContent = ref('')
/** SKILL.md 保存中 */
const savingSkillMd = ref(false)

/** 技能 bundle 文件列表（references/、scripts/） */
const skillFiles = ref<SkillFileView[]>([])
/** 文件列表加载中 */
const filesLoading = ref(false)
/** 当前打开的文件路径 */
const activeFilePath = ref('')
/** 当前打开的文件正文 */
const activeFileContent = ref('')
/** 文件正文加载中 */
const fileContentLoading = ref(false)
/** 文件保存中 */
const savingFile = ref(false)
/** 是否处于新建文件模式 */
const creatingFile = ref(false)
/** 新建文件的路径输入 */
const newFilePath = ref('')

/** 导入弹窗 */
const importDialogVisible = ref(false)

/** 打开导入技能弹窗 */
function openImportDialog(): void {
  importDialogVisible.value = true
}

/** 导入成功回调：刷新列表 */
async function onSkillInstalled(_name: string): Promise<void> {
  await loadAll()
}

/** 卸载回调：刷新列表 */
async function onSkillRemoved(_name: string): Promise<void> {
  await loadAll()
}

/** 表单数据 */
const form = reactive({
  name: '',
  nameZh: '',
  nameEn: '',
  description: '',
  skillType: 'custom' as string,
  icon: '',
  author: '',
  tags: '',
  enabled: true,
})

/** 分类 Tab 定义 */
const categoryTabs = computed(() => [
  { value: 'all', label: t('skillManage.tabAll'), icon: '🧩' },
  { value: 'builtin', label: t('skillManage.tabBuiltin'), icon: '🏗️' },
  { value: 'mcp', label: t('skillManage.tabMcp'), icon: '🔌' },
  { value: 'dynamic', label: t('skillManage.tabDynamic'), icon: '⚙️' },
])

function getCategoryCount(tab: { value: string }): number {
  return counts.value[tab.value] ?? 0
}

function isTabActive(tab: { value: string }): boolean {
  return !query.lifecycleState && query.skillType === tab.value
}

onMounted(() => {
  console.log('[SkillManage] 初始化, workspaceId:', currentWorkspaceId.value)
  loadAll()
})

/** 监听 workspaceId 变化（agent 列表加载后可能更新为真实值） */
watch(currentWorkspaceId, (val) => {
  if (val != null) {
    console.log('[SkillManage] workspaceId 更新:', val)
    loadAll()
  }
})

async function loadAll(): Promise<void> {
  loadCounts()
  await loadSkills()
}

async function loadCounts(): Promise<void> {
  try {
    const res = await skillApi.list(currentWorkspaceId.value)
    const list: Skill[] = (res as unknown as Skill[]) || []
    const map: Record<string, number> = { all: list.length, builtin: 0, mcp: 0, custom: 0 }
    for (const s of list) {
      const type = (s.skillType || 'custom').toLowerCase()
      if (map[type] !== undefined) {
        map[type] += 1
      } else {
        map.custom += 1
      }
    }
    counts.value = map
  } catch (err) {
    console.error('[SkillManage] loadCounts failed:', err)
    counts.value = {}
  }
}

function resetSegmentPages(): void {
  segments.enabled.page = 1
  segments.available.page = 1
}

function onTabChange(tab: { value: string }): void {
  query.skillType = tab.value
  query.lifecycleState = ''
  resetSegmentPages()
  loadSkills()
}

function onFilterChange(): void {
  resetSegmentPages()
  loadSkills()
}

async function loadSkills(): Promise<void> {
  await Promise.all([loadSegment('enabled'), loadSegment('available')])
}

async function loadSegment(key: 'enabled' | 'available'): Promise<void> {
  const seg = segments[key]
  const params: Record<string, unknown> = {
    page: seg.page,
    size: seg.size,
    enabled: key === 'enabled',
    workspaceId: currentWorkspaceId.value,
  }
  if (query.keyword) params.keyword = query.keyword.trim()
  if (query.skillType && query.skillType !== 'all') params.skillType = query.skillType
  if (query.sort && query.sort !== 'recommended') params.sort = query.sort
  if (query.lifecycleState) params.lifecycleState = query.lifecycleState

  loading.value = true
  try {
    const data = await skillApi.page(params as Parameters<typeof skillApi.page>[0])
    seg.items = data?.records || []
    seg.total = Number(data?.total) || 0
  } catch (err) {
    console.error(`[SkillManage] loadSegment(${key}) failed:`, err)
    seg.items = []
    seg.total = 0
  } finally {
    loading.value = false
  }
}

function openCreateModal(): void {
  resetForm()
  editingSkillId.value = null
  editingSkill.value = null
  activeEditTab.value = 'basic'
  showModal.value = true
}

function closeModal(): void {
  if (submitting.value) return
  showModal.value = false
}

function resetForm(): void {
  form.name = ''
  form.nameZh = ''
  form.nameEn = ''
  form.description = ''
  form.skillType = 'dynamic'
  form.icon = ''
  form.author = ''
  form.tags = ''
  form.enabled = true
}

/** 卡片点击或编辑按钮触发：加载详情后打开弹窗 */
async function openEditFromCard(skill: Skill): Promise<void> {
  // 重置文件与扫描编辑状态
  editingSkillId.value = null
  activeEditTab.value = 'basic'
  skillMdContent.value = ''
  skillFiles.value = []
  editingSkill.value = null
  closeFileEditor()
  try {
    const detail = await skillApi.get(skill.id)
    const target = detail || skill
    editingSkillId.value = skill.id
    editingSkill.value = target
    form.name = target.name || ''
    form.nameZh = target.nameZh || ''
    form.nameEn = target.nameEn || ''
    form.description = target.description || ''
    form.skillType = target.skillType || 'dynamic'
    form.icon = target.icon || ''
    form.author = target.author || ''
    form.tags = target.tags || ''
    form.enabled = !!target.enabled
    skillMdContent.value = target.skillContent || ''
    showModal.value = true
  } catch {
    // 加载失败则回退到当前卡片数据（SKILL.md 正文与扫描详情不可用）
    editingSkillId.value = skill.id
    editingSkill.value = skill
    form.name = skill.name
    form.nameZh = skill.nameZh || ''
    form.nameEn = skill.nameEn || ''
    form.description = skill.description || ''
    form.skillType = skill.skillType || 'custom'
    form.icon = skill.icon || ''
    form.author = skill.author || ''
    form.tags = skill.tags || ''
    form.enabled = !!skill.enabled
    showModal.value = true
  }
}

// ==================== 编辑弹窗：SKILL.md / 相关文件 ====================

/** 切换编辑 Tab：首次进入相关文件 Tab 时懒加载文件列表 */
function switchEditTab(tab: 'skillmd' | 'files' | 'security'): void {
  activeEditTab.value = tab
  if (tab === 'files' && skillFiles.value.length === 0 && !filesLoading.value) {
    loadSkillFiles()
  }
}

/** 保存 SKILL.md 协议正文 */
async function saveSkillMd(): Promise<void> {
  if (editingSkillId.value == null || savingSkillMd.value) return
  savingSkillMd.value = true
  try {
    await skillApi.update(editingSkillId.value, { skillContent: skillMdContent.value } as Partial<Skill>)
    ElMessage.success(t('skillManage.saveSuccess'))
    await loadAll()
  } catch {
    // 错误由拦截器处理
  } finally {
    savingSkillMd.value = false
  }
}

/** 加载技能 bundle 文件列表 */
async function loadSkillFiles(): Promise<void> {
  if (editingSkillId.value == null) return
  filesLoading.value = true
  try {
    const res = await skillApi.listFiles(editingSkillId.value)
    skillFiles.value = res || []
  } catch {
    skillFiles.value = []
  } finally {
    filesLoading.value = false
  }
}

/** 打开指定文件：加载正文进入编辑态 */
async function openSkillFile(filePath: string): Promise<void> {
  if (editingSkillId.value == null || fileContentLoading.value) return
  creatingFile.value = false
  activeFilePath.value = filePath
  activeFileContent.value = ''
  fileContentLoading.value = true
  try {
    const res = await skillApi.getFileContent(editingSkillId.value, filePath)
    activeFileContent.value = res?.content ?? ''
  } catch {
    activeFileContent.value = ''
  } finally {
    fileContentLoading.value = false
  }
}

/** 进入新建文件模式 */
function startNewFile(): void {
  creatingFile.value = true
  activeFilePath.value = ''
  activeFileContent.value = ''
  newFilePath.value = 'references/'
}

/** 关闭文件编辑区 */
function closeFileEditor(): void {
  creatingFile.value = false
  activeFilePath.value = ''
  activeFileContent.value = ''
}

/** 保存当前文件（新建或已有） */
async function saveActiveFile(): Promise<void> {
  if (editingSkillId.value == null || savingFile.value) return
  const path = creatingFile.value ? newFilePath.value.trim() : activeFilePath.value
  if (!path) {
    ElMessage.warning(t('skillManage.filePathRequired'))
    return
  }
  savingFile.value = true
  try {
    await skillApi.updateFileContent(editingSkillId.value, path, activeFileContent.value)
    ElMessage.success(t('skillManage.saveSuccess'))
    creatingFile.value = false
    activeFilePath.value = path
    await loadSkillFiles()
  } catch {
    // 错误由拦截器处理
  } finally {
    savingFile.value = false
  }
}

/** 文件大小展示：B / KB */
function formatFileSize(bytes: number | null | undefined): string {
  if (bytes == null) return ''
  if (bytes < 1024) return `${bytes} B`
  return `${(bytes / 1024).toFixed(1)} KB`
}

async function handleSubmit(): Promise<void> {
  if (!form.name.trim() || submitting.value) return
  submitting.value = true
  try {
    const payload: Record<string, unknown> = {
      name: form.name.trim(),
      nameZh: form.nameZh.trim() || undefined,
      nameEn: form.nameEn.trim() || undefined,
      description: form.description.trim() || undefined,
      skillType: form.skillType,
      icon: form.icon || undefined,
      author: form.author || undefined,
      tags: form.tags || undefined,
      enabled: form.enabled,
      workspaceId: currentWorkspaceId.value,
    }
    // 在已启用 / 未启用段中查找已存在记录 → 走更新
    const existing = findLoadedSkill(s => s.name === form.name.trim())
    if (existing) {
      await skillApi.update(existing.id, payload as Partial<Skill>)
      ElMessage.success(t('skillManage.updateSuccess'))
    } else {
      await skillApi.create(payload as Partial<Skill>)
      ElMessage.success(t('skillManage.createSuccess'))
    }
    showModal.value = false
    await loadAll()
  } catch (err) {
    console.error('[SkillManage] handleSubmit failed:', err)
    // 错误由拦截器处理
  } finally {
    submitting.value = false
  }
}

function findLoadedSkill(pred: (s: Skill) => boolean): Skill | undefined {
  return segments.enabled.items.find(pred) || segments.available.items.find(pred)
}

async function handleToggle(skill: Skill, enabled: boolean): Promise<void> {
  togglingId.value = skill.id
  try {
    await skillApi.toggle(skill.id, enabled)
    // 启停切换会导致该卡片在两段之间移动，重载两段
    await loadSkills()
    ElMessage.success(t(enabled ? 'skillManage.toggleOn' : 'skillManage.toggleOff'))
  } catch {
    // 错误由拦截器处理
  } finally {
    togglingId.value = null
  }
}

async function handleDelete(skill: Skill): Promise<void> {
  if (skill.builtin === true) {
    ElMessage.warning(t('skillManage.builtinUndeletable'))
    return
  }
  try {
    await ElMessageBox.confirm(
      t('skillManage.deleteConfirm', { name: resolveSkillName(skill) }),
      t('skillManage.deleteTitle'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        type: 'warning',
      }
    )
    await skillApi.remove(skill.id)
    ElMessage.success(t('skillManage.deleteSuccess'))
    await loadAll()
  } catch {
    // 取消或错误
  }
}

// ==================== 显示辅助 ====================

/** 按当前 locale 解析显示名（中文优先 nameZh，英文优先 nameEn） */
function resolveSkillName(skill: Skill): string {
  const isZh = locale.value.startsWith('zh')
  if (isZh && skill.nameZh) return skill.nameZh
  if (!isZh && skill.nameEn) return skill.nameEn
  return skill.name
}

/** i18n 名称与 slug 不一致时，UI 显示 slug 副标题 */
function hasI18nName(skill: Skill): boolean {
  return !!(skill.nameZh || skill.nameEn) && (skill.nameZh !== skill.name || skill.nameEn !== skill.name)
}

function parseTags(tags: string): string[] {
  if (!tags) return []
  return tags.split(',').map(s => s.trim()).filter(Boolean)
}

function getSkillIcon(type: string): string {
  const map: Record<string, string> = {
    builtin: '🏗️',
    mcp: '🔌',
    custom: '⚙️',
  }
  return map[type] || '🧩'
}

function getSkillIconBg(type: string): string {
  const map: Record<string, string> = {
    builtin: 'icon-bg-blue',
    mcp: 'icon-bg-purple',
    custom: 'icon-bg-orange',
  }
  return map[type] || 'icon-bg-grey'
}

/** 状态徽标：单一颜色，仅反映当前 enable 状态 */
function getStatusPill(skill: Skill): { label: string; cls: string } {
  if (skill.enabled === false) {
    return { label: t('skillManage.statusDisabled'), cls: 'st-disabled' }
  }
  return { label: t('skillManage.statusEnabled'), cls: 'st-ready' }
}

/** 源标签：builtin / mcp / 自定义 */
function getSourceLabel(skill: Skill): string {
  if (skill.builtin === true) return t('skillManage.sourceBuiltin')
  if (skill.skillType === 'mcp') return 'MCP'
  if (skill.skillType === 'builtin') return t('skillManage.sourceBuiltin')
  return t('skillManage.sourceCustom')
}

function getSourceClass(skill: Skill): string {
  if (skill.builtin === true || skill.skillType === 'builtin') return 'src-builtin'
  if (skill.skillType === 'mcp') return 'src-protocol'
  return 'src-local'
}
</script>

<style scoped>
.skills-page {
  display: flex;
  flex-direction: column;
  gap: 0;
  width: 100%;
  flex: 1;
  min-height: 0;
  padding: 20px 24px 0;
  background: var(--theme-bg);
  box-sizing: border-box;
  overflow: hidden;
}

/* 固定头部（红框内）：头部 + 分类 Tab + 搜索 */
.skills-fixed {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-bottom: 14px;
}

/* 可滚动列表区 */
.skills-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-bottom: 20px;
}
/* 页面头部 */
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.page-kicker {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--main-orange);
  margin-bottom: 2px;
}

.page-title {
  margin: 0 0 4px 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--dark-text);
}

.page-desc {
  margin: 0;
  font-size: 12px;
  color: var(--muted);
  max-width: 720px;
  line-height: 1.5;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

/* 按钮 */
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 16px;
  background: var(--main-orange);
  color: #fff;
  border: none;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
  transition: background 0.15s;
}
.btn-primary:hover:not(:disabled) {
  background: var(--dark-orange);
}
.btn-primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: var(--white);
  color: var(--body-text);
  border: 1px solid var(--light-grey);
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.15s;
}
.btn-secondary:hover:not(:disabled) {
  border-color: var(--main-orange);
  color: var(--main-orange);
}
.btn-secondary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* 毛玻璃卡片 */
.surface-card {
  background: var(--white);
  border: 1px solid var(--light-grey);
  border-radius: 14px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03);
}

/* 分类 Tab */
.category-tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  padding: 10px 14px;
}

.cat-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border: 1px solid var(--light-grey);
  background: var(--white);
  border-radius: 999px;
  font-size: 12px;
  color: var(--body-text);
  cursor: pointer;
  font-family: inherit;
  font-weight: 600;
  transition: all 0.15s;
}
.cat-tab:hover {
  border-color: var(--main-orange);
  color: var(--main-orange);
}
.cat-tab.active {
  background: var(--very-light-orange);
  border-color: var(--main-orange);
  color: var(--main-orange);
  font-weight: 600;
}
.cat-icon {
  font-size: 13px;
}
.cat-count {
  background: var(--lighter-grey);
  color: var(--muted);
  padding: 1px 7px;
  border-radius: 10px;
  font-size: 10px;
  font-weight: 600;
}
.cat-tab.active .cat-count {
  background: rgba(65, 118, 230, 0.18);
  color: var(--main-orange);
}

/* 搜索 + 排序 */
.skill-filter-bar {
  display: flex;
  gap: 10px;
  padding: 10px 14px;
  align-items: center;
}

.skill-search-input,
.skill-status-filter {
  height: 34px;
  border: 1px solid var(--light-grey);
  background: var(--white);
  border-radius: 10px;
  font-size: 13px;
  color: var(--dark-text);
  outline: none;
  font-family: inherit;
  transition: all 0.15s;
}
.skill-search-input {
  flex: 1;
  padding: 0 12px;
}
.skill-status-filter {
  padding: 0 10px;
  cursor: pointer;
  min-width: 160px;
}
.skill-search-input:focus,
.skill-status-filter:focus {
  border-color: var(--main-orange);
  box-shadow: 0 0 0 3px rgba(65, 118, 230, 0.1);
}

/* 段（已启用 / 未启用） */
.skill-section {
  display: flex;
  flex-direction: column;
}

.skill-section-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 10px;
}
.skill-section-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--dark-text);
}
.skill-section-count {
  font-size: 11px;
  font-weight: 600;
  color: var(--muted);
  background: var(--lighter-grey);
  padding: 2px 8px;
  border-radius: 10px;
}

/* 网格 */
.skill-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 14px;
}

/* 卡片 */
.skill-card {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 200px;
  cursor: pointer;
  outline: none;
  transition: all 0.15s;
}
.skill-card:hover {
  border-color: var(--main-orange);
  box-shadow: 0 4px 14px rgba(65, 118, 230, 0.08);
  transform: translateY(-1px);
}
.skill-card:focus-visible {
  border-color: var(--main-orange);
  box-shadow: 0 0 0 3px rgba(65, 118, 230, 0.12);
}
.skill-card.disabled {
  opacity: 0.62;
}

.skill-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.skill-icon-wrap {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.icon-bg-blue { background: rgba(64, 158, 255, 0.12); }
.icon-bg-purple { background: rgba(146, 84, 222, 0.12); }
.icon-bg-orange { background: rgba(65, 118, 230, 0.12); }
.icon-bg-grey { background: var(--lighter-grey); }
.skill-icon {
  font-size: 20px;
}

.skill-meta {
  flex: 1;
  overflow: hidden;
  min-width: 0;
}
.skill-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--dark-text);
  margin: 0 0 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.skill-slug {
  font-size: 11px;
  color: var(--muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 自定义 toggle 开关 */
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
  background: var(--light-grey);
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
  background: var(--white);
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
.toggle-switch input:disabled + .toggle-slider {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 描述 */
.skill-desc {
  font-size: 12px;
  color: var(--body-text);
  margin: 0;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex: 1;
}

/* 状态行 */
.skill-status-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin: 0;
}
.status-pill {
  padding: 2px 9px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.02em;
  display: inline-flex;
  align-items: center;
}
.status-pill.st-ready {
  background: rgba(34, 197, 94, 0.14);
  color: #16a34a;
}
.status-pill.st-disabled {
  background: var(--lighter-grey);
  color: var(--muted);
}
.status-pill.st-blocked {
  background: rgba(245, 63, 63, 0.14);
  color: #dc2626;
}
.source-label {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
}
.source-label.src-builtin {
  background: rgba(34, 197, 94, 0.14);
  color: #16a34a;
}
.source-label.src-protocol {
  background: rgba(99, 102, 241, 0.14);
  color: #6366f1;
}
.source-label.src-local {
  background: var(--lighter-grey);
  color: var(--muted);
}
.skill-version {
  font-size: 11px;
  color: var(--muted);
  margin-left: auto;
}

/* 标签 */
.skill-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  margin: 0;
}
.skill-tag {
  padding: 2px 8px;
  background: var(--lighter-grey);
  color: var(--body-text);
  border-radius: 4px;
  font-size: 10px;
}

/* 底栏 */
.skill-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid var(--light-grey);
  padding-top: 10px;
  margin-top: 2px;
}
.skill-author {
  font-size: 11px;
  color: var(--muted);
}
.skill-actions {
  display: flex;
  gap: 6px;
}
.skill-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  border: 1px solid var(--light-grey);
  background: var(--white);
  border-radius: 8px;
  font-size: 11px;
  color: var(--body-text);
  cursor: pointer;
  font-family: inherit;
  font-weight: 600;
  transition: all 0.15s;
}
.skill-btn:hover {
  border-color: var(--main-orange);
  color: var(--main-orange);
}
.skill-btn.danger:hover {
  border-color: #f53f3f;
  color: #f53f3f;
  background: rgba(245, 63, 63, 0.12);
}

/* 分页 */
.skill-pagination {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
}
.empty-icon {
  font-size: 42px;
  margin-bottom: 12px;
  opacity: 0.7;
}
.empty-state h3 {
  font-size: 15px;
  font-weight: 600;
  color: var(--dark-text);
  margin: 0 0 4px;
}
.empty-state p {
  font-size: 12px;
  color: var(--muted);
  margin: 0;
}

/* 弹窗 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.42);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
  padding: 20px;
}
.modal {
  background: var(--white);
  border: 1px solid var(--light-grey);
  border-radius: 14px;
  width: 100%;
  max-width: 620px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.18);
}
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--light-grey);
}
.modal-header h2 {
  font-size: 16px;
  font-weight: 700;
  color: var(--dark-text);
  margin: 0;
}
.modal-close {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--muted);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  font-family: inherit;
}
.modal-close:hover:not(:disabled) {
  background: var(--lighter-grey);
  color: var(--dark-text);
}
.modal-close:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}
.modal-hint {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--muted);
  line-height: 1.5;
}
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid var(--light-grey);
}

/* 编辑模式加宽弹窗 */
.modal.modal-wide {
  max-width: 880px;
}

/* 编辑弹窗三 Tab */
.edit-tabs {
  display: flex;
  gap: 4px;
  padding: 10px 20px 0;
  border-bottom: 1px solid var(--light-grey);
}
.edit-tab {
  padding: 8px 16px;
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  font-size: 13px;
  font-weight: 600;
  color: var(--muted);
  cursor: pointer;
  font-family: inherit;
  transition: all 0.15s;
}
.edit-tab:hover {
  color: var(--main-orange);
}
.edit-tab.active {
  color: var(--main-orange);
  border-bottom-color: var(--main-orange);
}

.edit-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* SKILL.md / 文件编辑的等宽字体 textarea */
.code-textarea {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  min-height: 240px;
}

/* Tab 内底部操作栏 */
.edit-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}
.char-count {
  font-size: 11px;
  color: var(--muted);
  margin-right: auto;
}

/* 相关文件：左列表 + 右编辑器 */
.files-layout {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 12px;
  min-height: 320px;
}
.files-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  border: 1px solid var(--light-grey);
  border-radius: 10px;
  padding: 8px;
  overflow-y: auto;
  max-height: 420px;
}
.files-list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  font-weight: 600;
  color: var(--body-text);
  padding: 2px 4px 6px;
  border-bottom: 1px solid var(--light-grey);
  margin-bottom: 4px;
}
.files-hint {
  font-size: 12px;
  color: var(--muted);
  padding: 12px 4px;
}
.file-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 6px 8px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  transition: all 0.15s;
}
.file-item:hover {
  background: var(--lighter-grey);
}
.file-item.active {
  background: var(--very-light-orange);
  border-color: var(--main-orange);
}
.file-name {
  font-size: 12px;
  color: var(--dark-text);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}
.file-size {
  font-size: 10px;
  color: var(--muted);
  flex-shrink: 0;
}

.file-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}
.file-editor-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.file-path {
  font-size: 12px;
  font-weight: 600;
  color: var(--dark-text);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.file-path-input {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
.file-editor-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed var(--light-grey);
  border-radius: 10px;
  font-size: 12px;
  color: var(--muted);
  min-height: 280px;
}

/* ===== 安全扫描 Tab ===== */
.scan-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.scan-time {
  font-size: 12px;
  color: var(--muted);
}
.scan-rescan-btn {
  margin-left: auto;
}
.scan-findings-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow-y: auto;
  max-height: 400px;
}
.scan-finding-item {
  border: 1px solid var(--light-grey);
  border-left: 3px solid var(--muted);
  border-radius: 8px;
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.scan-finding-item.sev-critical {
  border-left-color: #dc2626;
  background: rgba(245, 63, 63, 0.04);
}
.scan-finding-item.sev-high {
  border-left-color: #f97316;
}
.scan-finding-item.sev-medium {
  border-left-color: #eab308;
}
.scan-finding-item.sev-low {
  border-left-color: #3b82f6;
}
.scan-finding-item.sev-info {
  border-left-color: var(--muted);
}
.scan-finding-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.scan-finding-sev {
  font-size: 11px;
  font-weight: 700;
  color: var(--dark-text);
}
.scan-finding-id {
  font-size: 12px;
  font-weight: 600;
  color: var(--main-orange);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
.scan-finding-loc {
  font-size: 11px;
  color: var(--muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
.scan-finding-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--dark-text);
}
.scan-finding-desc {
  font-size: 12px;
  color: var(--body-text);
  line-height: 1.5;
}
.scan-finding-fix {
  font-size: 11px;
  color: var(--muted);
  line-height: 1.5;
}
.scan-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed var(--light-grey);
  border-radius: 10px;
  padding: 40px 16px;
  font-size: 12px;
  color: var(--muted);
  min-height: 200px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 16px;
}
.form-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.form-group.full-width {
  grid-column: 1 / -1;
}
.form-group.form-row-inline {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}
.form-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--body-text);
}
.form-input,
.form-textarea {
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--light-grey);
  border-radius: 8px;
  font-size: 13px;
  color: var(--dark-text);
  outline: none;
  font-family: inherit;
  transition: all 0.15s;
  background: var(--white);
  box-sizing: border-box;
}
.form-textarea {
  height: auto;
  padding: 8px 10px;
  resize: vertical;
  min-height: 60px;
  line-height: 1.5;
}
.form-input:focus,
.form-textarea:focus {
  border-color: var(--main-orange);
  box-shadow: 0 0 0 3px rgba(65, 118, 230, 0.1);
}
.form-input:disabled,
.form-textarea:disabled {
  background: var(--lighter-grey);
  color: var(--muted);
  cursor: not-allowed;
}

/* ===== 响应式适配 ===== */

/* 中等屏幕：grid 缩小最小宽度 */
@media (max-width: 1024px) {
  .skills-page {
    padding: 16px 18px;
  }
  .skill-grid {
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 12px;
  }
  .page-header {
    flex-direction: column;
    align-items: stretch;
  }
  .header-actions {
    justify-content: flex-end;
  }
  .skill-filter-bar {
    flex-direction: column;
    gap: 8px;
  }
  .skill-search-input,
  .skill-status-filter {
    width: 100%;
    min-width: unset;
  }
  .category-tabs {
    overflow-x: auto;
    flex-wrap: nowrap;
    -webkit-overflow-scrolling: touch;
  }
  .cat-tab {
    white-space: nowrap;
    flex-shrink: 0;
  }
}

/* 小屏幕：单列布局 */
@media (max-width: 720px) {
  .skills-page {
    padding: 12px 14px;
    gap: 12px;
  }
  .page-title {
    font-size: 17px;
  }
  .page-desc {
    font-size: 11px;
  }
  .btn-primary,
  .btn-secondary {
    font-size: 12px;
    padding: 7px 13px;
  }
  .skill-grid {
    grid-template-columns: 1fr;
    gap: 10px;
  }
  .skill-card {
    min-height: auto;
    padding: 14px;
  }
  .skill-header {
    gap: 10px;
  }
  .skill-icon-wrap {
    width: 36px;
    height: 36px;
  }
  .skill-icon {
    font-size: 17px;
  }
  .skill-name {
    font-size: 13px;
  }
  .skill-footer {
    flex-wrap: wrap;
    gap: 6px;
  }
  .skill-btn span {
    display: none;
  }
  /* 弹窗全屏 */
  .modal {
    max-height: 100vh;
    border-radius: 0;
    margin: 0;
  }
  .modal-header {
    padding: 12px 16px;
  }
  .modal-body {
    padding: 12px 16px;
  }
  .modal-footer {
    padding: 10px 16px;
  }
  .form-grid {
    grid-template-columns: 1fr;
  }
  /* 分页居中 */
  .skill-pagination {
    justify-content: center;
  }
}

/* 超小屏幕 */
@media (max-width: 480px) {
  .skills-page {
    padding: 10px 12px;
    gap: 10px;
  }
  .page-kicker {
    font-size: 10px;
  }
  .page-title {
    font-size: 15px;
  }
  .category-tabs {
    padding: 8px 10px;
    gap: 6px;
  }
  .cat-tab {
    padding: 5px 10px;
    font-size: 11px;
  }
  .cat-count {
    display: none;
  }
}
</style>
