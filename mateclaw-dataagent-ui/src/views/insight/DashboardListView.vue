<template>
  <div class="dashboard-list-view">
    <!-- 列表模式 -->
    <template v-if="mode === 'list'">
      <div class="list-mode" :class="{ 'with-ai-panel': showAiPanel }">
      <!-- 悬浮纸面卡片：页头 + 搜索行 + Tab 行 + 列表，与灰底导航形成两层视觉层级 -->
      <div class="page-card">
      <!-- 页头：标题组（左） + 搜索与操作（右）同一行 -->
      <div class="list-header">
        <div class="list-title-group">
          <span class="list-title-icon">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 3v16a2 2 0 0 0 2 2h16"/><path d="m7 13 3-3 4 4 5-6"/></svg>
          </span>
          <div class="list-title-text">
            <h2 class="list-title">{{ t('insight.title') }}</h2>
            <p class="list-subtitle">{{ t('insight.headerSub', { count: store.dashboards.length }) }}</p>
          </div>
        </div>
        <div class="header-actions">
          <el-input
            v-model="searchKeyword"
            :placeholder="t('insight.searchPlaceholder')"
            :prefix-icon="Search"
            clearable
            class="search-input"
          />
          <el-button class="ai-assistant-btn" :class="{ on: showAiPanel }" @click="toggleAiPanel">
            <template #icon>
              <RobotIcon style="width: 16px; height: 16px;" />
            </template>
            {{ t('insight.aiAssistant') }}
          </el-button>
          <el-button v-if="canCreate" type="primary" :icon="Plus" @click="handleCreate">
            {{ t('insight.create') }}
          </el-button>
        </div>
      </div>
      <div class="list-body">
        <div class="list-content">
          <!-- 筛选行：状态 Tab（胶囊式，与报告页统一） / 排序 / 视图切换 -->
          <div class="filter-row">
            <div class="filter-tabs">
              <button
                type="button"
                class="filter-tab"
                :class="{ active: statusFilter === 'all' }"
                @click="statusFilter = 'all'"
              >
                {{ t('insight.filterAll') }}<span class="tab-cnt">{{ statusCounts.all }}</span>
              </button>
              <button
                type="button"
                class="filter-tab"
                :class="{ active: statusFilter === 'published' }"
                @click="statusFilter = 'published'"
              >
                <span class="tab-dot dot-published"></span>{{ t('insight.status.published') }}<span class="tab-cnt">{{ statusCounts.published }}</span>
              </button>
              <button
                type="button"
                class="filter-tab"
                :class="{ active: statusFilter === 'draft' }"
                @click="statusFilter = 'draft'"
              >
                <span class="tab-dot dot-draft"></span>{{ t('insight.status.draft') }}<span class="tab-cnt">{{ statusCounts.draft }}</span>
              </button>
            </div>
            <button type="button" class="filter-sort" @click="toggleSortOrder">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m3 8 4-4 4 4"/><path d="M7 4v16"/><path d="m21 16-4 4-4-4"/><path d="M17 20V4"/></svg>
              {{ sortOrder === 'desc' ? t('insight.sortByRecent') : t('insight.sortByOldest') }}
            </button>
            <div class="view-toggle">
              <button
                type="button"
                class="view-toggle-btn"
                :class="{ on: viewMode === 'grid' }"
                :title="t('insight.viewGrid')"
                @click="viewMode = 'grid'"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/></svg>
              </button>
              <button
                type="button"
                class="view-toggle-btn"
                :class="{ on: viewMode === 'list' }"
                :title="t('insight.viewList')"
                @click="viewMode = 'list'"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/></svg>
              </button>
            </div>
          </div>

          <div v-loading="store.loading" class="list-scroll">
          <div v-if="displayedDashboards.length === 0 && !store.loading" class="empty-state">
            <div class="empty-illustration">
              <svg width="80" height="80" viewBox="0 0 80 80" aria-hidden="true">
                <rect x="12" y="14" width="56" height="52" rx="10" fill="var(--db-muted)" opacity=".45"/>
                <rect x="22" y="38" width="9" height="20" rx="3" fill="var(--main-orange)" opacity=".55"/>
                <rect x="35" y="28" width="9" height="30" rx="3" fill="var(--main-orange)" opacity=".75"/>
                <rect x="48" y="34" width="9" height="24" rx="3" fill="var(--main-orange)" opacity=".55"/>
                <circle cx="60" cy="22" r="10" fill="var(--db-card)" stroke="var(--db-border-strong)" stroke-width="1.5"/>
                <path d="M57 22 h6 M60 19 v6" stroke="var(--db-text-muted)" stroke-width="1.5" stroke-linecap="round"/>
              </svg>
            </div>
            <div class="empty-copy">
              <div class="empty-title">{{ searchKeyword ? t('insight.searchNoResult') : t('insight.listEmpty') }}</div>
              <div class="empty-hint">{{ searchKeyword ? t('insight.searchNoResultHint') : t('insight.emptyHint') }}</div>
            </div>
            <div class="empty-actions">
              <el-button @click="toggleAiPanel">
                <template #icon><RobotIcon style="width: 16px; height: 16px;" /></template>
                {{ t('insight.aiAssistant') }}
              </el-button>
              <el-button v-if="canCreate" type="primary" @click="handleCreate">{{ t('insight.create') }}</el-button>
            </div>
          </div>

          <div v-else ref="cardGridRef" class="card-grid" :class="{ 'view-list': viewMode === 'list' }">
            <div
              v-for="dashboard in displayedDashboards"
              :key="dashboard.id"
              class="dashboard-card"
              :class="['card-theme-' + getCardTheme(dashboard.id), { 'card-is-empty': getDashboardChartKind(dashboard) === 'empty' }]"
              @click="handlePreview(dashboard.id)"
            >
              <!-- 头部：图标 + 标题 + 状态标签 -->
              <div class="card-header">
                <div class="card-icon">
                  <!-- bar icon -->
                  <svg v-if="getDashboardIconType(dashboard) === 'bar'" width="15.5" height="15.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="20" x2="12" y2="10"/><line x1="18" y1="20" x2="18" y2="4"/><line x1="6" y1="20" x2="6" y2="16"/></svg>
                  <!-- line icon -->
                  <svg v-else-if="getDashboardIconType(dashboard) === 'line'" width="15.5" height="15.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 3v16a2 2 0 0 0 2 2h16"/><path d="m7 13 3-3 4 4 5-6"/></svg>
                  <!-- pie icon -->
                  <svg v-else-if="getDashboardIconType(dashboard) === 'pie'" width="15.5" height="15.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21.21 15.89A10 10 0 1 1 8 2.83"/><path d="M22 12A10 10 0 0 0 12 2v10z"/></svg>
                  <!-- funnel icon -->
                  <svg v-else-if="getDashboardIconType(dashboard) === 'funnel'" width="15.5" height="15.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 4h18l-7 8v6l-4 2v-8L3 4z"/></svg>
                  <!-- empty icon -->
                  <svg v-else width="15.5" height="15.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2z"/><line x1="8" y1="10" x2="16" y2="10"/><line x1="8" y1="14" x2="13" y2="14"/></svg>
                </div>
                <span class="card-name">{{ dashboard.name }}</span>
                <el-tag
                  class="card-status"
                  :type="dashboard.status === 'published' ? 'success' : 'warning'"
                  effect="light"
                  size="small"
                  round
                >
                  <span class="status-dot"></span>{{ dashboard.status === 'published' ? t('insight.status.published') : t('insight.status.draft') }}
                </el-tag>
              </div>

              <!-- 描述：截断时悬停弹出完整文案 -->
              <el-tooltip
                :content="dashboard.description"
                placement="top"
                :show-after="150"
                :disabled="!truncatedDescs[dashboard.id]"
                popper-class="card-desc-tooltip"
              >
                <div class="card-desc" :data-id="dashboard.id">{{ dashboard.description || t('insight.noDescription') }}</div>
              </el-tooltip>

              <!-- 图表预览区（按主题切换图形类型） -->
              <div class="card-chart-preview">
                <!-- 空状态（无组件时） -->
                <svg v-if="getDashboardChartKind(dashboard) === 'empty'" width="180" height="44" viewBox="0 0 180 44" aria-hidden="true">
                  <rect x="2" y="2" width="176" height="40" rx="8" fill="none" stroke="var(--db-text-muted)" stroke-width="1.5" stroke-dasharray="4 3" opacity=".3"/>
                  <g transform="translate(78, 10)" opacity=".35">
                    <path d="M18 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2z" fill="none" stroke="var(--db-text-muted)" stroke-width="1.5"/>
                    <line x1="8" y1="10" x2="16" y2="10" stroke="var(--db-text-muted)" stroke-width="1.5" stroke-linecap="round"/>
                    <line x1="8" y1="14" x2="13" y2="14" stroke="var(--db-text-muted)" stroke-width="1.5" stroke-linecap="round"/>
                  </g>
                </svg>
                <!-- KPI 网格（无 chart 组件时） -->
                <svg v-else-if="getDashboardChartKind(dashboard) === 'kpi-grid'" width="180" height="44" viewBox="0 0 180 44" aria-hidden="true">
                  <rect x="4" y="4" width="54" height="36" rx="6" fill="var(--card-tint-fg)" opacity=".08"/>
                  <rect x="12" y="12" width="24" height="4" rx="2" fill="var(--card-tint-fg)" opacity=".5"/>
                  <rect x="12" y="22" width="36" height="6" rx="3" fill="var(--card-tint-fg)" opacity=".35"/>
                  <rect x="63" y="4" width="54" height="36" rx="6" fill="var(--card-tint-fg)" opacity=".08"/>
                  <rect x="71" y="12" width="24" height="4" rx="2" fill="var(--card-tint-fg)" opacity=".5"/>
                  <rect x="71" y="22" width="36" height="6" rx="3" fill="var(--card-tint-fg)" opacity=".35"/>
                  <rect x="122" y="4" width="54" height="36" rx="6" fill="var(--card-tint-fg)" opacity=".08"/>
                  <rect x="130" y="12" width="24" height="4" rx="2" fill="var(--card-tint-fg)" opacity=".5"/>
                  <rect x="130" y="22" width="36" height="6" rx="3" fill="var(--card-tint-fg)" opacity=".35"/>
                </svg>
                <!-- 折线 -->
                <svg v-else-if="getDashboardChartKind(dashboard) === 'line'" width="180" height="44" viewBox="0 0 180 44" aria-hidden="true">
                  <path d="M4 34 L34 26 L64 30 L94 16 L124 20 L154 8 L176 12" fill="none" stroke="var(--card-tint-fg)" stroke-width="2" stroke-linecap="round"/>
                  <circle cx="154" cy="8" r="3" fill="var(--db-card)" stroke="var(--card-tint-fg)" stroke-width="2"/>
                </svg>
                <!-- 柱状图 -->
                <svg v-else-if="getDashboardChartKind(dashboard) === 'bar'" width="180" height="44" viewBox="0 0 180 44" aria-hidden="true">
                  <rect x="6" y="22" width="14" height="16" rx="2" fill="var(--card-tint-fg)" opacity=".35"/>
                  <rect x="28" y="16" width="14" height="22" rx="2" fill="var(--card-tint-fg)" opacity=".5"/>
                  <rect x="50" y="24" width="14" height="14" rx="2" fill="var(--card-tint-fg)" opacity=".35"/>
                  <rect x="72" y="10" width="14" height="28" rx="2" fill="var(--card-tint-fg)" opacity=".7"/>
                  <rect x="94" y="18" width="14" height="20" rx="2" fill="var(--card-tint-fg)" opacity=".5"/>
                  <rect x="116" y="8" width="14" height="30" rx="2" fill="var(--card-tint-fg)" opacity=".85"/>
                  <rect x="138" y="14" width="14" height="24" rx="2" fill="var(--card-tint-fg)"/>
                  <rect x="160" y="6" width="14" height="32" rx="2" fill="var(--card-tint-fg)" opacity=".6"/>
                </svg>
                <!-- 面积折线 -->
                <svg v-else-if="getDashboardChartKind(dashboard) === 'area'" width="180" height="44" viewBox="0 0 180 44" aria-hidden="true">
                  <path d="M4 34 L34 26 L64 30 L94 16 L124 20 L154 8 L176 12 L176 42 L4 42 Z" fill="var(--card-tint-fg)" opacity=".1"/>
                  <path d="M4 34 L34 26 L64 30 L94 16 L124 20 L154 8 L176 12" fill="none" stroke="var(--card-tint-fg)" stroke-width="2" stroke-linecap="round"/>
                  <circle cx="154" cy="8" r="3" fill="var(--db-card)" stroke="var(--card-tint-fg)" stroke-width="2"/>
                </svg>
                <!-- 柱状图带告警色 -->
                <svg v-else-if="getDashboardChartKind(dashboard) === 'bar-alert'" width="180" height="44" viewBox="0 0 180 44" aria-hidden="true">
                  <rect x="6" y="12" width="14" height="26" rx="2" fill="var(--card-tint-fg)" opacity=".8"/>
                  <rect x="28" y="20" width="14" height="18" rx="2" fill="var(--card-tint-fg)" opacity=".5"/>
                  <rect x="50" y="26" width="14" height="12" rx="2" fill="var(--card-tint-fg)" opacity=".35"/>
                  <rect x="72" y="16" width="14" height="22" rx="2" fill="var(--card-tint-fg)" opacity=".6"/>
                  <rect x="94" y="28" width="14" height="10" rx="2" fill="var(--db-card-pink-fg)" opacity=".75"/>
                  <rect x="116" y="22" width="14" height="16" rx="2" fill="var(--card-tint-fg)" opacity=".45"/>
                  <rect x="138" y="18" width="14" height="20" rx="2" fill="var(--card-tint-fg)" opacity=".6"/>
                  <rect x="160" y="24" width="14" height="14" rx="2" fill="var(--card-tint-fg)" opacity=".35"/>
                </svg>
                <!-- 环形图 -->
                <svg v-else-if="getDashboardChartKind(dashboard) === 'donut'" width="120" height="44" viewBox="0 0 120 44" aria-hidden="true">
                  <circle cx="26" cy="22" r="15" fill="none" stroke="var(--db-chart-preview-track)" stroke-width="7"/>
                  <circle cx="26" cy="22" r="15" fill="none" stroke="var(--card-tint-fg)" stroke-width="7" stroke-dasharray="56 94" stroke-linecap="round" transform="rotate(-90 26 22)"/>
                  <circle cx="26" cy="22" r="15" fill="none" stroke="var(--db-card-blue-fg)" stroke-width="7" stroke-dasharray="24 118" stroke-dashoffset="-56" transform="rotate(-90 26 22)"/>
                  <circle cx="62" cy="14" r="3" fill="var(--card-tint-fg)"/>
                  <rect x="70" y="11.5" width="34" height="5" rx="2.5" fill="var(--db-chart-preview-track)"/>
                  <circle cx="62" cy="30" r="3" fill="var(--db-card-blue-fg)"/>
                  <rect x="70" y="27.5" width="24" height="5" rx="2.5" fill="var(--db-chart-preview-track)"/>
                </svg>
                <!-- 漏斗 -->
                <svg v-else-if="getDashboardChartKind(dashboard) === 'funnel'" width="150" height="44" viewBox="0 0 150 44" aria-hidden="true">
                  <path d="M10 8 h130 l-24 10 h-82 Z" fill="var(--card-tint-fg)" opacity=".8"/>
                  <path d="M30 20 h90 l-20 8 h-50 Z" fill="var(--card-tint-fg)" opacity=".55"/>
                  <path d="M48 30 h54 l-14 7 h-26 Z" fill="var(--card-tint-fg)" opacity=".32"/>
                </svg>
                <!-- 双折线 -->
                <svg v-else width="180" height="44" viewBox="0 0 180 44" aria-hidden="true">
                  <path d="M4 36 L34 30 L64 32 L94 22 L124 26 L154 14 L176 18" fill="none" stroke="var(--card-tint-fg)" stroke-width="2" stroke-linecap="round"/>
                  <path d="M4 40 L34 38 L64 39 L94 34 L124 35 L154 28 L176 30" fill="none" stroke="var(--card-tint-fg)" stroke-width="2" stroke-linecap="round" stroke-dasharray="4 4" opacity=".45"/>
                </svg>
              </div>

              <!-- 底部信息行：更新时间 + 更新人 -->
              <div class="card-meta">
                <span class="card-time">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                  {{ t('insight.updatedAt') }} {{ formatTime(dashboard.updateTime) }}
                </span>
                <span class="card-owner">
                  <span class="owner-avatar">{{ (dashboard.ownerName || '--').charAt(0) }}</span>
                  {{ dashboard.ownerName || '--' }}
                </span>
              </div>

              <!-- 操作栏 -->
              <div class="card-actions" @click.stop>
                <div class="action-group">
                  <button class="card-action-btn" @click="handlePreview(dashboard.id)">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                    {{ t('insight.preview') }}
                  </button>
                  <button
                    v-if="canCreate"
                    class="card-action-btn"
                    @click="handleCopy(dashboard)"
                  >
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                    {{ t('insight.copy') }}
                  </button>
                  <button
                    v-if="canCreate && dashboard.status === 'draft'"
                    class="card-action-btn action-publish"
                    @click="handlePublish(dashboard)"
                  >
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
                    {{ t('insight.publish') }}
                  </button>
                  <button
                    v-if="canModifyDashboard(dashboard) && dashboard.status === 'published'"
                    class="card-action-btn action-unpublish"
                    @click="handleUnpublish(dashboard)"
                  >
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="6" y="4" width="4" height="16"/><rect x="14" y="4" width="4" height="16"/></svg>
                    {{ t('insight.unpublish') }}
                  </button>
                </div>
                <div v-if="canModifyDashboard(dashboard)" class="action-group action-group-right">
                  <button
                    v-if="canModifyDashboard(dashboard)"
                    class="card-action-btn"
                    @click="handleEdit(dashboard.id)"
                  >
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                    {{ t('insight.edit') }}
                  </button>
                  <button
                    v-if="canModifyDashboard(dashboard)"
                    class="card-action-btn action-delete"
                    @click="handleDelete(dashboard)"
                  >
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                    {{ t('insight.delete') }}
                  </button>
                 </div>
                <span v-else class="no-perm">
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                  {{ t('insight.noEditPermission') }}
                </span>
              </div>
            </div>
          </div>
          </div>
        </div>
      </div>

      <!-- AI助手抽屉（Element Plus Drawer，遮罩 + 右侧浮动面板） -->
      <el-drawer
        v-model="showAiPanel"
        direction="rtl"
        size="400px"
        :with-header="false"
        class="ai-drawer-overlay"
      >
        <AiChatPanel
          @close="showAiPanel = false"
          @dashboard-updated="handleAiDashboardUpdated"
        />
      </el-drawer>
    </div>
  </div>
