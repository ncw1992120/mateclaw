<template>
  <div class="skills-page">
    <!-- 固定头部：身份页头 + 筛选行，滚动时保持可见 -->
    <div class="skills-fixed">
      <!-- 内容区身份页头：标题 + 一句描述（左） + 主操作（右）；页面级页头已并入侧栏，内容区自带身份锚点 -->
      <div class="skill-content-header">
        <div class="skill-content-title">
          <div class="skill-content-name-row">
            <h3 class="skill-content-name">{{ t('configCenter.tabSkill') }}</h3>
            <!-- 启停数量标识：常驻标题旁，色彩编码与卡片状态 chip 一致；数据与当前筛选同源 -->
            <span v-if="statsReady" class="skill-title-stats">
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
          <p class="skill-content-desc">{{ t('configCenter.skillDesc') }}</p>
        </div>
        <div class="header-actions">
          <el-button v-if="hasPermission(PERMISSION.SKILL_MANAGE)" @click="openImportDialog">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
              <polyline points="7 10 12 15 17 10" />
              <line x1="12" y1="15" x2="12" y2="3" />
            </svg>
            {{ t('skillManage.import') }}
          </el-button>
          <el-button v-if="hasPermission(PERMISSION.SKILL_MANAGE)" type="primary" :icon="Plus" @click="openCreateModal">
            {{ t('skillManage.newSkill') }}
          </el-button>
        </div>
      </div>

      <!-- 筛选行：分类胶囊（左） + 搜索 + 排序（右），单行收纳 -->
      <div class="skill-toolbar">
        <div class="category-tabs">
          <button
            v-for="tab in categoryTabs"
            :key="tab.value"
            type="button"
            class="cat-tab"
            :class="{ active: isTabActive(tab) }"
            @click="onTabChange(tab)"
          >
            <span class="cat-dot" :class="tab.dot"></span>
            <span>{{ tab.label }}</span>
            <span class="cat-count">{{ getCategoryCount(tab) }}</span>
          </button>
        </div>
        <!-- 视图控件组：搜索（唯一胶囊） + 排序（安静文字式） + 刷新（图标按钮），共同操纵当前列表视图 -->
        <div class="view-controls">
          <el-input
            v-model="query.keyword"
            class="skill-search-input"
            :prefix-icon="Search"
            clearable
            :placeholder="t('skillManage.searchPlaceholder')"
            @keyup.enter="onFilterChange"
            @clear="onFilterChange"
          />
          <!-- 排序：el-dropdown，触发器为纯图标幽灵按钮；非默认排序时右下角主题色圆点，title 提示当前值 -->
          <el-dropdown class="sort-dropdown" trigger="click" @command="onSortChange">
            <button
              type="button"
              class="sort-icon-btn"
              :class="{ active: query.sort !== 'recommended' }"
              :title="`${t('skillManage.sortBy')}: ${currentSortLabel}`"
            >
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M7 4v13" />
                <path d="M4 14l3 3 3-3" />
                <path d="M17 20V7" />
                <path d="M14 10l3-3 3 3" />
              </svg>
              <span v-if="query.sort !== 'recommended'" class="sort-dot" aria-hidden="true"></span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="opt in sortOptions"
                  :key="opt.value"
                  :command="opt.value"
                  :icon="query.sort === opt.value ? Check : undefined"
                >
                  {{ opt.label }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <!-- 刷新：从页头下移的图标按钮，加载时图标旋转 -->
          <button
            type="button"
            class="refresh-btn"
            :class="{ loading }"
            :disabled="loading"
            :title="t('skillManage.refresh')"
            @click="loadAll"
          >
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="23 4 23 10 17 10" />
              <polyline points="1 20 1 14 7 14" />
              <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
            </svg>
          </button>
        </div>
      </div>
    </div>

    <!-- 可滚动列表区 -->
    <div class="skills-scroll">
      <!-- 全局空态：搜索/筛选未命中或无技能时展示 -->
      <div v-if="!loading && !hasAnyResult" class="global-empty surface-card">
        <div class="global-empty-icon">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <polyline points="22 12 16 12 14 15 10 15 8 12 2 12" />
            <path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z" />
          </svg>
        </div>
        <h3>{{ isFiltering ? t('skillManage.emptyFilterTitle') : t('skillManage.emptyNoneTitle') }}</h3>
        <p>{{ isFiltering ? t('skillManage.emptyFilterDesc') : t('skillManage.emptyNoneDesc') }}</p>
        <button v-if="isFiltering" type="button" class="clear-filter-btn" @click="clearFilters">
          {{ t('skillManage.clearFilters') }}
        </button>
      </div>

      <!-- 单列表：启停不再分段，状态由卡片上的状态 chip 表达；单一分页 -->
      <div v-else ref="skillListRef" class="skill-grid">
        <div
          v-for="skill in list.items"
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

          <!-- 描述：两行截断；仅当实际被截断时悬停弹出完整文案（对齐报告页 card-desc-tooltip 风格） -->
          <el-tooltip
            :content="skill.description"
            placement="top"
            :show-after="150"
            :disabled="!truncatedDescs[skill.id]"
            popper-class="card-desc-tooltip"
          >
            <p class="skill-desc" :data-id="skill.id">
              {{ skill.description || t('skillManage.noDescription') }}
            </p>
          </el-tooltip>

          <!-- 标签：内容描述的一部分，紧跟描述；等高卡的空白只落在下方底栏之上 -->
          <div v-if="parseTags(skill.tags).length > 0" class="skill-tags">
            <span v-for="tag in parseTags(skill.tags)" :key="tag" class="skill-tag">
              {{ tag }}
            </span>
          </div>

          <!-- 底栏：margin-top:auto 沉底；状态 chip 领衔 + 元信息（来源·版本·作者） + 右侧操作 -->
          <div class="skill-footer" @click.stop>
            <span class="skill-status" :class="{ off: !skill.enabled }">
              <span class="skill-status-dot" aria-hidden="true"></span>
              {{ skill.enabled ? t('skillManage.sectionEnabled') : t('skillManage.sectionAvailable') }}
            </span>
            <span class="skill-meta-line" :title="metaLabel(skill)">{{ metaLabel(skill) }}</span>
            <!-- 底栏操作：参考报告页 card-action-btn 的图标+文字轻量按钮 -->
            <div class="skill-actions">
              <button
                v-if="hasPermission(PERMISSION.SKILL_MANAGE)"
                type="button"
                class="skill-action-btn"
                @click.stop="openEditFromCard(skill)"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                  <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
                </svg>
                {{ t('skillManage.edit') }}
              </button>
              <button
                v-if="skill.builtin !== true && hasPermission(PERMISSION.SKILL_MANAGE)"
                type="button"
                class="skill-action-btn action-delete"
                @click.stop="handleDelete(skill)"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="3 6 5 6 21 6" />
                  <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                </svg>
                {{ t('skillManage.delete') }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 单一分页 -->
      <div v-if="!loading && list.total > 0" class="skill-pagination">
        <el-pagination
          v-model:current-page="list.page"
          v-model:page-size="list.size"
          :page-sizes="[12, 20, 50]"
          :total="list.total"
          layout="prev, pager, next, sizes, total"
          background
          @current-change="loadSkills"
          @size-change="onPageSizeChange"
        />
      </div>
    </div>

    <!-- 新建/编辑技能弹窗 -->
    <div v-if="showModal" class="modal-overlay" @click.self="closeModal">
      <div class="modal" :class="{ 'modal-wide': editingSkillId != null }">
        <div class="modal-header">
          <h2>{{ editingSkillId != null ? t('skillManage.editTitle') : t('skillManage.create') }}</h2>
          <button class="modal-close" :disabled="submitting" @click="closeModal">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        <!-- 编辑态 Tab：基础信息 / SKILL.md / 相关文件 / 安全扫描 -->
        <div v-if="editingSkillId != null" class="edit-tabs">
          <button
            v-for="et in editTabs"
            :key="et.value"
            type="button"
            class="edit-tab"
            :class="{ active: activeEditTab === et.value }"
            @click="switchEditTab(et.value)"
          >
            {{ et.label }}
          </button>
        </div>

        <div class="modal-body">
          <!-- ===== Tab: 基础信息 ===== -->
          <div v-if="activeEditTab === 'basic'" class="edit-section">
            <p class="modal-hint">{{ editingSkillId != null ? t('skillManage.editHint') : t('skillManage.createHint') }}</p>
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
              class="form-textarea code-textarea"
              rows="16"
              :placeholder="t('skillManage.skillMdPlaceholder')"
              :disabled="savingSkillMd"
            />
            <div class="edit-footer">
              <span class="char-count">{{ t('skillManage.charCount', { n: skillMdContent.length }) }}</span>
              <el-button
                type="primary"
                size="small"
                :disabled="savingSkillMd || skillMdContent === (editingSkill?.skillContent || '')"
                :loading="savingSkillMd"
                @click="saveSkillMd"
              >
                {{ t('common.confirm') }}
              </el-button>
            </div>
          </div>

          <!-- ===== Tab: 相关文件 ===== -->
          <div v-else-if="activeEditTab === 'files'" class="edit-section">
            <div class="files-layout">
              <!-- 左侧：文件列表 -->
              <div class="files-list">
                <div class="files-list-head">
                  <span class="files-title">{{ t('skillManage.filesTitle') }}</span>
                  <button type="button" class="file-new-btn" :disabled="creatingFile" @click="startNewFile">
                    {{ t('skillManage.fileNew') }}
                  </button>
                </div>
                <div v-if="filesLoading" class="files-hint">{{ t('common.loading') }}</div>
                <div v-else-if="skillFiles.length === 0" class="files-hint">{{ t('skillManage.filesEmpty') }}</div>
                <button
                  v-for="f in skillFiles"
                  v-else
                  :key="f.filePath"
                  type="button"
                  class="file-item"
                  :class="{ active: !creatingFile && activeFilePath === f.filePath }"
                  @click="openSkillFile(f)"
                >
                  <span class="file-name">{{ f.filePath }}</span>
                  <span class="file-size">{{ formatFileSize(f.contentSize) }}</span>
                </button>
              </div>

              <!-- 右侧：文件编辑器 -->
              <div class="file-editor">
                <template v-if="creatingFile">
                  <div class="file-editor-head">
                    <input
                      v-model="newFilePath"
                      class="form-input file-path-input"
                      :placeholder="t('skillManage.filePathPlaceholder')"
                      :disabled="savingFile"
                    />
                  </div>
                  <textarea
                    v-model="activeFileContent"
                    class="form-textarea code-textarea"
                    rows="14"
                    :placeholder="t('skillManage.skillMdPlaceholder')"
                    :disabled="savingFile"
                  />
                  <div class="edit-footer">
                    <el-button size="small" :disabled="savingFile" @click="closeFileEditor">
                      {{ t('common.cancel') }}
                    </el-button>
                    <el-button type="primary" size="small" :disabled="savingFile" :loading="savingFile" @click="saveActiveFile">
                      {{ t('common.confirm') }}
                    </el-button>
                  </div>
                </template>

                <template v-else-if="activeFilePath">
                  <div class="file-editor-head">
                    <span class="file-path">{{ activeFilePath }}</span>
                    <span v-if="activeFileSize != null" class="file-size">{{ formatFileSize(activeFileSize) }}</span>
                  </div>
                  <div v-if="fileContentLoading" class="file-editor-empty">{{ t('common.loading') }}</div>
                  <template v-else>
                    <textarea
                      v-model="activeFileContent"
                      class="form-textarea code-textarea"
                      rows="14"
                      :disabled="savingFile"
                    />
                    <div class="edit-footer">
                      <el-button
                        type="primary"
                        size="small"
                        :disabled="savingFile"
                        :loading="savingFile"
                        @click="saveActiveFile"
                      >
                        {{ t('common.confirm') }}
                      </el-button>
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
                type="button"
                class="scan-rescan-btn"
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

        <!-- 底部操作：仅基础信息 Tab 展示（其余 Tab 自带保存动作） -->
        <div v-if="activeEditTab === 'basic'" class="modal-footer">
          <el-button :disabled="submitting" @click="closeModal">
            {{ t('common.cancel') }}
          </el-button>
          <el-button type="primary" :disabled="!form.name || submitting" :loading="submitting" @click="handleSubmit">
            {{ submitting ? t('common.loading') : t('common.confirm') }}
          </el-button>
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
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, ElPagination } from 'element-plus'
import { Plus, Search, Check } from '@element-plus/icons-vue'
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

/** 单列表分页状态（启停不再分段，状态由卡片状态 chip 表达） */
interface SkillListState {
  items: Skill[]
  total: number
  page: number
  size: number
}
const list = reactive<SkillListState>({ items: [], total: 0, page: 1, size: 12 })

/** 当前筛选下的启停分布：额外一次 size=1 的 enabled 查询取总数，差值得到未启用数 */
const statusCounts = reactive({ enabled: 0, disabled: 0 })
/** 计数是否已就绪：首次加载完成后才在标题旁显示数量，避免首帧 0/0 闪烁 */
const statsReady = ref(false)

/** 分类 Tab 计数（按后端 page 总数） */
const counts = ref<Record<string, number>>({})

/** 整体加载态 */
const loading = ref(false)
/** 提交中 */
const submitting = ref(false)
/** 切换中 */
const togglingId = ref<number | null>(null)

/** 列表容器 ref：用于测量描述截断状态 */
const skillListRef = ref<HTMLElement | null>(null)

/** 描述被截断的技能 id 集合：仅截断的卡片悬停时弹出完整描述（对齐报告页 card-desc-tooltip 行为） */
const truncatedDescs = ref<Record<string, boolean>>({})

/** 测量各卡片描述是否被两行 line-clamp 截断（scrollHeight 超出可视高度即为截断） */
function measureDescTruncation(): void {
  const root = skillListRef.value
  if (!root) return
  const map: Record<string, boolean> = {}
  root.querySelectorAll<HTMLElement>('.skill-desc').forEach((el) => {
    const id = el.dataset.id
    if (id) map[id] = el.scrollHeight > el.clientHeight
  })
  truncatedDescs.value = map
}

/** 窗口尺寸变化会改变卡片宽度与换行数，需重新测量 */
function handleWindowResize(): void {
  measureDescTruncation()
}

/** 筛选条件 */
const query = reactive({
  keyword: '',
  skillType: 'all' as string,
  sort: 'recommended' as string,
  lifecycleState: '' as string,
})

/** 弹窗 */
const showModal = ref(false)

// ==================== 编辑弹窗多 Tab 状态 ====================

/** 编辑中的技能 ID（null 表示新建模式：仅展示基础信息 Tab） */
const editingSkillId = ref<number | null>(null)

/** 编辑中的技能详情实体（含 SKILL.md 正文与安全扫描结果） */
const editingSkill = ref<Skill | null>(null)

/** 编辑弹窗当前 Tab：basic / skillmd / files / security */
const activeEditTab = ref<'basic' | 'skillmd' | 'files' | 'security'>('basic')

/** 编辑态 Tab 定义 */
const editTabs = computed(() => [
  { value: 'basic' as const, label: t('skillManage.tabBasic') },
  { value: 'skillmd' as const, label: t('skillManage.tabSkillmd') },
  { value: 'files' as const, label: t('skillManage.tabFiles') },
  { value: 'security' as const, label: t('skillManage.tabSecurity') },
])

/** SKILL.md 正文编辑缓冲 */
const skillMdContent = ref('')
/** SKILL.md 保存中 */
const savingSkillMd = ref(false)

/** 技能 bundle 文件列表（references/、scripts/） */
const skillFiles = ref<SkillFileView[]>([])
/** 文件列表加载中 */
const filesLoading = ref(false)

/** 当前打开的文件路径（空表示未选择） */
const activeFilePath = ref('')
/** 当前打开文件的大小（字节） */
const activeFileSize = ref<number | null>(null)
/** 当前打开文件的正文编辑缓冲 */
const activeFileContent = ref('')
/** 文件正文加载中 */
const fileContentLoading = ref(false)
/** 文件保存中 */
const savingFile = ref(false)

/** 新建文件模式 */
const creatingFile = ref(false)
/** 新建文件路径输入 */
const newFilePath = ref('')

/** 切换编辑 Tab：首次进入相关文件 Tab 时懒加载文件列表 */
function switchEditTab(tab: 'basic' | 'skillmd' | 'files' | 'security'): void {
  activeEditTab.value = tab
  if (tab === 'files' && skillFiles.value.length === 0 && !filesLoading.value) {
    loadSkillFiles()
  }
}

/** 保存 SKILL.md 正文（复用技能更新接口的 skillContent 字段） */
async function saveSkillMd(): Promise<void> {
  if (editingSkillId.value == null || savingSkillMd.value) return
  savingSkillMd.value = true
  try {
    const updated = await skillApi.update(editingSkillId.value, { skillContent: skillMdContent.value })
    if (updated) {
      editingSkill.value = updated
    }
    ElMessage.success(t('skillManage.saveSuccess'))
  } catch {
    // 错误由拦截器处理
  } finally {
    savingSkillMd.value = false
  }
}

/** 拉取技能 bundle 文件列表 */
async function loadSkillFiles(): Promise<void> {
  if (editingSkillId.value == null) return
  filesLoading.value = true
  try {
    skillFiles.value = await skillApi.listFiles(editingSkillId.value)
  } catch {
    skillFiles.value = []
  } finally {
    filesLoading.value = false
  }
}

/** 打开指定文件：拉取正文进入编辑态 */
async function openSkillFile(file: SkillFileView): Promise<void> {
  if (editingSkillId.value == null || file.filePath == null) return
  creatingFile.value = false
  activeFilePath.value = file.filePath
  activeFileSize.value = file.contentSize ?? null
  activeFileContent.value = ''
  fileContentLoading.value = true
  try {
    const detail = await skillApi.getFileContent(editingSkillId.value, file.filePath)
    activeFileContent.value = detail?.content ?? ''
    activeFileSize.value = detail?.contentSize ?? file.contentSize ?? null
  } catch {
    activeFileContent.value = ''
  } finally {
    fileContentLoading.value = false
  }
}

/** 进入新建文件模式：清空右侧编辑器 */
function startNewFile(): void {
  creatingFile.value = true
  newFilePath.value = ''
  activeFilePath.value = ''
  activeFileSize.value = null
  activeFileContent.value = ''
}

/** 退出新建文件模式，回到未选择状态 */
function closeFileEditor(): void {
  creatingFile.value = false
  newFilePath.value = ''
  activeFilePath.value = ''
  activeFileSize.value = null
  activeFileContent.value = ''
}

/** 保存当前编辑（或新建）的 bundle 文件 */
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
    if (creatingFile.value) {
      // 新建成功：退出新建态并选中新文件
      creatingFile.value = false
      await loadSkillFiles()
      activeFilePath.value = path
    }
  } catch {
    // 错误由拦截器处理
  } finally {
    savingFile.value = false
  }
}