</template>

    <!-- 编辑器模式 -->
    <InsightDashboardEditorView
      v-else-if="mode === 'editor'"
      :dashboard-id="currentDashboardId"
      @back="handleBackToList"
      @preview="handlePreviewFromEditor"
    />

    <!-- 预览模式 -->
    <DashboardPreviewView
      v-else-if="mode === 'preview'"
      :dashboard-id="currentDashboardId"
      @back="handleBackToList"
      @edit="handleEdit(currentDashboardId)"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, computed, nextTick, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import RobotIcon from './components/RobotIcon.vue'
import dayjs from 'dayjs'
import { formatRelativeTime } from '@/utils/time'
import type { InsightDashboard } from '@/types'
import { useInsightDashboardStore } from '@/stores/useInsightDashboardStore'
import { usePersistedState } from '@/composables/usePersistedRef'
import { usePermission, PERMISSION } from '@/composables/usePermission'
import { useUserStore } from '@/stores/useUserStore'
import InsightDashboardEditorView from './InsightDashboardEditorView.vue'
import DashboardPreviewView from './DashboardPreviewView.vue'
import AiChatPanel from './components/AiChatPanel.vue'

defineOptions({
  name: 'DashboardListView',
})

const { t } = useI18n()
const store = useInsightDashboardStore()
const { hasPermission, canModifyResource } = usePermission()
const userStore = useUserStore()