/** 文件大小展示：字节 → 可读单位 */
function formatFileSize(size: number | null | undefined): string {
  if (size == null) return ''
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

// ==================== 安全扫描 Tab ====================

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
  if (editingSkillId.value == null || rescanning.value) return
  rescanning.value = true
  try {
    const updated = await skillApi.rescan(editingSkillId.value)
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

/** 排序选项（value 与后端 params.sort 对齐） */
const sortOptions = computed(() => [
  { value: 'recommended', label: t('skillManage.sort.recommended') },
  { value: 'name', label: t('skillManage.sort.name') },
  { value: 'status', label: t('skillManage.sort.status') },
  { value: 'type', label: t('skillManage.sort.source') },
  { value: 'updated', label: t('skillManage.sort.updated') },
])

/** 当前排序的展示文案（下拉触发器内联显示） */
const currentSortLabel = computed(
  () => sortOptions.value.find((o) => o.value === query.sort)?.label ?? t('skillManage.sort.recommended'),
)

/** 下拉菜单选中排序 */
function onSortChange(value: string | number | object): void {
  if (typeof value !== 'string' || value === query.sort) return
  query.sort = value
  onFilterChange()
}

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

/** 列表是否有结果（决定展示全局空态还是卡片列表） */
const hasAnyResult = computed(() => list.total > 0)

/** 是否存在筛选/搜索条件（区分全局空态的文案与操作） */
const isFiltering = computed(
  () => !!query.keyword.trim() || query.skillType !== 'all' || !!query.lifecycleState,
)

/** 清除全部筛选条件并重新加载 */
function clearFilters(): void {
  query.keyword = ''
  query.skillType = 'all'
  query.sort = 'recommended'
  query.lifecycleState = ''
  resetListPage()
  loadSkills()
}

/** 分类 Tab 定义：圆点颜色与卡片图标 tint 同一套色彩编码（内置=蓝 / MCP=紫 / 自定义=橙） */
const categoryTabs = computed(() => [
  { value: 'all', label: t('skillManage.tabAll'), dot: 'dot-all' },
  { value: 'builtin', label: t('skillManage.tabBuiltin'), dot: 'dot-builtin' },
  { value: 'mcp', label: t('skillManage.tabMcp'), dot: 'dot-mcp' },
  { value: 'dynamic', label: t('skillManage.tabDynamic'), dot: 'dot-custom' },
])

function getCategoryCount(tab: { value: string }): number {
  return counts.value[tab.value] ?? 0
}

function isTabActive(tab: { value: string }): boolean {
  return !query.lifecycleState && query.skillType === tab.value
}

onMounted(() => {
  console.log('[SkillManage] 初始化, workspaceId:', currentWorkspaceId.value)
  window.addEventListener('resize', handleWindowResize)
  loadAll()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleWindowResize)
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

function resetListPage(): void {
  list.page = 1
}

function onTabChange(tab: { value: string }): void {
  query.skillType = tab.value
  query.lifecycleState = ''
  resetListPage()
  loadSkills()
}

function onFilterChange(): void {
  resetListPage()
  loadSkills()
}

/** 单页条数变化：回到第一页再加载，避免页码越界 */
function onPageSizeChange(): void {
  list.page = 1
  loadSkills()
}

async function loadSkills(): Promise<void> {
  const params: Record<string, unknown> = {
    page: list.page,
    size: list.size,
    workspaceId: currentWorkspaceId.value,
  }
  if (query.keyword) params.keyword = query.keyword.trim()
  if (query.skillType && query.skillType !== 'all') params.skillType = query.skillType
  if (query.sort && query.sort !== 'recommended') params.sort = query.sort
  if (query.lifecycleState) params.lifecycleState = query.lifecycleState

  loading.value = true
  try {
    // 计数请求与列表请求并行：同一筛选条件 + enabled=true、size=1，只取 total
    const countParams: Record<string, unknown> = { ...params, page: 1, size: 1, enabled: true }
    const [data, enabledData] = await Promise.all([
      skillApi.page(params as Parameters<typeof skillApi.page>[0]),
      skillApi.page(countParams as Parameters<typeof skillApi.page>[0]).catch(() => null),
    ])
    list.items = data?.records || []
    list.total = Number(data?.total) || 0
    if (enabledData) {
      const enabledTotal = Number(enabledData.total) || 0
      statusCounts.enabled = enabledTotal
      statusCounts.disabled = Math.max(0, list.total - enabledTotal)
    }
  } catch (err) {
    console.error('[SkillManage] loadSkills failed:', err)
    list.items = []
    list.total = 0
    statusCounts.enabled = 0
    statusCounts.disabled = 0
  } finally {
    loading.value = false
    statsReady.value = true
  }
  // 渲染完成后测量描述截断状态，驱动 tooltip 是否启用
  await nextTick()
  measureDescTruncation()
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
    skillMdContent.value = skill.skillContent || ''
    showModal.value = true
  }
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
    // 编辑模式按当前技能 ID 更新；新建模式创建
    if (editingSkillId.value != null) {
      await skillApi.update(editingSkillId.value, payload as Partial<Skill>)
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

async function handleToggle(skill: Skill, enabled: boolean): Promise<void> {
  togglingId.value = skill.id
  try {
    await skillApi.toggle(skill.id, enabled)
    // 启停切换后卡片状态 chip 更新，重载列表
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
    builtin: 'card-theme-blue',
    mcp: 'card-theme-violet',
    custom: 'card-theme-orange',
  }
  return map[type] || 'card-theme-grey'
}

/** 源标签：builtin / mcp / 自定义 */
function getSourceLabel(skill: Skill): string {
  if (skill.builtin === true) return t('skillManage.sourceBuiltin')
  if (skill.skillType === 'mcp') return 'MCP'
  if (skill.skillType === 'builtin') return t('skillManage.sourceBuiltin')
  return t('skillManage.sourceCustom')
}

/** 底栏合并元信息行：来源 · 版本 · 作者，单行安静文本（超长省略，title 显示全文） */
function metaLabel(skill: Skill): string {
  const parts: string[] = [getSourceLabel(skill)]
  if (skill.version) parts.push(`v${skill.version}`)
  if (skill.author) parts.push(skill.author)
  return parts.join(' · ')
}
</script>

<style scoped>
.skills-page {
  display: flex;
  flex-direction: column;
  gap: 0;
  width: 100%;
  /* 父容器为块级滚动容器，用 height:100% 取得确定高度，内部 .skills-scroll 才能独立滚动、头部保持固定 */
  height: 100%;
  min-height: 0;
  /* 透明底：配置壳纸面卡片已提供工作区层级，子视图不再自带灰底与外边距 */
  background: transparent;
  box-sizing: border-box;
  overflow: hidden;
}

/* 固定头部：身份页头 + 筛选行，滚动时保持可见 */
.skills-fixed {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-bottom: 14px;
}

/* 可滚动列表区：横向负 margin + 等值内 padding，为卡片 hover 阴影/焦点环留出溢出余量，同时保持与固定头部对齐 */
.skills-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 10px 8px 24px;
  margin: 0 -8px;
}

/* 内容区身份页头：标题/描述靠左，主操作靠右，单行对齐 */
.skill-content-header {
  display: flex;
  align-items: center;
  gap: 16px;
}

.skill-content-title {
  min-width: 0;
}

.skill-content-name {
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

/* 描述：上下间距用 margin（line-clamp 元素加垂直 padding 会漏绘裁切行） */
.skill-content-desc {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--db-text-muted);
  line-height: 18px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 筛选行：分类胶囊靠左，搜索 + 排序靠右，单行收纳；窄屏换行 */
.skill-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  margin-left: auto;
  flex-shrink: 0;
}

/* 页头操作区：与洞察/报告页同一套 Element 胶囊按钮皮肤 */
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

.header-actions :deep(.el-button:not(.el-button--primary)) {
  background: var(--db-card);
  border-color: var(--db-border-strong);
  color: var(--db-text-secondary);
}

.header-actions :deep(.el-button:not(.el-button--primary):hover) {
  border-color: var(--main-orange);
  color: var(--main-orange);
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

/* 分类 Tab：灰底分段胶囊容器（次级控件，内 3px 衬住激活项） */
.category-tabs {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px;
  border-radius: 999px;
  background: var(--db-bg);
  max-width: 100%;
  overflow-x: auto;
  scrollbar-width: none;
}
.category-tabs::-webkit-scrollbar {
  display: none;
}

.cat-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 13px;
  border: 1px solid transparent;
  background: transparent;
  border-radius: 999px;
  font-size: 13px;
  color: var(--db-text-secondary);
  cursor: pointer;
  font-family: inherit;
  font-weight: 500;
  white-space: nowrap;
  flex-shrink: 0;
  transition: color var(--transition-fast, 0.15s), background var(--transition-fast, 0.15s), border-color var(--transition-fast, 0.15s), box-shadow var(--transition-fast, 0.15s);
}
.cat-tab:hover:not(.active) {
  color: var(--db-text);
  background: color-mix(in srgb, var(--db-text-muted) 8%, transparent);
}
.cat-tab.active {
  background: var(--theme-surface-elevated);
  border-color: color-mix(in srgb, var(--main-orange) 40%, transparent);
  color: var(--main-orange);
  font-weight: 600;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}
/* 来源圆点：与卡片图标 tint 同一套色彩编码，筛选与卡片图标互为索引 */
.cat-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.cat-dot.dot-all { background: var(--db-text-muted); }
.cat-dot.dot-builtin { background: var(--db-card-blue-fg); }
.cat-dot.dot-mcp { background: var(--db-card-violet-fg); }
.cat-dot.dot-custom { background: var(--db-card-orange-fg); }
.cat-count {
  background: color-mix(in srgb, var(--db-text-muted) 14%, transparent);
  color: var(--db-text-secondary);
  padding: 0 7px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  line-height: 16px;
}
.cat-tab.active .cat-count {
  background: color-mix(in srgb, var(--main-orange) 12%, transparent);
  color: var(--main-orange);
}

/* 视图控件组：flex:1 吸收行内剩余空间并右对齐，搜索框因此能撑开到可读宽度 */
.view-controls {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex: 1;
  min-width: 0;
  flex-wrap: wrap;
}

/* 搜索框：与洞察/报告页同一套 el-input 白底描边胶囊皮肤；min 220 保证占位符整行可见 */
.skill-search-input {
  flex: 1;
  min-width: 220px;
  max-width: 320px;
}

.skill-search-input :deep(.el-input__wrapper) {
  height: 36px;
  border-radius: 999px;
  background: var(--theme-surface-elevated);
  box-shadow: var(--shadow-sm), inset 0 0 0 1px var(--theme-border-strong);
  padding: 0 14px;
  transition: box-shadow var(--transition-fast, 0.15s), background var(--transition-fast, 0.15s);
}

.skill-search-input :deep(.el-input__inner) {
  font-size: 13px;
}

.skill-search-input :deep(.el-input__wrapper:hover) {
  box-shadow:
    var(--shadow-sm),
    inset 0 0 0 1px color-mix(in srgb, var(--main-orange) 45%, var(--theme-border-strong));
}

.skill-search-input :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 3px color-mix(in srgb, var(--main-orange) 14%, transparent),
    inset 0 0 0 1px var(--main-orange);
}

.skill-search-input :deep(.el-input__prefix) {
  color: var(--db-text-muted);
  transition: color var(--transition-fast, 0.15s);
}

.skill-search-input :deep(.el-input__wrapper.is-focus .el-input__prefix) {
  color: var(--main-orange);
}

/* 排序触发器：纯图标幽灵按钮，与刷新按钮成对；非默认排序时右下角主题色圆点 */
.sort-dropdown {
  flex-shrink: 0;
}

.sort-icon-btn {
  position: relative;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--db-text-secondary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  transition: color var(--transition-fast, 0.15s), background var(--transition-fast, 0.15s);
}

.sort-icon-btn:hover {
  color: var(--db-text);
  background: color-mix(in srgb, var(--db-text-muted) 8%, transparent);
}

.sort-icon-btn.active {
  color: var(--main-orange);
}

.sort-dot {
  position: absolute;
  right: 4px;
  bottom: 4px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--main-orange);
}

/* 刷新：图标幽灵按钮，从页头下移至此；加载时图标旋转 */
.refresh-btn {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--db-text-secondary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  transition: color var(--transition-fast, 0.15s), background var(--transition-fast, 0.15s);
}

.refresh-btn:hover:not(:disabled) {
  color: var(--db-text);
  background: color-mix(in srgb, var(--db-text-muted) 8%, transparent);
}

.refresh-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.refresh-btn.loading svg {
  animation: skill-refresh-spin 0.9s linear infinite;
}

@keyframes skill-refresh-spin {
  to {
    transform: rotate(360deg);
  }
}

/* 标题行：页面名称 + 启停数量标识同一基线，数量常驻标题旁 */
.skill-content-name-row {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

/* 启停数量标识：紧凑内联，色彩编码与卡片状态 chip 一致 */
.skill-title-stats {
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

/* 网格：280px 起配，适配侧栏占用后的窄内容区 */
.skill-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

/* 卡片：系统卡片家族 token（shadow-card 默认 / hover 上浮 + shadow-card-hover / 底栏 8px） */
.skill-card {
  padding: 16px 16px 14px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 168px;
  cursor: pointer;
  outline: none;
  box-shadow: var(--shadow-card);
  overflow: hidden;
  transition:
    box-shadow var(--transition-base),
    border-color var(--transition-fast),
    transform var(--transition-fast);
}
.skill-card:hover {
  border-color: var(--db-border-strong);
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}
.skill-card:focus-visible {
  border-color: var(--db-border-strong);
  box-shadow:
    var(--shadow-card-hover),
    0 0 0 3px color-mix(in srgb, var(--main-orange) 14%, transparent);
}
.skill-card.disabled {
  opacity: 0.62;
}

.skill-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 图标 chip：36px 锚点尺寸，在宽卡片中保持视觉权重；主题色由全局 tint 变量驱动（暗色模式自适配） */
.skill-icon-wrap {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--card-tint-bg);
  color: var(--card-tint-fg);
}
/* 技能类型主题：对齐 dashboard-design.css 的卡片 tint 变量对 */
.card-theme-blue { --card-tint-bg: var(--db-card-blue-bg); --card-tint-fg: var(--db-card-blue-fg); }
.card-theme-violet { --card-tint-bg: var(--db-card-violet-bg); --card-tint-fg: var(--db-card-violet-fg); }
.card-theme-orange { --card-tint-bg: var(--db-card-orange-bg); --card-tint-fg: var(--db-card-orange-fg); }
.card-theme-grey {
  --card-tint-bg: color-mix(in srgb, var(--db-text-muted) 10%, transparent);
  --card-tint-fg: var(--db-text-muted);
}
.skill-icon {
  font-size: 18px;
  line-height: 1;
}

.skill-meta {
  flex: 1;
  overflow: hidden;
  min-width: 0;
}
/* 名称：卡片唯一焦点，与 12px 正文 / 11px 元信息拉开层级 */
.skill-name {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--db-text);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.skill-slug {
  font-size: 11px;
  color: var(--db-text-muted);
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
.toggle-switch input:disabled + .toggle-slider {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 描述：正文区，两行截断；min-height 预留两行，单行描述卡与双行卡内容基线对齐 */
.skill-desc {
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

/* 元信息合并行：来源·版本·作者单行安静文本，flex:1 + 省略号收纳窄卡 */
.skill-meta-line {
  flex: 1;
  min-width: 0;
  font-size: 11px;
  color: var(--db-text-muted);
  letter-spacing: 0.02em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 状态 chip：元信息行领衔，圆点+文字；启用用 Element success 绿，未启用降级为安静灰 */
.skill-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 600;
  color: var(--el-color-success);
}
.skill-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}
.skill-status.off {
  color: var(--db-text-muted);
  font-weight: 500;
}

/* 标签：内容区的次级信息，chip 稍加体量与描述区分 */
.skill-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin: 0;
}
.skill-tag {
  padding: 3px 8px;
  background: color-mix(in srgb, var(--db-text-muted) 12%, transparent);
  color: var(--db-text-secondary);
  border-radius: 6px;
  font-size: 10px;
}

/* 底栏：margin-top:auto 沉底，等高卡的弹性空白只落在分隔线上方；元信息合并行（左） + 操作按钮（右） */
.skill-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  border-top: 1px solid var(--db-border);
  padding-top: 10px;
  margin-top: auto;
}
.skill-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}
/* 操作按钮：参考报告页 card-action-btn 的图标+文字轻量按钮（透明底、悬停着色） */
.skill-action-btn {
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
.skill-action-btn:hover {
  color: var(--db-text);
  background: var(--db-hover);
}
.skill-action-btn.action-delete {
  color: #ef4444;
}
.skill-action-btn.action-delete:hover {
  color: #dc2626;
  background: var(--db-danger-bg);
}

/* 分页 */
.skill-pagination {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}

/* 分页主题化：对齐 ChatView browse-pagination 的既有语言——描边胶囊页码、主题橙实心激活态、安静的辅助文字 */
.skill-pagination :deep(.el-pagination) {
  --el-pagination-font-size: 12px;
  --el-pagination-text-color: var(--db-text-secondary);
  --el-pagination-button-color: var(--db-text-secondary);
  --el-pagination-hover-color: var(--main-orange);
}

.skill-pagination :deep(.el-pager li),
.skill-pagination :deep(.btn-prev),
.skill-pagination :deep(.btn-next) {
  min-width: 26px;
  height: 26px;
  line-height: 26px;
  border-radius: 8px;
  background: var(--db-card);
  border: 1px solid var(--db-border);
  color: var(--db-text-secondary);
  font-weight: 500;
  margin: 0 3px;
  transition: color var(--transition-fast, 0.15s), border-color var(--transition-fast, 0.15s), background var(--transition-fast, 0.15s);
}

.skill-pagination :deep(.el-pager li:hover:not(.is-active)) {
  color: var(--main-orange);
  border-color: color-mix(in srgb, var(--main-orange) 45%, transparent);
  background: color-mix(in srgb, var(--main-orange) 6%, transparent);
}

.skill-pagination :deep(.el-pager li.is-active) {
  color: #fff;
  background: var(--main-orange);
  border-color: var(--main-orange);
  font-weight: 700;
}

.skill-pagination :deep(.btn-prev:hover:not(:disabled)),
.skill-pagination :deep(.btn-next:hover:not(:disabled)) {
  color: var(--main-orange);
  border-color: color-mix(in srgb, var(--main-orange) 45%, transparent);
  background: color-mix(in srgb, var(--main-orange) 6%, transparent);
}

.skill-pagination :deep(.btn-prev:disabled),
.skill-pagination :deep(.btn-next:disabled) {
  color: var(--db-text-muted);
  background: var(--db-card);
  opacity: 0.6;
}

.skill-pagination :deep(.el-pagination__total),
.skill-pagination :deep(.el-pagination__sizes) {
  color: var(--db-text-muted);
}

/* 每页条数下拉触发器：与页码同一描边胶囊形态 */
.skill-pagination :deep(.el-select__wrapper) {
  border-radius: 8px;
  box-shadow: inset 0 0 0 1px var(--db-border);
  background: var(--db-card);
  color: var(--db-text-secondary);
  min-height: 26px;
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
  margin: 0 0 6px;
}
.global-empty p {
  font-size: 12px;
  color: var(--db-text-muted);
  line-height: 18px;
  margin: 0;
}
.clear-filter-btn {
  margin-top: 14px;
  height: 30px;
  padding: 0 14px;
  border: 1px solid var(--db-border-strong);
  background: var(--db-card);
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--db-text-secondary);
  cursor: pointer;
  font-family: inherit;
  transition: border-color 0.15s, color 0.15s;
}
.clear-filter-btn:hover {
  border-color: var(--main-orange);
  color: var(--main-orange);
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
  background: var(--db-card);
  border: 1px solid var(--db-border);
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
  border-bottom: 1px solid var(--db-border);
}
.modal-header h2 {
  font-size: 16px;
  font-weight: 700;
  color: var(--db-text);
  margin: 0;
}
.modal-close {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--db-text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  font-family: inherit;
}
.modal-close:hover:not(:disabled) {
  background: color-mix(in srgb, var(--db-text-muted) 12%, transparent);
  color: var(--db-text);
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
  color: var(--db-text-muted);
  line-height: 1.5;
}
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid var(--db-border);
}