/** 新建/复制权限：member 及以上（viewer 只读） */
const canCreate = computed(() => hasPermission(PERMISSION.INSIGHT_CREATE))

/**
 * 是否可管理该仪表盘（编辑/发布/取消发布/删除）：
 * 工作区 admin+owner 管理全部，普通成员仅限自己创建的
 */
function canModifyDashboard(dashboard: InsightDashboard): boolean {
  return canModifyResource((dashboard as InsightDashboard & { ownerId?: number | string | null }).ownerId)
}

type ViewMode = 'list' | 'editor' | 'preview'
const mode = usePersistedState<ViewMode>('mc-insight-view-mode', 'list')
const currentDashboardId = usePersistedState<string>('mc-insight-dashboard-id', '')

/** AI助手面板可见性 */
const showAiPanel = ref(false)

/** 搜索关键词 */
const searchKeyword = ref('')

/** 按关键词过滤仪表盘列表 */
const filteredDashboards = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return store.dashboards
  }
  return store.dashboards.filter((d) => {
    return d.name?.toLowerCase().includes(keyword)
      || d.description?.toLowerCase().includes(keyword)
      || d.ownerName?.toLowerCase().includes(keyword)
  })
})

/** 状态筛选：all / published / draft */
const statusFilter = ref<'all' | 'published' | 'draft'>('all')

/** 排序方向：desc=最近更新在前 */
const sortOrder = ref<'desc' | 'asc'>('desc')

/** 卡片布局：grid / list（持久化） */
const viewMode = usePersistedState<'grid' | 'list'>('mc-insight-view-layout', 'grid')

/** 卡片网格容器 ref：用于测量描述截断状态 */
const cardGridRef = ref<HTMLElement | null>(null)

/** 描述被截断的看板 id 集合：仅截断的卡片悬停时弹出完整描述 */
const truncatedDescs = ref<Record<string, boolean>>({})

/** 测量各卡片描述是否被单行省略截断（scrollWidth 超出可视宽度即为截断） */
function measureDescTruncation(): void {
  const root = cardGridRef.value
  if (!root) {
    return
  }
  const map: Record<string, boolean> = {}
  root.querySelectorAll<HTMLElement>('.card-desc').forEach((el) => {
    const id = el.dataset.id
    if (id) {
      map[id] = el.scrollWidth > el.clientWidth
    }
  })
  truncatedDescs.value = map
}

/** 窗口尺寸变化会改变卡片宽度，需重新测量 */
function handleWindowResize(): void {
  measureDescTruncation()
}

/** 各状态计数（基于搜索后的列表） */
const statusCounts = computed(() => {
  const list = filteredDashboards.value
  return {
    all: list.length,
    published: list.filter((d) => d.status === 'published').length,
    draft: list.filter((d) => d.status === 'draft').length,
  }
})