/* 弹窗底栏按钮：Element 皮肤，与列表页操作按钮同族（主色一致、尺寸适配弹窗） */
.modal-footer :deep(.el-button + .el-button) {
  margin-left: 0;
}

.modal-footer :deep(.el-button) {
  height: 34px;
  border-radius: 10px;
  padding: 0 16px;
  font-size: 13px;
  font-weight: 500;
}

.modal-footer :deep(.el-button:not(.el-button--primary)) {
  background: var(--db-card);
  border-color: var(--db-border-strong);
  color: var(--db-text-secondary);
}

.modal-footer :deep(.el-button:not(.el-button--primary):hover) {
  border-color: var(--main-orange);
  color: var(--main-orange);
}

.modal-footer :deep(.el-button--primary) {
  background: var(--main-orange);
  border-color: var(--main-orange);
}

.modal-footer :deep(.el-button--primary:hover),
.modal-footer :deep(.el-button--primary:focus) {
  background: var(--main-orange);
  border-color: var(--main-orange);
  filter: brightness(1.08);
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
  color: var(--db-text-secondary);
}
.form-input,
.form-textarea {
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--db-border);
  border-radius: 8px;
  font-size: 13px;
  color: var(--db-text);
  outline: none;
  font-family: inherit;
  transition: all 0.15s;
  background: var(--db-card);
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
  background: color-mix(in srgb, var(--db-text-muted) 12%, transparent);
  color: var(--db-text-muted);
  cursor: not-allowed;
}

/* ===== 编辑弹窗多 Tab ===== */
.modal.modal-wide {
  max-width: 860px;
}

.edit-tabs {
  display: flex;
  gap: 4px;
  padding: 10px 20px 0;
  border-bottom: 1px solid var(--db-border);
  flex-shrink: 0;
}

.edit-tab {
  padding: 8px 14px 10px;
  border: none;
  background: transparent;
  font-size: 13px;
  font-weight: 500;
  color: var(--db-text-muted);
  cursor: pointer;
  border-radius: 8px 8px 0 0;
  position: relative;
  font-family: inherit;
  transition: color 0.15s;
}

.edit-tab:hover {
  color: var(--db-text);
}

.edit-tab.active {
  color: var(--main-orange);
  font-weight: 600;
}

.edit-tab.active::after {
  content: '';
  position: absolute;
  left: 10px;
  right: 10px;
  bottom: -1px;
  height: 2px;
  border-radius: 2px;
  background: var(--main-orange);
}

.edit-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 320px;
}

/* ===== SKILL.md 编辑 ===== */
.code-textarea {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  min-height: 320px;
  white-space: pre;
}

.edit-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.char-count {
  margin-right: auto;
  font-size: 11px;
  color: var(--db-text-muted);
}

/* ===== 相关文件 Tab ===== */
.files-layout {
  display: flex;
  gap: 12px;
  flex: 1;
  min-height: 0;
}