/** 最终展示列表：状态筛选 + 按更新时间排序 */
const displayedDashboards = computed(() => {
  let list = filteredDashboards.value
  if (statusFilter.value !== 'all') {
    list = list.filter((d) => d.status === statusFilter.value)
  }
  const sorted = [...list]
  sorted.sort((a, b) => {
    const ta = dayjs(a.updateTime).valueOf() || 0
    const tb = dayjs(b.updateTime).valueOf() || 0
    return sortOrder.value === 'desc' ? tb - ta : ta - tb
  })
  return sorted
})

/* watch 必须位于 displayedDashboards 声明之后：
   setup 同步执行时求值源数组会访问未初始化的 const，抛出 TDZ ReferenceError 导致整页崩溃 */
watch([displayedDashboards, viewMode], () => {
  void nextTick(measureDescTruncation)
})

/** 切换排序方向 */
function toggleSortOrder(): void {
  sortOrder.value = sortOrder.value === 'desc' ? 'asc' : 'desc'
}

onMounted(() => {
  window.addEventListener('resize', handleWindowResize)
  store.fetchDashboards().catch(() => {
    ElMessage.error(t('insight.loadFailed'))
  })
  // 刷新后恢复编辑/预览模式时，需要加载对应仪表盘数据
  if (mode.value !== 'list' && currentDashboardId.value) {
    store.selectDashboard(currentDashboardId.value).catch(() => {
      // 仪表盘可能已被删除，回退到列表
      mode.value = 'list'
      currentDashboardId.value = ''
    })
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleWindowResize)
})

/** 格式化时间：相对时间（刚刚 / x 分钟前 / 昨天 HH:mm…），空值回退 -- */
function formatTime(time: string): string {
  return formatRelativeTime(time) || '--'
}

/** 切换AI助手面板 */
function toggleAiPanel(): void {
  showAiPanel.value = !showAiPanel.value
}

/** AI助手生成仪表盘成功后 */
function handleAiDashboardUpdated(dashboardId: string): void {
  if (dashboardId) {
    currentDashboardId.value = dashboardId
    mode.value = 'editor'
  }
  showAiPanel.value = false
  store.fetchDashboards().catch(() => {
    // 静默失败
  })
}

/** 新建仪表盘 */
async function handleCreate(): Promise<void> {
  try {
    const created = await store.createDashboard({
      name: t('insight.defaultName'),
      description: '',
    })
    currentDashboardId.value = created.id
    mode.value = 'editor'
  } catch {
    ElMessage.error(t('insight.createFailed'))
  }
}

/** 编辑仪表盘 */
function handleEdit(id: string): void {
  currentDashboardId.value = id
  mode.value = 'editor'
}

/** 预览仪表盘 */
function handlePreview(id: string): void {
  currentDashboardId.value = id
  mode.value = 'preview'
}

/** 从编辑器进入预览 */
function handlePreviewFromEditor(dashboardId: string): void {
  currentDashboardId.value = dashboardId
  mode.value = 'preview'
}

/** 发布仪表盘 */
async function handlePublish(dashboard: InsightDashboard): Promise<void> {
  try {
    await store.updateDashboard(dashboard.id, { status: 'published' })
    ElMessage.success(t('insight.publishSuccess'))
  } catch {
    ElMessage.error(t('insight.publishFailed'))
  }
}

/** 取消发布 */
async function handleUnpublish(dashboard: InsightDashboard): Promise<void> {
  try {
    await store.updateDashboard(dashboard.id, { status: 'draft' })
    ElMessage.success(t('insight.unpublishSuccess'))
  } catch {
    ElMessage.error(t('insight.unpublishFailed'))
  }
}

/** 复制仪表盘 */
async function handleCopy(dashboard: InsightDashboard): Promise<void> {
  try {
    await store.copyDashboard(dashboard.id)
    ElMessage.success(t('insight.copySuccess'))
  } catch {
    ElMessage.error(t('insight.copyFailed'))
  }
}

/** 删除仪表盘 */
async function handleDelete(dashboard: InsightDashboard): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('insight.deleteConfirm', { name: dashboard.name }),
      t('common.confirm'),
      { type: 'warning' }
    )
    await store.deleteDashboard(dashboard.id)
    ElMessage.success(t('insight.deleteSuccess'))
  } catch (e) {
    // 用户取消删除时不报错
    if (e !== 'cancel') {
      ElMessage.error(t('insight.deleteFailed'))
    }
  }
}

/** 卡片主题色（浅色系）：按 ID hash 稳定分配 */
type CardTheme = 'blue' | 'violet' | 'green' | 'orange' | 'pink' | 'cyan'
const cardThemes: CardTheme[] = ['blue', 'violet', 'green', 'orange', 'pink', 'cyan']

function hashId(id: string): number {
  let hash = 0
  for (let i = 0; i < id.length; i++) {
    hash = id.charCodeAt(i) + ((hash << 5) - hash)
  }
  return Math.abs(hash)
}

function getCardTheme(id: string): CardTheme {
  return cardThemes[hashId(id) % cardThemes.length]
}

/** 卡片主题对应的图标类型（保留用于回退） */
const cardThemeIcon: Record<CardTheme, 'bar' | 'line' | 'pie' | 'funnel'> = {
  blue: 'bar',
  violet: 'line',
  green: 'bar',
  orange: 'pie',
  pink: 'line',
  cyan: 'funnel',
}