.files-list {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
  border: 1px solid var(--db-border);
  border-radius: 10px;
  padding: 8px;
}

.files-list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 2px 4px 8px;
}

.files-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--db-text-secondary);
}

.file-new-btn {
  border: none;
  background: transparent;
  color: var(--main-orange);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 6px;
  font-family: inherit;
}

.file-new-btn:hover {
  background: color-mix(in srgb, var(--main-orange) 10%, transparent);
}

.file-new-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.files-hint {
  padding: 24px 8px;
  text-align: center;
  font-size: 12px;
  color: var(--db-text-muted);
}

.file-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  border: none;
  background: transparent;
  padding: 7px 9px;
  border-radius: 7px;
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  transition: background 0.15s;
}

.file-item:hover {
  background: color-mix(in srgb, var(--db-text-muted) 8%, transparent);
}

.file-item.active {
  background: color-mix(in srgb, var(--main-orange) 12%, transparent);
}

.file-name {
  font-size: 12px;
  color: var(--db-text);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-item.active .file-name {
  color: var(--main-orange);
  font-weight: 600;
}

.file-size {
  font-size: 10px;
  color: var(--db-text-muted);
  flex-shrink: 0;
}

.file-editor {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  border: 1px solid var(--db-border);
  border-radius: 10px;
  padding: 10px;
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
  color: var(--db-text);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-path-input {
  flex: 1;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.file-editor .code-textarea {
  flex: 1;
  min-height: 260px;
}

.file-editor-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed var(--db-border);
  border-radius: 10px;
  font-size: 12px;
  color: var(--db-text-muted);
  min-height: 260px;
}

/* ===== 安全扫描 Tab ===== */
.scan-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}

.status-pill.st-ready {
  background: rgba(34, 197, 94, 0.14);
  color: #16a34a;
}

.status-pill.st-disabled {
  background: color-mix(in srgb, var(--db-text-muted) 12%, transparent);
  color: var(--db-text-muted);
}

.status-pill.st-blocked {
  background: rgba(245, 63, 63, 0.14);
  color: #dc2626;
}

.scan-time {
  font-size: 12px;
  color: var(--db-text-muted);
}

.scan-rescan-btn {
  margin-left: auto;
  border: 1px solid var(--db-border-strong);
  background: var(--db-card);
  color: var(--db-text-secondary);
  font-size: 12px;
  font-weight: 600;
  padding: 5px 12px;
  border-radius: 8px;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.15s;
}

.scan-rescan-btn:hover:not(:disabled) {
  border-color: var(--main-orange);
  color: var(--main-orange);
}

.scan-rescan-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
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
  border: 1px solid var(--db-border);
  border-left: 3px solid var(--db-text-muted);
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
  border-left-color: var(--db-text-muted);
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
  color: var(--db-text);
}

.scan-finding-id {
  font-size: 12px;
  font-weight: 600;
  color: var(--main-orange);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.scan-finding-loc {
  font-size: 11px;
  color: var(--db-text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.scan-finding-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--db-text);
}

.scan-finding-desc {
  font-size: 12px;
  color: var(--db-text-secondary);
  line-height: 1.5;
}

.scan-finding-fix {
  font-size: 11px;
  color: var(--db-text-muted);
  line-height: 1.5;
}

.scan-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed var(--db-border);
  border-radius: 10px;
  padding: 40px 16px;
  font-size: 12px;
  color: var(--db-text-muted);
  min-height: 200px;
}

/* ===== 响应式适配 ===== */

/* 中等屏幕：grid 缩小最小宽度 */
@media (max-width: 1024px) {
  .skill-grid {
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    gap: 12px;
  }
  .skill-content-header {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }
  .header-actions {
    margin-left: 0;
    justify-content: flex-end;
  }
  .skill-toolbar {
    flex-direction: column;
    align-items: stretch;
  }
  .category-tabs {
    align-self: flex-start;
  }
  .skill-search-input {
    flex-basis: 100%;
    max-width: none;
  }
  .sort-icon-btn {
    margin-left: auto;
  }
}

/* 小屏幕：单列布局 */
@media (max-width: 720px) {
  /* 标题行允许换行：标题占满整行，启停数量落到第二行，避免标题被挤成单字省略 */
  .skill-content-name-row {
    flex-wrap: wrap;
    gap: 6px 14px;
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
    width: 26px;
    height: 26px;
  }
  .skill-icon {
    font-size: 14px;
  }
  .skill-name {
    font-size: 13px;
  }
  .skill-footer {
    flex-wrap: wrap;
    gap: 6px;
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
  .cat-tab {
    padding: 5px 10px;
    font-size: 11px;
  }
  .cat-count {
    display: none;
  }
}
</style>