/** 卡片主题对应的图表预览图形类型（回退用） */
const cardThemeChart: Record<CardTheme, ChartKind> = {
  blue: 'bar',
  violet: 'area',
  green: 'bar-alert',
  orange: 'donut',
  pink: 'dual-line',
  cyan: 'funnel',
}

/** 从仪表盘 schemaJson 解析出主要图表类型 */
type ChartKind = 'bar' | 'area' | 'bar-alert' | 'donut' | 'funnel' | 'dual-line' | 'kpi-grid' | 'line' | 'empty'

/** chartType → ChartKind 映射 */
const chartTypeToKind: Record<string, ChartKind> = {
  bar: 'bar',
  line: 'line',
  area: 'area',
  pie: 'donut',
  scatter: 'dual-line',
  radar: 'donut',
  funnel: 'funnel',
  gauge: 'donut',
  heatmap: 'bar-alert',
  candlestick: 'bar-alert',
  sankey: 'funnel',
  treemap: 'bar',
  sunburst: 'donut',
  tree: 'line',
  graph: 'dual-line',
  map: 'area',
  lines: 'dual-line',
  boxplot: 'bar',
  parallel: 'dual-line',
  themeRiver: 'area',
  pictorialBar: 'bar',
  effectScatter: 'dual-line',
}

function getDashboardChartKind(dashboard: InsightDashboard): ChartKind {
  // schemaJson 为空 → 空状态
  if (!dashboard.schemaJson || dashboard.schemaJson.trim() === '') return 'empty'
  try {
    const parsed = JSON.parse(dashboard.schemaJson)
    const pages = parsed?.pages ?? []
    if (!Array.isArray(pages) || pages.length === 0) return 'empty'

    // 收集所有组件
    const allComponents: Array<{ type?: string; chartType?: string }> = []
    for (const page of pages) {
      if (Array.isArray(page.components)) {
        allComponents.push(...page.components)
      }
    }
    if (allComponents.length === 0) return 'empty'

    // 统计 chart 类型分布
    const chartCounts: Record<string, number> = {}
    let hasKpi = false
    let hasTable = false
    for (const comp of allComponents) {
      if (comp.type === 'chart' && comp.chartType) {
        chartCounts[comp.chartType] = (chartCounts[comp.chartType] ?? 0) + 1
      } else if (comp.type === 'kpi') {
        hasKpi = true
      } else if (comp.type === 'table') {
        hasTable = true
      }
    }

    // 有 chart 组件 → 取数量最多的 chartType
    const entries = Object.entries(chartCounts)
    if (entries.length > 0) {
      entries.sort((a, b) => b[1] - a[1])
      const topChartType = entries[0][0]
      return chartTypeToKind[topChartType] ?? 'line'
    }

    // 无 chart 但有 kpi/table → 用 kpi-grid
    if (hasKpi || hasTable) return 'kpi-grid'

    // 只有 filter/timeFilter/aiAnalysis → 默认简洁样式
    return 'kpi-grid'
  } catch {
    // schemaJson 解析失败 → 回退到主题色映射
    return cardThemeChart[getCardTheme(dashboard.id)]
  }
}

function getDashboardIconType(dashboard: InsightDashboard): 'bar' | 'line' | 'pie' | 'funnel' | 'empty' {
  const kind = getDashboardChartKind(dashboard)
  if (kind === 'empty') return 'empty'
  if (kind === 'bar' || kind === 'bar-alert') return 'bar'
  if (kind === 'line' || kind === 'area' || kind === 'dual-line') return 'line'
  if (kind === 'donut') return 'pie'
  if (kind === 'funnel') return 'funnel'
  if (kind === 'kpi-grid') return 'bar'
  return 'line'
}

/** 返回列表 */
function handleBackToList(): void {
  mode.value = 'list'
  currentDashboardId.value = ''
  store.fetchDashboards().catch(() => {
    // 静默失败
  })
}
</script>

<style scoped>
.dashboard-list-view {
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.list-mode {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--db-bg);
  /* 四周留灰底边距，让纸面卡片悬浮于页面底色之上，与透明导航拉开层级 */
  padding: var(--space-md) var(--space-lg) var(--space-lg);
}

/* 悬浮纸面卡片：承载筛选/搜索行与列表，白色表面 + 圆角 + 投影，与灰底页面分层 */
.page-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--theme-surface);
  border: 1px solid var(--theme-border);
  border-radius: 16px;
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}

/* 页头：标题组（左） + 搜索与操作（右）同一行，建立页面层级且不占多余行数 */
.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: 20px var(--space-xl) 12px;
  flex-shrink: 0;
}

/* 标题组：图标 chip（左） + 标题/副标题（右），横向排布 */
.list-title-group {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

/* 标题图标：主题色淡底圆角 chip，轻量不抢顶导层级 */
.list-title-icon {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: color-mix(in srgb, var(--main-orange) 10%, transparent);
  color: var(--main-orange);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.list-title-text {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.list-title {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--db-text);
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.list-subtitle {
  margin: 0;
  font-size: 12px;
  color: var(--db-text-muted);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.list-body {
  flex: 1;
  min-height: 0;
  display: flex;
  overflow: hidden;
}

/* 搜索行：搜索居左、AI 助手 / 新建居右；作为卡片第二行，与上方筛选行分隔 */
/* 搜索框：白底描边胶囊（36px），置于页头右侧与标题同行；
   宽度可收缩，优先让出空间给操作按钮 */
.search-input {
  width: 260px;
  max-width: 100%;
  flex: 0 1 260px;
  min-width: 160px;
}

.search-input :deep(.el-input__wrapper) {
  height: 36px;
  border-radius: 999px;
  background: var(--theme-surface-elevated);
  box-shadow: var(--shadow-sm), inset 0 0 0 1px var(--theme-border-strong);
  padding: 0 14px;
  transition: box-shadow var(--transition-fast), background var(--transition-fast);
}

.search-input :deep(.el-input__inner) {
  font-size: 13px;
}

.search-input :deep(.el-input__wrapper:hover) {
  box-shadow:
    var(--shadow-sm),
    inset 0 0 0 1px color-mix(in srgb, var(--main-orange) 45%, var(--theme-border-strong));
}

.search-input :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 3px color-mix(in srgb, var(--main-orange) 14%, transparent),
    inset 0 0 0 1px var(--main-orange);
}

.search-input :deep(.el-input__prefix) {
  color: var(--db-text-muted);
  transition: color var(--transition-fast);
}

.search-input :deep(.el-input__wrapper.is-focus .el-input__prefix) {
  color: var(--main-orange);
}

/* 页头操作区：搜索 / AI 助手 / 新建同一行，flex gap 保证间距；
   同时清零 Element 相邻按钮自带 margin，避免与 gap 叠加 */
.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
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
  border-color: var(--db-accent);
  color: var(--db-accent);
}

.header-actions :deep(.el-button.ai-assistant-btn.on) {
  background: color-mix(in srgb, var(--db-accent) 8%, transparent);
  border-color: var(--db-accent);
  color: var(--db-accent);
  font-weight: 600;
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

.list-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 0 var(--space-lg) var(--space-lg);
}

.filter-row {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: 0 0 var(--space-md);
}

/* 窄屏：筛选行收紧间距，搜索行换行让搜索独占整行，避免右侧按钮被推出视口 */
@media (max-width: 640px) {
  .list-header {
    flex-wrap: wrap;
  }

  .header-actions {
    flex: 1 1 100%;
    flex-wrap: wrap;
  }

  .search-input {
    flex: 1 1 100%;
    width: 100%;
    min-width: 0;
  }

  .filter-row {
    gap: 12px;
  }

  .filter-tabs {
    gap: 12px;
  }
}

/* 状态 Tab 容器：灰底分段胶囊（次级控件，内 3px 衬住激活项） */
.filter-tabs {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px;
  border-radius: 999px;
  background: var(--db-bg);
  max-width: 100%;
  overflow-x: auto;
  scrollbar-width: none;
}

.filter-tabs::-webkit-scrollbar {
  display: none;
}

/* 状态 Tab 项：默认灰容器上透明，hover 浅灰底；
   激活为白面浮起 + 主题色描边/文字（次级高亮，实心 pill 只留给顶导） */
.filter-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 13px;
  border-radius: 999px;
  border: 1px solid transparent;
  background: transparent;
  font-size: 13px;
  font-weight: 500;
  color: var(--db-text-secondary);
  cursor: pointer;
  white-space: nowrap;
  font-family: inherit;
  flex-shrink: 0;
  transition: color var(--transition-fast), background var(--transition-fast), border-color var(--transition-fast), box-shadow var(--transition-fast);
}

.filter-tab:hover:not(.active) {
  color: var(--db-text);
  background: color-mix(in srgb, var(--db-text-muted) 8%, transparent);
}

.filter-tab.active {
  background: var(--theme-surface-elevated);
  border-color: color-mix(in srgb, var(--main-orange) 40%, transparent);
  color: var(--main-orange);
  font-weight: 600;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

/* Tab 状态点：已发布绿 / 草稿橙，与卡片状态标签同色系 */
.tab-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.tab-dot.dot-published {
  background: #14a05a;
}

.tab-dot.dot-draft {
  background: #dd8a1d;
}

/* Tab 计数徽标：默认半透明灰 chip，激活 pill 上为半透明白底 + 白字 */
.tab-cnt {
  font-size: 11px;
  font-weight: 600;
  line-height: 16px;
  color: var(--db-text-secondary);
  background: color-mix(in srgb, var(--db-text-muted) 14%, transparent);
  border-radius: 999px;
  padding: 0 7px;
  min-width: 16px;
  text-align: center;
  font-variant-numeric: tabular-nums;
  transition: color var(--transition-fast), background var(--transition-fast);
}

.filter-tab.active .tab-cnt {
  color: var(--main-orange);
  background: color-mix(in srgb, var(--main-orange) 10%, transparent);
}

.filter-sort {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: transparent;
  font-size: 12.5px;
  color: var(--db-text-secondary);
  cursor: pointer;
  padding: 4px 6px;
  border-radius: var(--radius-sm);
  white-space: nowrap;
}

.filter-sort:hover {
  color: var(--db-text);
  background: var(--db-hover);
}

.view-toggle {
  display: flex;
  border: 1px solid var(--db-border-strong);
  border-radius: 7px;
  overflow: hidden;
  background: var(--db-card);
  flex-shrink: 0;
}

.view-toggle-btn {
  width: 32px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--db-text-muted);
  cursor: pointer;
  padding: 0;
}

.view-toggle-btn.on {
  background: color-mix(in srgb, var(--main-orange) 8%, transparent);
  color: var(--main-orange);
}

.list-scroll {
  flex: 1;
  overflow-y: auto;
  /* 顶部留白：为首卡片 hover 上浮预留空间，避免上边框/投影被滚动容器裁切 */
  padding: 4px 0 var(--space-xl);
}

.empty-state {
  width: 100%;
  min-height: 360px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 20px;
  padding: 48px 24px;
}

.empty-illustration svg {
  display: block;
  filter: drop-shadow(0 4px 12px color-mix(in srgb, var(--main-orange) 12%, transparent));
}

.empty-copy {
  text-align: center;
  max-width: 360px;
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--db-text);
  line-height: 1.4;
  margin-bottom: 6px;
}

.empty-hint {
  font-size: 13px;
  color: var(--db-text-muted);
  line-height: 1.5;
}

.empty-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 4px;
}

.empty-actions :deep(.el-button) {
  height: 36px;
  border-radius: 8px;
  padding: 0 16px;
  font-size: 13.5px;
  font-weight: 500;
}

.empty-actions :deep(.el-button:not(.el-button--primary)) {
  background: var(--db-card);
  border-color: var(--db-border-strong);
  color: var(--db-text-secondary);
}

.empty-actions :deep(.el-button:not(.el-button--primary):hover) {
  border-color: var(--db-accent);
  color: var(--db-accent);
}

.empty-actions :deep(.el-button--primary) {
  background: var(--main-orange);
  border-color: var(--main-orange);
  box-shadow: var(--shadow-md);
}

.empty-actions :deep(.el-button--primary:hover),
.empty-actions :deep(.el-button--primary:focus) {
  background: var(--main-orange);
  border-color: var(--main-orange);
  filter: brightness(1.08);
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  grid-auto-rows: 1fr;
  gap: var(--space-lg);
}

.card-grid.view-list {
  grid-template-columns: 1fr;
}

.dashboard-card {
  background: var(--db-card);
  border: 1px solid var(--db-border);
  border-radius: var(--radius-lg);
  padding: 16px 16px 12px;
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-card);
  cursor: pointer;
  transition: box-shadow var(--transition-base), border-color var(--transition-fast), transform var(--transition-fast);
  overflow: hidden;
}

.dashboard-card:hover {
  border-color: var(--db-border-strong);
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* 卡片主题：浅色系图标底色 + 图表主色（深色模式下由 --db-card-*-bg 覆盖） */
.card-theme-blue { --card-tint-bg: var(--db-card-blue-bg); --card-tint-fg: var(--db-card-blue-fg); }
.card-theme-violet { --card-tint-bg: var(--db-card-violet-bg); --card-tint-fg: var(--db-card-violet-fg); }
.card-theme-green { --card-tint-bg: var(--db-card-green-bg); --card-tint-fg: var(--db-card-green-fg); }
.card-theme-orange { --card-tint-bg: var(--db-card-orange-bg); --card-tint-fg: var(--db-card-orange-fg); }
.card-theme-pink { --card-tint-bg: var(--db-card-pink-bg); --card-tint-fg: var(--db-card-pink-fg); }
.card-theme-cyan { --card-tint-bg: var(--db-card-cyan-bg); --card-tint-fg: var(--db-card-cyan-fg); }

/* 空状态：icon 颜色与预览图一致（灰色） */
.card-is-empty .card-icon {
  background: color-mix(in srgb, var(--db-text-muted) 10%, transparent);
  color: var(--db-text-muted);
}

.card-icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--card-tint-bg);
  color: var(--card-tint-fg);
}

.card-name {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-status.el-tag {
  margin-left: auto;
  border: none;
  border-radius: 20px;
  font-weight: 600;
  flex-shrink: 0;
}

.card-status .status-dot {
  display: inline-block;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  margin-right: 5px;
}

.card-status.el-tag--success {
  background: #e7f8ef;
  color: #14a05a;
}

.card-status.el-tag--warning {
  background: #fdf1e0;
  color: #dd8a1d;
}

.card-desc {
  padding: 8px 0;
  font-size: 12px;
  color: var(--db-text-secondary);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-chart-preview {
  height: 100px;
  min-height: 100px;
  flex: 1;
  background: var(--db-chart-preview-bg);
  border: 1px solid var(--db-border);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  gap: var(--space-md);
}

.card-time {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--db-text-muted);
  white-space: nowrap;
}

.card-time svg {
  flex-shrink: 0;
}

.card-owner {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--db-text-secondary);
  white-space: nowrap;
}

.owner-avatar {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--db-accent-light);
  color: var(--db-accent);
  font-size: 10px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.card-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0;
  border-top: 1px solid var(--db-border);
  padding-top: 8px;
  flex-wrap: nowrap;
}
.action-group {
  display: flex;
  align-items: center;
  gap: 2px;
}
.action-group-right {
  border-left: 1px solid var(--db-border);
}

.no-perm {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--db-text-quaternary);
  white-space: nowrap;
}
.card-action-btn {
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
  transition: color var(--transition-fast), background var(--transition-fast);
  white-space: nowrap;
}

.card-action-btn:hover {
  color: var(--db-text);
  background: var(--db-hover);
}

.card-action-btn.action-publish {
  color: var(--main-orange);
  font-weight: 600;
  background: color-mix(in srgb, var(--main-orange) 8%, transparent);
}

.card-action-btn.action-publish:hover {
  background: color-mix(in srgb, var(--main-orange) 14%, transparent);
}

.card-action-btn.action-unpublish {
  color: #d97706;
}

.card-action-btn.action-unpublish:hover {
  color: #b45309;
  background: rgba(245, 158, 11, 0.06);
}

.card-action-btn.action-delete {
  color: #ef4444;
}

.card-action-btn.action-delete:hover {
  color: #dc2626;
  background: var(--db-danger-bg);
}
</style>

<!-- 抽屉覆盖样式（非 scoped：class 继承到 el-drawer 根节点 .el-overlay，scoped 选择器够不到） -->
<style>
/* 遮罩：使用 --db-mask，随明暗主题切换 */
.ai-drawer-overlay.el-overlay {
  background-color: var(--db-mask);
}

/* 面板：白/暗底卡片、左侧圆角与投影，贴合设计稿 */
.ai-drawer-overlay .el-drawer {
  background: var(--db-card);
  border-radius: 12px 0 0 12px;
  border-left: 1px solid var(--db-border);
  box-shadow: -14px 0 44px rgba(23, 43, 99, 0.18);
}

/* 抽屉内容区无内边距，AiChatPanel 自带各区域 padding */
.ai-drawer-overlay .el-drawer__body {
  padding: 0;
  overflow: hidden;
}
</style>
