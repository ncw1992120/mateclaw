<template>
  <div class="knowledge-config-page">
    <!-- ========== 知识库列表视图 (Library) ========== -->
    <div v-if="!currentKB" ref="libraryRef" class="kb-library">
      <!-- 身份页头：标题 + 数量统计 + 描述（左） + 主操作（右），与技能/数据/词典页同一版式语言 -->
      <div class="kb-content-header">
        <div class="kb-content-title">
          <div class="kb-content-name-row">
            <h3 class="kb-content-name">{{ t('knowledgeConfig.wikiLibrary') }}</h3>
            <!-- 数量标识：加载完成后才显示，避免首帧 0 闪烁 -->
            <span v-if="statsReady" class="kb-title-stats">
              <span class="summary-item">
                <span class="summary-dot" aria-hidden="true"></span>
                {{ t('knowledgeConfig.kbCount') }}
                <b class="summary-num">{{ knowledgeBases.length }}</b>
              </span>
            </span>
          </div>
          <p class="kb-content-desc">{{ t('knowledgeConfig.desc') }}</p>
        </div>
        <div class="header-actions">
          <button v-if="canManage" type="button" class="btn-create-pill" @click="openCreateDialog">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true">
              <line x1="12" y1="5" x2="12" y2="19" />
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
            {{ t('knowledgeConfig.create') }}
          </button>
        </div>
      </div>

      <!-- 加载中 -->
      <div v-if="loading && knowledgeBases.length === 0" class="page-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>{{ t('common.loading') }}</span>
      </div>

      <!-- 全局空态：线型 SVG chip + 文案 + 胶囊按钮，与技能/数据/词典页同形态 -->
      <div v-else-if="knowledgeBases.length === 0" class="global-empty surface-card">
        <div class="global-empty-icon">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
            <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
          </svg>
        </div>
        <h3>{{ t('knowledgeConfig.emptyTitle') }}</h3>
        <p class="global-empty-desc">{{ t('knowledgeConfig.emptyDesc') }}</p>
        <button v-if="canManage" type="button" class="btn-create-pill small" @click="openCreateDialog">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true">
            <line x1="12" y1="5" x2="12" y2="19" />
            <line x1="5" y1="12" x2="19" y2="12" />
          </svg>
          {{ t('knowledgeConfig.create') }}
        </button>
      </div>

      <!-- 知识库卡片网格 -->
      <div v-else class="kb-card-grid">
        <div
          v-for="kb in knowledgeBases"
          :key="kb.id"
          class="kb-card"
          @click="selectKB(kb)"
        >
          <div class="kb-card-header">
            <h3 class="kb-card-name" :title="kb.name">{{ kb.name }}</h3>
            <el-tag class="kb-card-status" :type="statusTagType(kb.status)" effect="light" size="small" round>
              <span class="status-dot"></span>{{ statusLabel(kb.status) }}
            </el-tag>
          </div>
          <!-- 描述：截断时悬停弹出完整文案（对齐报告/洞察卡片） -->
          <el-tooltip
            :content="kb.description || t('knowledgeConfig.noDescription')"
            placement="top"
            :show-after="150"
            :disabled="!kb.description || !truncatedDescs[kb.id]"
            popper-class="card-desc-tooltip"
          >
            <p class="kb-card-desc" :data-id="kb.id">{{ kb.description || t('knowledgeConfig.noDescription') }}</p>
          </el-tooltip>
          <div class="kb-card-footer">
            <span class="kb-card-meta">
              {{ t('knowledgeConfig.rawCount') }} {{ kb.rawCount }}
              <span class="meta-sep">·</span>
              {{ t('knowledgeConfig.pageCount') }} {{ kb.pageCount }}
            </span>
            <!-- 操作：纯文字轻量按钮，常驻底栏右侧 -->
            <div v-if="canManage" class="kb-card-actions" @click.stop>
              <button type="button" class="kb-card-action" @click="openEditDialog(kb)">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                {{ t('common.edit') }}
              </button>
              <button type="button" class="kb-card-action kb-card-action--danger" @click="handleDelete(kb)">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                {{ t('knowledgeConfig.delete') }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ========== 知识库工作区视图 (Workspace) ========== -->
    <div v-else class="kb-workspace">
      <!-- 工作区头部 -->
      <div class="workspace-header">
        <div class="header-left">
          <button type="button" class="ws-back-btn" :title="t('knowledgeConfig.backToLibrary')" @click="backToLibrary">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/></svg>
          </button>
          <div class="header-kb-info">
            <span class="header-kb-name">{{ currentKB.name }}</span>
            <span v-if="currentKB.description" class="header-kb-desc">{{ currentKB.description }}</span>
          </div>
        </div>
        <div class="header-right">
          <!-- 状态从左侧身份组移至右组，与计数元信息行组成“状态信息组”（左侧只留身份） -->
          <el-tag class="kb-card-status workspace-status" :type="statusTagType(currentKB.status)" effect="light" size="small" round>
            <span class="status-dot"></span>{{ statusLabel(currentKB.status) }}
          </el-tag>
          <span class="workspace-meta">
            {{ t('knowledgeConfig.pageCount') }} {{ currentKB.pageCount }}
            <span class="meta-sep">·</span>
            {{ t('knowledgeConfig.rawCount') }} {{ currentKB.rawCount }}
          </span>
        </div>
      </div>

      <div class="workspace-body">
        <!-- 左侧 Wiki 页面导航 -->
        <div class="wiki-sidebar">
          <div class="sidebar-header">
            <span class="sidebar-title">{{ t('knowledgeConfig.wikiPages') }}</span>
            <span class="sidebar-count">{{ pages.length }}</span>
          </div>
          <el-input
            v-model="pageSearchQuery"
            :placeholder="t('knowledgeConfig.searchPages')"
            clearable
            class="sidebar-search"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <div class="sidebar-pages">
            <!-- 分组折叠改用 el-collapse：动画/aria/点击区内置，展开态由 v-model 持有（搜索过滤不丢失） -->
            <el-collapse v-model="expandedGroups" class="sidebar-collapse">
              <el-collapse-item v-for="[type, group] in groupedSidebarPages" :key="type" :name="type">
                <template #title>
                  <span class="sidebar-group-row">
                    <span class="sidebar-group-left">
                      <span>{{ type }}</span>
                      <span class="sidebar-group-count">{{ group.length }}</span>
                    </span>
                    <el-icon class="sidebar-group-arrow"><ArrowDown /></el-icon>
                  </span>
                </template>
                <div class="sidebar-group-pages">
                  <div
                    v-for="page in group"
                    :key="page.id"
                    class="sidebar-page-item"
                    :class="{ active: currentPage?.slug === page.slug }"
                    @click="openPage(page); activeTab = 'pages'"
                  >
                    <span class="sidebar-page-title">{{ page.title }}</span>
                    <span v-if="page.locked" class="page-flag page-flag--locked" :title="t('knowledgeConfig.pageLocked')">
                      <svg width="9" height="9" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                    </span>
                  </div>
                </div>
              </el-collapse-item>
            </el-collapse>
          </div>
        </div>

        <!-- 右侧主内容区 -->
        <div class="workspace-main">
          <!-- Tab 导航 -->
          <div class="workspace-tabs">
            <button
              v-for="tab in visibleTabs"
              :key="tab.key"
              class="tab-btn"
              :class="{ active: activeTab === tab.key }"
              @click="activeTab = tab.key"
            >
              {{ tab.label }}
            </button>
          </div>

          <!-- Tab 内容区 -->
          <div class="workspace-content">
        <!-- ===== 原始材料 Tab ===== -->
        <div v-if="activeTab === 'raw'" class="tab-panel raw-panel">
          <!-- 摄取工具卡：上传条 + 添加文本 + 目录扫描 -->
          <div v-if="canManage" class="raw-ingest">
            <div
              class="upload-zone"
              :class="{ 'is-dragging': isDragging }"
              @click="triggerFileInput"
              @dragover.prevent
              @dragenter.prevent="isDragging = true"
              @dragleave.prevent="isDragging = false"
              @drop.prevent="handleDrop"
            >
              <span class="upload-icon-chip">
                <el-icon :size="16"><Upload /></el-icon>
              </span>
              <div class="upload-text">
                <span class="upload-label">{{ t('knowledgeConfig.dropFiles') }}</span>
                <span class="upload-hint">.txt .md .csv .pdf .docx .xlsx .pptx .html</span>
              </div>
            </div>
            <input ref="fileInput" type="file" style="display:none" accept=".txt,.md,.csv,.pdf,.docx,.doc,.xlsx,.xls,.pptx,.ppt,.html,.htm" multiple @change="handleFileSelect" />
            <div class="raw-ingest-actions">
              <el-button class="add-text-btn" @click="showAddTextDialog = true">
                <el-icon><Plus /></el-icon>
                {{ t('knowledgeConfig.addText') }}
              </el-button>
              <el-input
                v-model="scanPath"
                :placeholder="t('knowledgeConfig.dirPlaceholder')"
                clearable
                class="scan-input"
                @keyup.enter="handleScanDir"
              >
                <template #prefix>
                  <el-icon><Folder /></el-icon>
                </template>
                <template #append>
                  <el-button :loading="scanning" class="scan-append-btn" @click="handleScanDir">
                    <el-icon><Search /></el-icon>
                    {{ t('knowledgeConfig.scan') }}
                  </el-button>
                </template>
              </el-input>
            </div>
          </div>

          <!-- 扫描结果 -->
          <div v-if="scanResult" class="scan-result">
            <el-alert type="success" :closable="true" @close="scanResult = null">
              {{ t('knowledgeConfig.scanResult', scanResult) }}
            </el-alert>
          </div>

          <!-- 原始材料列表 -->
          <div class="raw-list">
            <div class="raw-list-header">
              <h4 class="raw-list-title">{{ t('knowledgeConfig.rawMaterials') }}</h4>
              <span class="raw-list-count">{{ rawMaterials.length }}</span>
              <el-button
                v-if="canManage && rawMaterials.some(r => r.processingStatus === 'pending')"
                type="primary"
                size="small"
                class="process-all-btn"
                @click="handleProcessAll"
              >
                <el-icon><VideoPlay /></el-icon>
                {{ t('knowledgeConfig.processAll') }}
              </el-button>
            </div>
            <div v-if="rawMaterials.length === 0" class="inline-empty">
              <div class="inline-empty-icon">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
              </div>
              <p>{{ t('knowledgeConfig.noRawMaterials') }}</p>
            </div>

            <div
              v-for="raw in rawMaterials"
              :key="raw.id"
              class="raw-item"
              :class="{ 'raw-item--active': selectedRawId === raw.id }"
              @click="toggleRawFilter(raw.id)"
            >
              <div class="raw-item-row">
                <div class="raw-item-info">
                  <span class="raw-item-title">{{ raw.title }}</span>
                  <el-tag size="small" effect="plain">{{ raw.sourceType }}</el-tag>
                </div>
                <div class="raw-item-meta">
                  <el-tag :type="rawStatusType(raw.processingStatus)" size="small">
                    {{ rawStatusLabel(raw.processingStatus) }}
                  </el-tag>
                  <span v-if="raw.pageCount" class="page-count-chip">
                    <el-icon><Document /></el-icon>
                    {{ raw.pageCount }}
                  </span>
                  <span v-if="raw.errorMessage && (raw.processingStatus === 'failed' || raw.processingStatus === 'partial')" class="error-hint" :title="raw.errorMessage">
                    {{ raw.errorMessage }}
                  </span>
                </div>
                <div class="raw-item-actions" @click.stop>
                  <el-button v-if="canManage && raw.processingStatus === 'processing'" size="small" text type="danger" @click="handleCancelRaw(raw)">
                    <el-icon><Close /></el-icon>
                  </el-button>
                  <el-button v-else-if="canManage && (raw.processingStatus === 'failed' || raw.processingStatus === 'partial' || raw.processingStatus === 'completed' || raw.processingStatus === 'cancelled')" size="small" text @click="handleReprocess(raw)">
                    <el-icon><RefreshRight /></el-icon>
                  </el-button>
                  <el-button size="small" text @click="handleDownloadRaw(raw)">
                    <el-icon><Download /></el-icon>
                  </el-button>
                  <el-button v-if="canManage" size="small" text type="danger" @click="handleDeleteRaw(raw)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
              </div>

              <!-- 处理进度条 -->
              <div v-if="raw.processingStatus === 'processing'" class="raw-progress">
                <el-progress
                  :percentage="raw.progressTotal ? Math.round((raw.progressDone / raw.progressTotal) * 100) : 0"
                  :indeterminate="!raw.progressTotal"
                  :stroke-width="4"
                  :show-text="false"
                />
                <span class="progress-label">
                  {{ raw.progressTotal ? `${raw.progressDone} / ${raw.progressTotal}` : t('knowledgeConfig.progressPreparing') }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- ===== Wiki 页面 Tab ===== -->
        <div v-if="activeTab === 'pages'" class="tab-panel pages-panel">
          <div v-if="!currentPage" class="inline-empty">
            <div class="inline-empty-icon">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/></svg>
            </div>
            <p>{{ t('knowledgeConfig.selectPage') }}</p>
          </div>
          <div v-else class="page-viewer">
            <div class="page-viewer-header">
              <div>
                <h2 class="page-viewer-title">{{ currentPage.title }}</h2>
                <div class="page-viewer-meta">
                  <el-tag v-if="currentPage.pageType" size="small" effect="plain" type="info">
                    {{ pageTypeLabel(currentPage.pageType) }}
                  </el-tag>
                  <span class="page-viewer-slug">{{ currentPage.slug }}</span>
                </div>
              </div>
              <div class="page-viewer-actions">
                <el-button v-if="canManage" size="small" type="danger" text @click="handleDeletePage(currentPage)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>
            <div v-if="currentPage.summary && !editingPage" class="page-summary">
              <el-alert type="info" :title="currentPage.summary" :closable="false" />
            </div>
            <div v-if="!editingPage" class="page-content markdown-body" v-html="renderedPageContent" />
            <template v-else>
              <el-input v-model="editContent" type="textarea" :rows="20" />
              <div class="page-edit-actions">
                <el-button size="small" @click="editingPage = false">{{ t('common.cancel') }}</el-button>
                <el-button size="small" type="primary" @click="savePageEdit">{{ t('common.save') }}</el-button>
              </div>
            </template>
            <!-- 编辑入口：跟随内容区，位于页面底部 -->
            <div v-if="canManage && !editingPage" class="page-edit-bar">
              <el-button size="small" @click="editingPage = true">
                <el-icon><EditPen /></el-icon>
                {{ t('common.edit') }}
              </el-button>
            </div>
          </div>
        </div>

        <!-- ===== 知识图谱 Tab ====== -->
        <div v-if="activeTab === 'graph'" class="tab-panel graph-panel" :class="{ fullscreen: graphFullscreen }">
          <div class="graph-toolbar">
            <div class="graph-toolbar-left">
              <span class="graph-stats">
                <span class="graph-stat-item">{{ graphStats.nodeCount }} {{ t('knowledgeConfig.nodes') }}</span>
                <span class="graph-stat-sep">·</span>
                <span class="graph-stat-item">{{ graphStats.edgeCount }} {{ t('knowledgeConfig.edges') }}</span>
                <span class="graph-stat-sep">·</span>
                <span class="graph-stat-item graph-stat-orphan">{{ graphStats.orphanCount }} {{ t('knowledgeConfig.orphanNodes') }}</span>
              </span>
              <el-checkbox v-model="graphShowOrphans" size="small">{{ t('knowledgeConfig.showOrphans') }}</el-checkbox>
              <el-select v-model="graphTypeFilter" size="small" clearable :placeholder="t('knowledgeConfig.filterByType')" style="width:120px">
                <el-option v-for="type in graphAvailableTypes" :key="type" :label="pageTypeLabel(type)" :value="type" />
              </el-select>
            </div>
            <div class="graph-toolbar-right">
              <el-button size="small" text @click="resetGraph">
                <el-icon><Refresh /></el-icon>
              </el-button>
              <el-button size="small" text @click="toggleGraphFullscreen">
                <el-icon><FullScreen /></el-icon>
              </el-button>
            </div>
          </div>
          <div ref="graphContainer" class="graph-canvas" />
          <div v-if="graphSelectedNode" class="graph-node-panel">
            <div class="node-panel-header">
              <h4>{{ graphSelectedNode.title }}</h4>
              <el-button size="small" text @click="graphSelectedNode = null">
                <el-icon><Close /></el-icon>
              </el-button>
            </div>
            <p class="node-panel-type">{{ pageTypeLabel(graphSelectedNode.pageType) }}</p>
            <p class="node-panel-slug">{{ graphSelectedNode.slug }}</p>
            <el-button size="small" type="primary" @click="openPage(graphSelectedNode); activeTab = 'pages'">
              {{ t('knowledgeConfig.viewPage') }}
            </el-button>
          </div>
        </div>

        <!-- ===== 处理配置 Tab ===== -->
        <div v-if="activeTab === 'config'" class="tab-panel config-panel">
          <div class="config-grid">
            <!-- Embedding Model -->
            <div class="config-card">
              <div class="config-card-head">
                <h4>{{ t('knowledgeConfig.embeddingModel') }}</h4>
                <el-button v-if="canManage" size="small" type="primary" :loading="configSavingEmbedding" @click="saveEmbeddingModel">
                  {{ t('common.save') }}
                </el-button>
              </div>
              <p class="config-desc">{{ t('knowledgeConfig.embeddingDesc') }}</p>
              <el-input v-model="configEmbeddingModelId" :disabled="!canManage" :placeholder="t('knowledgeConfig.modelIdPlaceholder')" />
            </div>

            <!-- Ingest Mode -->
            <div class="config-card">
              <div class="config-card-head">
                <h4>{{ t('knowledgeConfig.ingestMode') }}</h4>
                <el-button v-if="canManage" size="small" type="primary" :loading="configSavingIngest" @click="saveIngestMode">
                  {{ t('common.save') }}
                </el-button>
              </div>
              <el-radio-group v-model="configIngestMode" :disabled="!canManage">
                <el-radio-button value="eager">{{ t('knowledgeConfig.eager') }}</el-radio-button>
                <el-radio-button value="lazy">{{ t('knowledgeConfig.lazy') }}</el-radio-button>
              </el-radio-group>
              <p class="config-hint">{{ configIngestMode === 'eager' ? t('knowledgeConfig.eagerHint') : t('knowledgeConfig.lazyHint') }}</p>
            </div>

            <!-- Processing Rules -->
            <div class="config-card">
              <div class="config-card-head">
                <h4>{{ t('knowledgeConfig.processingRules') }}</h4>
                <el-button v-if="canManage" size="small" type="primary" @click="handleSaveConfig">
                  {{ t('common.save') }}
                </el-button>
              </div>
              <el-input
                v-model="kbConfigContent"
                type="textarea"
                :rows="12"
                :disabled="!canManage"
                :placeholder="t('knowledgeConfig.configPlaceholder')"
              />
            </div>

            <!-- Search Preview -->
            <div class="config-card">
              <div class="config-card-head">
                <h4>{{ t('knowledgeConfig.searchPreview') }}</h4>
              </div>
              <div class="search-preview-row">
                <el-input v-model="searchPreviewQuery" :placeholder="t('knowledgeConfig.searchPreviewPlaceholder')" @keyup.enter="runSearchPreview">
                  <template #append>
                    <el-button :loading="searchPreviewLoading" @click="runSearchPreview">
                      <el-icon><Search /></el-icon>
                    </el-button>
                  </template>
                </el-input>
                <el-select v-model="searchPreviewMode" size="small" style="width:120px">
                  <el-option label="hybrid" value="hybrid" />
                  <el-option label="semantic" value="semantic" />
                  <el-option label="keyword" value="keyword" />
                </el-select>
              </div>
              <div v-if="searchPreviewResults.length" class="search-preview-results">
                <div v-for="result in searchPreviewResults" :key="result.slug" class="search-result-item" @click="openPage({ slug: result.slug, title: result.title } as WikiPage); activeTab = 'pages'">
                  <span class="search-result-title">{{ result.title }}</span>
                  <span class="search-result-score">{{ result.score.toFixed(3) }}</span>
                  <p class="search-result-snippet">{{ result.snippet }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- ===== 加工器 Tab ===== -->
        <div v-if="activeTab === 'transformations'" class="tab-panel transformations-panel">
          <div class="panel-header">
            <div>
              <h4>{{ t('knowledgeConfig.transformations') }}</h4>
              <p class="panel-desc">{{ t('knowledgeConfig.transformationsDesc') }}</p>
            </div>
            <el-button v-if="canManage" type="primary" size="small" @click="openTransformEditor()">
              <el-icon><Plus /></el-icon> {{ t('knowledgeConfig.createTransformation') }}
            </el-button>
          </div>

          <div v-if="transformationLoading" class="panel-loading">
            <el-icon class="is-loading"><Loading /></el-icon>
          </div>
          <div v-else-if="transformations.length === 0" class="panel-empty">
            <el-icon :size="48"><SetUp /></el-icon>
            <p>{{ t('knowledgeConfig.noTransformations') }}</p>
          </div>

          <div v-else class="transformation-list">
            <div v-for="tpl in transformations" :key="tpl.id" class="transformation-card">
              <div class="tpl-header">
                <div class="tpl-title">{{ tpl.title || tpl.name }}</div>
                <div class="tpl-flags">
                  <el-tag v-if="tpl.applyDefault" size="small" type="success">default</el-tag>
                  <el-tag v-if="!tpl.enabled" size="small" type="info">disabled</el-tag>
                  <el-tag size="small" type="info">{{ tpl.outputFormat || 'markdown' }}</el-tag>
                </div>
              </div>
              <p v-if="tpl.description" class="tpl-desc">{{ tpl.description }}</p>

              <div class="tpl-actions">
                <el-select v-model="selectedRawForTransform[tpl.id]" size="small" clearable :placeholder="t('knowledgeConfig.selectRaw')" style="width:200px" :disabled="!canManage">
                  <el-option v-for="raw in rawMaterials.filter(r => r.processingStatus === 'completed' || r.processingStatus === 'partial')" :key="raw.id" :label="raw.title" :value="raw.id" />
                </el-select>
                <el-button v-if="canManage" size="small" type="primary" @click="onApplyTransform(tpl)">{{ t('knowledgeConfig.run') }}</el-button>
                <el-button v-if="canManage" size="small" @click="onAggregateTransform(tpl)">{{ t('knowledgeConfig.aggregate') }}</el-button>
                <el-button v-if="canManage" size="small" text @click="openTransformEditor(tpl)">
                  <el-icon><EditPen /></el-icon>
                </el-button>
                <el-button v-if="canManage" size="small" text type="danger" @click="onDeleteTransform(tpl)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>

              <!-- Runs -->
              <details class="runs-details">
                <summary>{{ t('knowledgeConfig.runs') }} ({{ (transformationRuns[tpl.id] || []).length }})</summary>
                <div class="runs-list">
                  <div v-for="run in (transformationRuns[tpl.id] || [])" :key="run.id" class="run-item">
                    <div class="run-header">
                      <el-tag :type="run.status === 'completed' ? 'success' : run.status === 'failed' ? 'danger' : run.status === 'running' ? 'warning' : 'info'" size="small">{{ run.status }}</el-tag>
                      <span class="run-time">{{ formatTimestamp(run.createTime) }}</span>
                      <span v-if="run.durationMs" class="run-duration">{{ formatDuration(run.durationMs) }}</span>
                      <span v-if="run.tokens" class="run-tokens">{{ run.tokens }} tokens</span>
                    </div>
                    <div v-if="run.output" class="run-output">
                      <pre>{{ run.output.substring(0, 500) }}{{ run.output.length > 500 ? '...' : '' }}</pre>
                    </div>
                    <div v-if="run.error" class="run-error">{{ run.error }}</div>
                    <div class="run-actions">
                      <el-button v-if="canManage && (run.status === 'running' || run.status === 'pending')" size="small" text @click="onCancelTransformRun(run.id)">{{ t('knowledgeConfig.cancel') }}</el-button>
                      <el-button v-if="canManage && run.status === 'completed' && run.output" size="small" text type="primary" @click="onSaveRunAsPage(run.id)">{{ t('knowledgeConfig.saveAsPage') }}</el-button>
                      <el-button v-if="canManage" size="small" text type="danger" @click="onDeleteTransformRun(run.id)">{{ t('knowledgeConfig.delete') }}</el-button>
                    </div>
                  </div>
                </div>
              </details>
            </div>
          </div>
        </div>

        <!-- ===== 近况快照 Tab ===== -->
        <div v-if="activeTab === 'hotCache'" class="tab-panel hotcache-panel">
          <div v-if="hotCacheLoading" class="panel-loading">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>{{ t('common.loading') }}</span>
          </div>
          <div v-else-if="!hotCache" class="panel-empty">
            <el-icon :size="48"><Clock /></el-icon>
            <p>{{ t('knowledgeConfig.noHotCache') }}</p>
            <el-button v-if="canManage" type="primary" :loading="hotCacheRegenerating" @click="onRegenerateHotCache">
              {{ t('knowledgeConfig.regenerate') }}
            </el-button>
          </div>
          <div v-else class="hot-cache-body">
            <div v-if="canManage" class="panel-actions">
              <el-button :loading="hotCacheRegenerating" @click="onRegenerateHotCache">
                <el-icon><Refresh /></el-icon> {{ t('knowledgeConfig.regenerate') }}
              </el-button>
              <el-button type="danger" text @click="onResetHotCache">
                {{ t('knowledgeConfig.reset') }}
              </el-button>
            </div>
            <div class="meta-grid">
              <div class="meta-item">
                <span class="meta-label">{{ t('knowledgeConfig.lastUpdated') }}</span>
                <span class="meta-value">{{ formatTimestamp(hotCache.lastUpdated) }}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">{{ t('knowledgeConfig.updateReason') }}</span>
                <span class="meta-value">{{ hotCache.updateReason || '-' }}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">{{ t('knowledgeConfig.rebuildCount') }}</span>
                <span class="meta-value">{{ hotCache.rebuildCount }}</span>
              </div>
              <div class="meta-item">
                <span class="meta-label">{{ t('knowledgeConfig.lastRebuildDuration') }}</span>
                <span class="meta-value">{{ formatDuration(hotCache.lastRebuildDurationMs) }}</span>
              </div>
            </div>
            <div v-if="hotCache.lastRebuildError" class="error-banner">
              <el-icon><Warning /></el-icon> {{ hotCache.lastRebuildError }}
            </div>
            <pre class="cache-content">{{ hotCache.content }}</pre>
          </div>
        </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ========== 新建知识库弹窗 ========== -->
    <el-dialog
      v-model="createDialogVisible"
      :title="t('knowledgeConfig.createTitle')"
      width="480px"
      destroy-on-close
    >
      <el-form :model="createForm" label-width="80px">
        <el-form-item :label="t('knowledgeConfig.fieldName')" required>
          <el-input v-model="createForm.name" :placeholder="t('knowledgeConfig.fieldNamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('knowledgeConfig.fieldDescription')">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            :placeholder="t('knowledgeConfig.fieldDescPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="createLoading" @click="handleCreate">
          {{ t('common.create') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- ========== 编辑知识库弹窗 ========== -->
    <el-dialog
      v-model="editDialogVisible"
      :title="t('knowledgeConfig.editTitle')"
      width="480px"
      destroy-on-close
    >
      <el-form :model="editForm" label-width="80px">
        <el-form-item :label="t('knowledgeConfig.fieldName')" required>
          <el-input v-model="editForm.name" :placeholder="t('knowledgeConfig.fieldNamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('knowledgeConfig.fieldDescription')">
          <el-input
            v-model="editForm.description"
            type="textarea"
            :rows="3"
            :placeholder="t('knowledgeConfig.fieldDescPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="editLoading" @click="handleEditSubmit">
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- ========== 添加文本材料弹窗 ========== -->
    <el-dialog
      v-model="showAddTextDialog"
      :title="t('knowledgeConfig.addTextTitle')"
      width="600px"
      destroy-on-close
    >
      <el-form :model="addTextForm" label-width="60px">
        <el-form-item :label="t('knowledgeConfig.colTitle')" required>
          <el-input v-model="addTextForm.title" />
        </el-form-item>
        <el-form-item :label="t('knowledgeConfig.colContent')">
          <el-input v-model="addTextForm.content" type="textarea" :rows="12" :placeholder="t('knowledgeConfig.pasteContent')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddTextDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="addTextLoading" @click="handleAddText">
          {{ t('common.add') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- ========== 转换模板编辑弹窗 ========== -->
    <el-dialog
      v-model="transformEditorOpen"
      :title="transformEditing ? t('knowledgeConfig.editTransformation') : t('knowledgeConfig.createTransformation')"
      width="640px"
      destroy-on-close
    >
      <el-form :model="transformForm" label-width="100px">
        <el-form-item :label="t('knowledgeConfig.transName')" required>
          <el-input v-model="transformForm.name" :placeholder="t('knowledgeConfig.transNamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('knowledgeConfig.transTitle')">
          <el-input v-model="transformForm.title" />
        </el-form-item>
        <el-form-item :label="t('knowledgeConfig.transDescription')">
          <el-input v-model="transformForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="t('knowledgeConfig.transPrompt')">
          <el-input v-model="transformForm.promptTemplate" type="textarea" :rows="6" placeholder="{{content}}" />
        </el-form-item>
        <el-form-item :label="t('knowledgeConfig.transModelId')">
          <el-input v-model="transformForm.modelId" placeholder="provider::modelName" />
        </el-form-item>
        <el-form-item :label="t('knowledgeConfig.transOutputTarget')">
          <el-radio-group v-model="transformForm.outputTarget">
            <el-radio-button value="none">{{ t('knowledgeConfig.outputNone') }}</el-radio-button>
            <el-radio-button value="page">{{ t('knowledgeConfig.outputPage') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('knowledgeConfig.transFormat')">
          <el-radio-group v-model="transformForm.outputFormat">
            <el-radio-button value="markdown">Markdown</el-radio-button>
            <el-radio-button value="json">JSON</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="transformForm.outputFormat === 'json'" :label="t('knowledgeConfig.transSchema')">
          <el-input v-model="transformForm.outputSchema" type="textarea" :rows="3" placeholder="JSON Schema" />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="transformForm.applyDefault">{{ t('knowledgeConfig.applyDefault') }}</el-checkbox>
          <el-checkbox v-model="transformForm.enabled">{{ t('knowledgeConfig.enabled') }}</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transformEditorOpen = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="transformSaving" @click="saveTransform">
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { usePermission, PERMISSION } from '@/composables/usePermission'
import * as echarts from 'echarts'
import {
  Plus, Delete, Loading, Document,
  Upload, Folder, Search, Close, RefreshRight, Download, VideoPlay,
  Share, SetUp, Clock, Grid, EditPen, Monitor, FullScreen, Refresh,
  ArrowDown, Warning,
} from '@element-plus/icons-vue'
import {
  listKBs, createKB, deleteKB, updateKB, processKB, getConfig, updateConfig,
  listRaw, addRawText, uploadRaw, deleteRaw, reprocessRaw, cancelRaw,
  listPages, getPage, updatePage, deletePage, subscribeProgress,
  scanDirectory, setSourceDirectory, downloadRaw,
  listTransformations, createTransformation, updateTransformation, deleteTransformation,
  applyTransformation, aggregateTransformation, listTransformationRuns,
  cancelTransformationRun, saveRunAsPage, deleteTransformationRun,
  getHotCache, regenerateHotCache, resetHotCache,
  getRelatedPages, getPageCitations, enrichPage, repairPage, searchPreview, getKBStats,
} from '@/api/knowledge'
import type {
  KnowledgeBase, RawMaterial, WikiPage, WikiTransformation,
  WikiTransformationRun, WikiHotCache, RelatedPageResult, PageSearchResult,
} from '@/api/knowledge'

const { t } = useI18n()
const { hasPermission } = usePermission()

/** 是否可管理知识库（管理员/工作区 admin+owner，全局管理员自动放行），否则只读 */
const canManage = computed(() => hasPermission(PERMISSION.KNOWLEDGE_MANAGE))

const KB_STORAGE_KEY = 'mateclaw_kb_state'

function loadKBState(): { kbId: string | null; tab: string } {
  try {
    const raw = localStorage.getItem(KB_STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw)
      return { kbId: parsed.kbId ?? null, tab: parsed.tab ?? 'raw' }
    }
  } catch { /* ignore */ }
  return { kbId: null, tab: 'raw' }
}

function saveKBState(kbId: string | null, tab: string) {
  try {
    localStorage.setItem(KB_STORAGE_KEY, JSON.stringify({ kbId, tab }))
  } catch { /* ignore */ }
}

function clearKBState() {
  try {
    localStorage.removeItem(KB_STORAGE_KEY)
  } catch { /* ignore */ }
}

// ==================== State ====================
const loading = ref(false)
/** 首次加载完成后才显示标题旁数量统计，避免首帧 0 闪烁 */
const statsReady = ref(false)
const knowledgeBases = ref<KnowledgeBase[]>([])
const currentKB = ref<KnowledgeBase | null>(null)
const activeTab = ref('raw')
const rawMaterials = ref<RawMaterial[]>([])
const pages = ref<WikiPage[]>([])
const currentPage = ref<WikiPage | null>(null)
const selectedRawId = ref<string | null>(null)
const pageSearchQuery = ref('')
const kbConfigContent = ref('')
const editingPage = ref(false)
const editContent = ref('')
const isDragging = ref(false)
const scanPath = ref('')
const scanning = ref(false)
const scanResult = ref<{ scanned: number; added: number; skipped: number } | null>(null)

// SSE
let sse: EventSource | null = null
let fallbackTimer: number | null = null

// ==================== Graph ====================
const graphChart = ref<echarts.ECharts | null>(null)
const graphContainer = ref<HTMLDivElement | null>(null)
const graphLoading = ref(false)
const graphFullscreen = ref(false)
const graphShowOrphans = ref(true)
const graphTypeFilter = ref<string | null>(null)
const graphAvailableTypes = ref<string[]>([])
const graphSelectedNode = ref<WikiPage | null>(null)
const graphStats = ref({ nodeCount: 0, edgeCount: 0, orphanCount: 0 })

// ==================== Config ====================
const configEmbeddingModelId = ref<string | null>(null)
const configIngestMode = ref<'eager' | 'lazy'>('eager')
const configSavingEmbedding = ref(false)
const configSavingIngest = ref(false)
const searchPreviewQuery = ref('')
const searchPreviewMode = ref('hybrid')
const searchPreviewResults = ref<PageSearchResult[]>([])
const searchPreviewLoading = ref(false)

// ==================== Transformations ====================
const transformations = ref<WikiTransformation[]>([])
const transformationRuns = ref<Record<string, WikiTransformationRun[]>>({})
const transformationLoading = ref(false)
const selectedRawForTransform = ref<Record<string, string>>({})
const transformEditorOpen = ref(false)
const transformEditing = ref<WikiTransformation | null>(null)
const transformSaving = ref(false)
const transformForm = ref<Record<string, unknown>>({})

// ==================== HotCache ====================
const hotCache = ref<WikiHotCache | null>(null)
const hotCacheLoading = ref(false)
const hotCacheRegenerating = ref(false)

// ==================== KB Stats ====================
const kbStats = ref<Record<string, unknown> | null>(null)

// Tabs
const tabs = [
  { key: 'raw', label: t('knowledgeConfig.tabRaw') },
  { key: 'pages', label: t('knowledgeConfig.tabPages') },
  { key: 'graph', label: t('knowledgeConfig.tabGraph') },
  { key: 'config', label: t('knowledgeConfig.tabConfig') },
  { key: 'transformations', label: t('knowledgeConfig.tabTransformations') },
  { key: 'hotCache', label: t('knowledgeConfig.tabHotCache') },
]
const visibleTabs = computed(() => tabs)

function pageTypeLabel(type?: string | null) {
  const value = type || 'other'
  return t(`knowledgeConfig.pageType.${value}`, value)
}

// ==================== Computed ====================
const filteredPages = computed(() => {
  if (!pageSearchQuery.value) return pages.value
  const q = pageSearchQuery.value.toLowerCase()
  return pages.value.filter(p => p.title.toLowerCase().includes(q))
})

const groupedSidebarPages = computed(() => {
  const groups = new Map<string, WikiPage[]>()
  const list = filteredPages.value
  for (const page of list) {
    const type = pageTypeLabel(page.pageType)
    if (!groups.has(type)) {
      groups.set(type, [])
    }
    groups.get(type)!.push(page)
  }
  return groups
})

/** el-collapse 展开分组：新分组默认展开；用户手动收起的状态保留，搜索过滤重算分组时不丢失 */
const expandedGroups = ref<string[]>([])
const seenSidebarGroups = new Set<string>()
watch(groupedSidebarPages, (groups) => {
  const types = [...groups.keys()]
  for (const type of types) {
    if (!seenSidebarGroups.has(type)) {
      seenSidebarGroups.add(type)
      expandedGroups.value.push(type)
    }
  }
  expandedGroups.value = expandedGroups.value.filter((t) => types.includes(t))
}, { immediate: true })

const renderedPageContent = computed(() => {
  if (!currentPage.value?.content) return ''
  // Simple markdown-like rendering for [[links]]
  let content = currentPage.value.content
  content = content.replace(/\[\[([^\]]+)\]\]/g, '<a class="wiki-link">$1</a>')
  // Convert markdown headers, lists, etc. (simplified)
  content = content.replace(/^### (.*$)/gim, '<h3>$1</h3>')
  content = content.replace(/^## (.*$)/gim, '<h2>$1</h2>')
  content = content.replace(/^# (.*$)/gim, '<h1>$1</h1>')
  content = content.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
  content = content.replace(/\*(.*?)\*/g, '<em>$1</em>')
  content = content.replace(/^\- (.*$)/gim, '<li>$1</li>')
  content = content.replace(/\n/g, '<br>')
  return content
})

// ==================== Status Helpers ====================
function statusLabel(status: string) {
  const map: Record<string, string> = {
    active: t('knowledgeConfig.statusActive'),
    processing: t('knowledgeConfig.statusProcessing'),
    error: t('knowledgeConfig.statusError'),
  }
  return map[status] || status
}

function statusTagType(status: string) {
  const map: Record<string, string> = {
    active: 'success',
    processing: 'warning',
    error: 'danger',
  }
  return map[status] || 'info'
}

function rawStatusType(status: string) {
  const map: Record<string, string> = {
    completed: 'success',
    processing: 'warning',
    failed: 'danger',
    pending: 'info',
    partial: 'warning',
    cancelled: 'info',
  }
  return map[status] || 'info'
}

function rawStatusLabel(status: string) {
  const map: Record<string, string> = {
    completed: t('knowledgeConfig.statusCompleted'),
    processing: t('knowledgeConfig.statusProcessing'),
    failed: t('knowledgeConfig.statusFailed'),
    pending: t('knowledgeConfig.statusPending'),
    partial: t('knowledgeConfig.statusPartial'),
    cancelled: t('knowledgeConfig.statusCancelled'),
  }
  return map[status] || status
}

// ==================== KB Library ====================
const libraryRef = ref<HTMLElement | null>(null)
/** 描述两行截断状态映射（kbId → 是否截断），仅截断的描述悬停才弹 tooltip */
const truncatedDescs = ref<Record<string, boolean>>({})

/** 测量卡片描述是否被截断（line-clamp 裁切后 scrollHeight > clientHeight） */
function measureDescTruncation(): void {
  const root = libraryRef.value
  if (!root) return
  const map: Record<string, boolean> = {}
  root.querySelectorAll<HTMLElement>('.kb-card-desc').forEach((el) => {
    const id = el.dataset.id
    if (id) map[id] = el.scrollHeight > el.clientHeight
  })
  truncatedDescs.value = map
}

/** 窗口尺寸变化会改变卡片宽度与换行数，需重新测量 */
function handleKbWindowResize(): void {
  measureDescTruncation()
}

watch(knowledgeBases, () => {
  nextTick(() => measureDescTruncation())
})

async function fetchKBs() {
  loading.value = true
  try {
    knowledgeBases.value = await listKBs()
    const saved = loadKBState()
    if (saved.kbId) {
      const savedKb = knowledgeBases.value.find(k => String(k.id) === saved.kbId)
      if (savedKb) {
        activeTab.value = saved.tab || 'raw'
        await selectKB(savedKb)
      }
    }
  } catch {
    /* 错误已由拦截器处理 */
  } finally {
    loading.value = false
    statsReady.value = true
  }
}

async function selectKB(kb: KnowledgeBase) {
  currentKB.value = kb
  selectedRawId.value = null
  currentPage.value = null
  saveKBState(String(kb.id), activeTab.value)
  await Promise.all([fetchRawMaterials(kb.id), fetchPages(kb.id), fetchKBConfig(kb.id)])
  // 启动 SSE
  setupSSE(kb.id)
}

function backToLibrary() {
  closeSSE()
  currentKB.value = null
  currentPage.value = null
  rawMaterials.value = []
  pages.value = []
  activeTab.value = 'raw'
  clearKBState()
}

// ==================== Raw Materials ====================
async function fetchRawMaterials(kbId: string) {
  try {
    rawMaterials.value = await listRaw(kbId)
  } catch {
    rawMaterials.value = []
  }
}

async function fetchPages(kbId: string, rawId?: string) {
  try {
    pages.value = await listPages(kbId, rawId)
  } catch {
    pages.value = []
  }
}

async function fetchKBConfig(kbId: string) {
  try {
    const result = await getConfig(kbId)
    kbConfigContent.value = result.content || ''
  } catch {
    kbConfigContent.value = ''
  }
}

function toggleRawFilter(rawId: string) {
  if (selectedRawId.value === rawId) {
    selectedRawId.value = null
    if (currentKB.value) fetchPages(currentKB.value.id)
  } else {
    selectedRawId.value = rawId
    if (currentKB.value) fetchPages(currentKB.value.id, rawId)
  }
}

// ==================== SSE Progress ====================
function setupSSE(kbId: string) {
  closeSSE()
  try {
    sse = subscribeProgress(kbId)

    sse.addEventListener('raw.started', (ev: MessageEvent) => {
      try {
        const data = JSON.parse(ev.data)
        const raw = rawMaterials.value.find(r => r.id === String(data.rawId))
        if (raw) {
          raw.processingStatus = 'processing'
          raw.progressDone = 0
          raw.progressTotal = 0
        }
      } catch { /* ignore */ }
    })

    sse.addEventListener('route.done', (ev: MessageEvent) => {
      try {
        const data = JSON.parse(ev.data)
        const raw = rawMaterials.value.find(r => r.id === String(data.rawId))
        if (raw && typeof data.total === 'number') {
          raw.progressTotal = data.total
        }
      } catch { /* ignore */ }
    })

    sse.addEventListener('chunk.done', (ev: MessageEvent) => {
      try {
        const data = JSON.parse(ev.data)
        const raw = rawMaterials.value.find(r => r.id === String(data.rawId))
        if (raw && typeof data.done === 'number') {
          raw.progressDone = data.done
        }
      } catch { /* ignore */ }
    })

    sse.addEventListener('raw.completed', (ev: MessageEvent) => {
      try {
        const data = JSON.parse(ev.data)
        const raw = rawMaterials.value.find(r => r.id === String(data.rawId))
        if (raw) {
          raw.processingStatus = data.status === 'partial' ? 'partial' : 'completed'
          if (typeof data.totalPages === 'number') {
            raw.progressDone = data.totalPages
            raw.progressTotal = data.totalPages
          }
        }
        if (currentKB.value) {
          fetchRawMaterials(currentKB.value.id)
          fetchPages(currentKB.value.id)
        }
      } catch { /* ignore */ }
    })

    sse.addEventListener('raw.failed', (ev: MessageEvent) => {
      try {
        const data = JSON.parse(ev.data)
        const raw = rawMaterials.value.find(r => r.id === String(data.rawId))
        if (raw) raw.processingStatus = 'failed'
        if (currentKB.value) fetchRawMaterials(currentKB.value.id)
      } catch { /* ignore */ }
    })

    sse.onerror = () => {
      // EventSource auto-reconnects
    }
  } catch (e) {
    console.error('SSE setup failed:', e)
  }

  // Fallback polling
  fallbackTimer = window.setInterval(() => {
    if (currentKB.value) {
      fetchRawMaterials(currentKB.value.id)
    }
  }, 60000)
}

function closeSSE() {
  if (sse) {
    sse.close()
    sse = null
  }
  if (fallbackTimer) {
    clearInterval(fallbackTimer)
    fallbackTimer = null
  }
}

// ==================== File Upload ====================
const fileInput = ref<HTMLInputElement | null>(null)

function triggerFileInput() {
  fileInput.value?.click()
}

async function handleFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  if (!input.files || !currentKB.value) return
  await Promise.all(Array.from(input.files).map(f => uploadFile(f)))
  input.value = ''
}

async function handleDrop(event: DragEvent) {
  isDragging.value = false
  if (!event.dataTransfer?.files || !currentKB.value) return
  await Promise.all(Array.from(event.dataTransfer.files).map(f => uploadFile(f)))
}

async function uploadFile(file: File) {
  if (!currentKB.value) return
  try {
    await uploadRaw(currentKB.value.id, file)
    ElMessage.success(t('knowledgeConfig.uploadSuccess', { name: file.name }))
    await fetchRawMaterials(currentKB.value.id)
  } catch {
    /* 错误已由拦截器处理 */
  }
}

// ==================== Actions ====================
async function handleReprocess(raw: RawMaterial) {
  if (!currentKB.value) return
  try {
    await reprocessRaw(currentKB.value.id, raw.id)
    ElMessage.success(t('knowledgeConfig.reprocessTriggered'))
    await fetchRawMaterials(currentKB.value.id)
  } catch { /* */ }
}

async function handleCancelRaw(raw: RawMaterial) {
  if (!currentKB.value) return
  try {
    await cancelRaw(currentKB.value.id, raw.id)
    ElMessage.success(t('knowledgeConfig.cancelSuccess'))
    await fetchRawMaterials(currentKB.value.id)
  } catch { /* */ }
}

async function handleDeleteRaw(raw: RawMaterial) {
  if (!currentKB.value) return
  try {
    await ElMessageBox.confirm(
      t('knowledgeConfig.deleteRawConfirm', { title: raw.title }),
      t('common.confirm'),
      { type: 'warning' }
    )
    await deleteRaw(currentKB.value.id, raw.id)
    ElMessage.success(t('knowledgeConfig.deleteSuccess'))
    await fetchRawMaterials(currentKB.value.id)
  } catch { /* */ }
}

async function handleDownloadRaw(raw: RawMaterial) {
  if (!currentKB.value) return
  try {
    const blob = await downloadRaw(currentKB.value.id, raw.id) as unknown as Blob
    let filename = raw.title || `raw-${raw.id}`
    if (!filename.includes('.')) filename += '.txt'
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    a.remove()
    setTimeout(() => URL.revokeObjectURL(url), 0)
  } catch {
    ElMessage.error(t('knowledgeConfig.downloadFailed'))
  }
}

async function handleProcessAll() {
  if (!currentKB.value) return
  try {
    await processKB(currentKB.value.id)
    ElMessage.success(t('knowledgeConfig.processTriggered'))
    await fetchRawMaterials(currentKB.value.id)
  } catch { /* */ }
}

async function handleScanDir() {
  if (!currentKB.value || !scanPath.value.trim()) return
  scanning.value = true
  try {
    await setSourceDirectory(currentKB.value.id, scanPath.value.trim())
    const result = await scanDirectory(currentKB.value.id)
    scanResult.value = result
    ElMessage.success(t('knowledgeConfig.scanSuccess', result))
    await fetchRawMaterials(currentKB.value.id)
  } catch {
    scanResult.value = null
  } finally {
    scanning.value = false
  }
}

// ==================== Page Actions ====================
async function openPage(page: WikiPage) {
  if (!currentKB.value) return
  try {
    const detail = await getPage(currentKB.value.id, page.slug)
    currentPage.value = detail
    editContent.value = detail.content || ''
    editingPage.value = false
  } catch { /* */ }
}

async function savePageEdit() {
  if (!currentKB.value || !currentPage.value) return
  try {
    await updatePage(currentKB.value.id, currentPage.value.slug, editContent.value)
    ElMessage.success(t('knowledgeConfig.pageSaved'))
    const detail = await getPage(currentKB.value.id, currentPage.value.slug)
    currentPage.value = detail
    editingPage.value = false
  } catch { /* */ }
}

async function handleDeletePage(page: WikiPage) {
  if (!currentKB.value) return
  try {
    await ElMessageBox.confirm(
      t('knowledgeConfig.deletePageConfirm', { title: page.title }),
      t('common.confirm'),
      { type: 'warning' }
    )
    await deletePage(currentKB.value.id, page.slug)
    ElMessage.success(t('knowledgeConfig.deleteSuccess'))
    currentPage.value = null
    await fetchPages(currentKB.value.id)
  } catch { /* */ }
}

// ==================== KB CRUD ====================
const createDialogVisible = ref(false)
const createLoading = ref(false)
const createForm = ref({ name: '', description: '' })

function openCreateDialog() {
  createForm.value = { name: '', description: '' }
  createDialogVisible.value = true
}

async function handleCreate() {
  if (!createForm.value.name.trim()) {
    ElMessage.warning(t('knowledgeConfig.nameRequired'))
    return
  }
  createLoading.value = true
  try {
    await createKB(createForm.value)
    ElMessage.success(t('knowledgeConfig.createSuccess'))
    createDialogVisible.value = false
    await fetchKBs()
  } catch { /* */ } finally {
    createLoading.value = false
  }
}

// ==================== Edit KB ====================
/** 编辑知识库弹窗状态 */
const editDialogVisible = ref(false)
const editLoading = ref(false)
const editForm = ref({ id: '', name: '', description: '' })

/** 打开编辑知识库弹窗 */
function openEditDialog(kb: KnowledgeBase): void {
  editForm.value = { id: String(kb.id), name: kb.name || '', description: kb.description || '' }
  editDialogVisible.value = true
}

/** 保存知识库名称/描述修改 */
async function handleEditSubmit() {
  if (!editForm.value.name.trim()) {
    ElMessage.warning(t('knowledgeConfig.nameRequired'))
    return
  }
  editLoading.value = true
  try {
    await updateKB(editForm.value.id, {
      name: editForm.value.name.trim(),
      description: editForm.value.description,
    })
    ElMessage.success(t('knowledgeConfig.updateSuccess'))
    editDialogVisible.value = false
    await fetchKBs()
    // 若编辑的是当前打开的知识库，同步工作区头部信息
    if (currentKB.value && String(currentKB.value.id) === editForm.value.id) {
      currentKB.value = {
        ...currentKB.value,
        name: editForm.value.name.trim(),
        description: editForm.value.description,
      }
    }
  } catch { /* */ } finally {
    editLoading.value = false
  }
}

async function handleDelete(kb: KnowledgeBase) {
  try {
    await ElMessageBox.confirm(
      t('knowledgeConfig.deleteConfirm', { name: kb.name }),
      t('common.confirm'),
      { type: 'warning' }
    )
    await deleteKB(kb.id)
    ElMessage.success(t('knowledgeConfig.deleteSuccess'))
    await fetchKBs()
  } catch { /* */ }
}

async function handleSaveConfig() {
  if (!currentKB.value) return
  try {
    await updateConfig(currentKB.value.id, kbConfigContent.value)
    ElMessage.success(t('knowledgeConfig.configSaved'))
  } catch { /* */ }
}

// ==================== Add Text ====================
const showAddTextDialog = ref(false)
const addTextLoading = ref(false)
const addTextForm = ref({ title: '', content: '' })

async function handleAddText() {
  if (!addTextForm.value.title.trim() || !currentKB.value) {
    ElMessage.warning(t('knowledgeConfig.titleRequired'))
    return
  }
  addTextLoading.value = true
  try {
    await addRawText(currentKB.value.id, addTextForm.value.title, addTextForm.value.content)
    ElMessage.success(t('knowledgeConfig.addSuccess'))
    showAddTextDialog.value = false
    addTextForm.value = { title: '', content: '' }
    await fetchRawMaterials(currentKB.value.id)
  } catch { /* */ } finally {
    addTextLoading.value = false
  }
}

// ==================== Graph ====================
function buildGraphData() {
  const allPages = pages.value
  const slugToPage = new Map(allPages.map(p => [p.slug, p]))
  const canonical = (s: string) => s.replace(/[-_]/g, '').toLowerCase()
  const canonicalToSlug = new Map<string, string>()
  allPages.forEach(p => { canonicalToSlug.set(canonical(p.slug), p.slug) })

  const titleToSlug = new Map<string, string>()
  allPages.forEach(p => {
    titleToSlug.set(p.title.toLowerCase(), p.slug)
    titleToSlug.set(canonical(p.title), p.slug)
  })

  function resolveLink(link: string): string | null {
    const slug = link.toLowerCase().trim()
    if (slugToPage.has(slug)) return slug
    const c = canonical(slug)
    if (canonicalToSlug.has(c)) return canonicalToSlug.get(c)!
    if (titleToSlug.has(slug)) return titleToSlug.get(slug)!
    return null
  }

  const edges: { source: string; target: string }[] = []
  const inDegree = new Map<string, number>()
  const outDegree = new Map<string, number>()
  allPages.forEach(p => {
    inDegree.set(p.slug, 0)
    outDegree.set(p.slug, 0)
  })

  allPages.forEach(page => {
    if (!page.outgoingLinks) return
    try {
      const links: string[] = JSON.parse(page.outgoingLinks)
      links.forEach(link => {
        const target = resolveLink(link)
        if (target && target !== page.slug) {
          edges.push({ source: page.slug, target })
          inDegree.set(target, (inDegree.get(target) || 0) + 1)
          outDegree.set(page.slug, (outDegree.get(page.slug) || 0) + 1)
        }
      })
    } catch { /* ignore */ }
  })

  const orphanSlugs = new Set(
    allPages.filter(p => {
      const out = outDegree.get(p.slug) || 0
      const inn = inDegree.get(p.slug) || 0
      return out === 0 && inn === 0
    }).map(p => p.slug)
  )

  let filteredPages = graphShowOrphans.value
    ? allPages
    : allPages.filter(p => !orphanSlugs.has(p.slug))

  if (graphTypeFilter.value) {
    filteredPages = filteredPages.filter(p => p.pageType === graphTypeFilter.value)
  }

  graphAvailableTypes.value = Array.from(new Set(allPages.map(p => p.pageType).filter(Boolean) as string[])).sort()

  const TYPE_COLORS: Record<string, string> = {
    concept: '#4176E6', technology: '#409eff', process: '#67c23a',
    person: '#e6a23c', organization: '#909399', product: '#9254de',
    place: '#36cfc9', event: '#ff4d4f', term: '#ff9c6e', other: '#b37feb',
  }

  const nodes = filteredPages.map(p => {
    const out = outDegree.get(p.slug) || 0
    const inn = inDegree.get(p.slug) || 0
    const degree = out + inn
    return {
      id: p.slug,
      name: p.title || p.slug,
      symbolSize: Math.max(20, Math.min(60, 20 + degree * 6)),
      itemStyle: { color: TYPE_COLORS[p.pageType || 'other'] || '#909399' },
      label: { show: degree > 1 || filteredPages.length < 30, fontSize: 11 },
      value: degree,
      page: p,
    }
  })

  const validSlugs = new Set(nodes.map(n => n.id))
  const validEdges = edges
    .filter(e => validSlugs.has(e.source) && validSlugs.has(e.target))
    .map(e => ({ source: e.source, target: e.target }))

  graphStats.value = {
    nodeCount: nodes.length,
    edgeCount: validEdges.length,
    orphanCount: graphShowOrphans.value ? orphanSlugs.size : 0,
  }

  return { nodes, edges: validEdges, orphanCount: orphanSlugs.size }
}

function initGraphChart() {
  if (!graphContainer.value) return
  if (graphChart.value) return
  graphChart.value = echarts.init(graphContainer.value, undefined, { renderer: 'canvas' })
  graphChart.value.on('click', (params: any) => {
    if (params.dataType === 'node' && params.data.page) {
      graphSelectedNode.value = params.data.page as WikiPage
    }
  })
}

function doRenderGraph() {
  if (!graphContainer.value || activeTab.value !== 'graph') return

  const rect = graphContainer.value.getBoundingClientRect()
  if (rect.width === 0 || rect.height === 0) {
    return
  }

  initGraphChart()
  if (!graphChart.value) return

  const { nodes, edges } = buildGraphData()
  graphChart.value.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        if (params.dataType === 'node') {
          const p: WikiPage = params.data.page
          const summary = (p.summary || '').substring(0, 80)
          const ellipsis = (p.summary || '').length > 80 ? '…' : ''
          return `<div style="max-width:220px;word-break:break-all;white-space:normal">
                    <strong style="display:block;margin-bottom:2px">${p.title}</strong>
                    <small style="color:#999;display:block;margin-bottom:4px">${pageTypeLabel(p.pageType)}</small>
                    <span style="font-size:11px;line-height:1.5;display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden">${summary}${ellipsis}</span>
                  </div>`
        }
        return ''
      },
    },
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      data: nodes,
      links: edges,
      label: {
        show: true,
        position: 'right',
        fontSize: 10,
        color: '#4e5969',
        distance: 4,
      },
      force: {
        repulsion: 220,
        gravity: 0.06,
        edgeLength: [60, 180],
        friction: 0.55,
      },
      lineStyle: {
        color: 'rgba(150,150,150,0.35)',
        curveness: 0.08,
        width: 1,
      },
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: 6,
      emphasis: {
        focus: 'adjacency',
        lineStyle: { width: 2 },
      },
    }],
  }, { notMerge: true, lazyUpdate: true })
}

let graphRenderTimer: number | null = null

function renderGraph() {
  if (graphRenderTimer) {
    clearTimeout(graphRenderTimer)
    graphRenderTimer = null
  }
  if (!graphContainer.value || activeTab.value !== 'graph') return
  const rect = graphContainer.value.getBoundingClientRect()
  if (rect.width === 0 || rect.height === 0) {
    graphRenderTimer = window.setTimeout(() => {
      graphRenderTimer = null
      renderGraph()
    }, 150)
    return
  }
  doRenderGraph()
}

function resetGraph() {
  graphSelectedNode.value = null
  renderGraph()
}

function toggleGraphFullscreen() {
  graphFullscreen.value = !graphFullscreen.value
  nextTick(() => {
    requestAnimationFrame(() => {
      graphChart.value?.resize()
    })
  })
}

watch([() => pages.value.length, graphShowOrphans, graphTypeFilter], () => {
  if (activeTab.value === 'graph') {
    nextTick(() => {
      renderGraph()
    })
  }
})

watch(activeTab, (tab, oldTab) => {
  if (tab === 'graph') {
    nextTick(() => {
      renderGraph()
    })
  }
  if (oldTab === 'graph' && tab !== 'graph') {
    if (graphChart.value) {
      graphChart.value.dispose()
      graphChart.value = null
    }
    if (graphRenderTimer) {
      clearTimeout(graphRenderTimer)
      graphRenderTimer = null
    }
  }
})

function onGraphResize() {
  if (activeTab.value === 'graph') {
    graphChart.value?.resize()
  }
}

// ==================== Config Panel ====================
async function loadConfigPanel() {
  if (!currentKB.value) return
  const kb = currentKB.value
  configEmbeddingModelId.value = kb.embeddingModelId
  try {
    const cfg = await getConfig(kb.id)
    if (cfg.content) {
      try {
        const parsed = JSON.parse(cfg.content)
        configIngestMode.value = parsed.ingestMode || 'eager'
      } catch {
        configIngestMode.value = 'eager'
      }
    }
  } catch { /* */ }
}

async function saveEmbeddingModel() {
  if (!currentKB.value) return
  configSavingEmbedding.value = true
  try {
    await updateKB(currentKB.value.id, { embeddingModelId: configEmbeddingModelId.value })
    ElMessage.success(t('knowledgeConfig.configSaved'))
  } catch { /* */ } finally {
    configSavingEmbedding.value = false
  }
}

async function saveIngestMode() {
  if (!currentKB.value) return
  configSavingIngest.value = true
  try {
    const cfg = await getConfig(currentKB.value.id)
    let parsed: Record<string, unknown> = {}
    try { parsed = JSON.parse(cfg.content || '{}') } catch { /* */ }
    parsed.ingestMode = configIngestMode.value
    await updateConfig(currentKB.value.id, JSON.stringify(parsed))
    ElMessage.success(t('knowledgeConfig.configSaved'))
  } catch { /* */ } finally {
    configSavingIngest.value = false
  }
}

async function runSearchPreview() {
  if (!currentKB.value || !searchPreviewQuery.value.trim()) return
  searchPreviewLoading.value = true
  try {
    searchPreviewResults.value = await searchPreview(
      currentKB.value.id, searchPreviewQuery.value.trim(),
      searchPreviewMode.value, 5
    )
  } catch {
    searchPreviewResults.value = []
  } finally {
    searchPreviewLoading.value = false
  }
}

// ==================== Transformations ====================
async function loadTransformations() {
  if (!currentKB.value) return
  transformationLoading.value = true
  try {
    const list = await listTransformations(currentKB.value.id)
    transformations.value = list
    const runsMap: Record<string, WikiTransformationRun[]> = {}
    for (const tpl of list) {
      const runs = await listTransformationRuns(tpl.id)
      runsMap[tpl.id] = runs
    }
    transformationRuns.value = runsMap
  } catch {
    transformations.value = []
    transformationRuns.value = {}
  } finally {
    transformationLoading.value = false
  }
}

function openTransformEditor(tpl?: WikiTransformation) {
  transformEditing.value = tpl || null
  transformForm.value = tpl ? {
    name: tpl.name,
    title: tpl.title,
    description: tpl.description,
    promptTemplate: tpl.promptTemplate,
    applyDefault: !!tpl.applyDefault,
    enabled: !!tpl.enabled,
    modelId: tpl.modelId,
    outputTarget: tpl.outputTarget || 'none',
    outputFormat: tpl.outputFormat || 'markdown',
    outputSchema: tpl.outputSchema,
  } : {
    name: '', title: '', description: '', promptTemplate: '',
    applyDefault: false, enabled: true, modelId: null,
    outputTarget: 'none', outputFormat: 'markdown', outputSchema: '',
  }
  transformEditorOpen.value = true
}

async function saveTransform() {
  if (!currentKB.value || !transformForm.value.name) return
  transformSaving.value = true
  try {
    const data = { ...transformForm.value, kbId: currentKB.value.id }
    if (transformEditing.value) {
      await updateTransformation(transformEditing.value.id, data)
    } else {
      await createTransformation(currentKB.value.id, data)
    }
    ElMessage.success(t('knowledgeConfig.configSaved'))
    transformEditorOpen.value = false
    await loadTransformations()
  } catch { /* */ } finally {
    transformSaving.value = false
  }
}

async function onDeleteTransform(tpl: WikiTransformation) {
  try {
    await ElMessageBox.confirm(
      t('knowledgeConfig.deleteConfirm', { name: tpl.name || tpl.title }),
      t('common.confirm'), { type: 'warning' }
    )
    await deleteTransformation(tpl.id)
    ElMessage.success(t('knowledgeConfig.deleteSuccess'))
    await loadTransformations()
  } catch { /* */ }
}

async function onApplyTransform(tpl: WikiTransformation) {
  const rawId = selectedRawForTransform.value[tpl.id]
  if (!rawId) {
    ElMessage.warning(t('knowledgeConfig.selectRawFirst'))
    return
  }
  try {
    await applyTransformation(tpl.id, { rawId })
    ElMessage.success(t('knowledgeConfig.transformTriggered'))
    await loadTransformations()
  } catch { /* */ }
}

async function onAggregateTransform(tpl: WikiTransformation) {
  if (!currentKB.value) return
  try {
    await aggregateTransformation(tpl.id, currentKB.value.id)
    ElMessage.success(t('knowledgeConfig.aggregateSuccess'))
    await loadTransformations()
    if (currentKB.value) await fetchPages(currentKB.value.id)
  } catch { /* */ }
}

async function onCancelTransformRun(runId: string) {
  try {
    await cancelTransformationRun(runId)
    ElMessage.success(t('knowledgeConfig.cancelSuccess'))
    await loadTransformations()
  } catch { /* */ }
}

async function onSaveRunAsPage(runId: string) {
  try {
    await saveRunAsPage(runId)
    ElMessage.success(t('knowledgeConfig.saveAsPageSuccess'))
    if (currentKB.value) await fetchPages(currentKB.value.id)
  } catch { /* */ }
}

async function onDeleteTransformRun(runId: string) {
  try {
    await deleteTransformationRun(runId)
    await loadTransformations()
  } catch { /* */ }
}

function formatDuration(ms: number | null): string {
  if (!ms) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

function formatTimestamp(iso: string | null): string {
  if (!iso) return '-'
  return new Date(iso).toLocaleString('zh-CN')
}

// ==================== HotCache ====================
async function loadHotCache() {
  if (!currentKB.value) return
  hotCacheLoading.value = true
  try {
    hotCache.value = await getHotCache(currentKB.value.id)
  } catch {
    hotCache.value = null
  } finally {
    hotCacheLoading.value = false
  }
}

async function onRegenerateHotCache() {
  if (!currentKB.value) return
  hotCacheRegenerating.value = true
  try {
    await regenerateHotCache(currentKB.value.id)
    ElMessage.success(t('knowledgeConfig.regenerateTriggered'))
    setTimeout(() => loadHotCache(), 4000)
  } catch { /* */ } finally {
    hotCacheRegenerating.value = false
  }
}

async function onResetHotCache() {
  if (!currentKB.value) return
  try {
    await ElMessageBox.confirm(
      t('knowledgeConfig.resetHotCacheConfirm'),
      t('common.confirm'), { type: 'warning' }
    )
    await resetHotCache(currentKB.value.id)
    ElMessage.success(t('knowledgeConfig.resetSuccess'))
    hotCache.value = null
  } catch { /* */ }
}

// ==================== Lifecycle ====================
onMounted(() => {
  fetchKBs()
  window.addEventListener('resize', onGraphResize)
  window.addEventListener('resize', handleKbWindowResize)
})

onBeforeUnmount(() => {
  closeSSE()
  window.removeEventListener('resize', onGraphResize)
  window.removeEventListener('resize', handleKbWindowResize)
  if (graphRenderTimer) {
    clearTimeout(graphRenderTimer)
    graphRenderTimer = null
  }
  if (graphChart.value) {
    graphChart.value.dispose()
    graphChart.value = null
  }
})

watch(activeTab, (tab) => {
  if (currentKB.value) {
    saveKBState(String(currentKB.value.id), tab)
  }
  if (tab === 'transformations') loadTransformations()
  if (tab === 'hotCache') loadHotCache()
  if (tab === 'config') loadConfigPanel()
})
</script>

<style scoped>
/* ========== 页面根 ========== */
.knowledge-config-page {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: transparent;
}

/* ========== Library 视图 ========== */
.kb-library {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: auto;
}

/* 身份页头：标题/描述靠左，主操作靠右，单行对齐 */
.kb-content-header {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
  padding: 2px 2px 14px;
}

.kb-content-title {
  min-width: 0;
}

.kb-content-name-row {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.kb-content-name {
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

.kb-content-desc {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--db-text-muted);
  line-height: 18px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 数量标识：紧凑内联，与技能/数据/词典页统计同一语言 */
.kb-title-stats {
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
.summary-num {
  font-size: 12px;
  font-weight: 700;
  color: var(--db-text);
  margin-left: 1px;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  margin-left: auto;
  flex-shrink: 0;
}

/* 主操作：家族胶囊按钮（主题色实心 + 白字 + 阴影） */
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
.btn-create-pill.small {
  height: 30px;
  padding: 0 14px;
  font-size: 12px;
}

.page-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 48px 0;
  color: var(--db-text-muted);
}

/* 全局空态：一整块接管内容区，图标为线型 SVG chip */
.global-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
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
  max-width: 360px;
  line-height: 1.6;
}
.global-empty-desc {
  font-size: 12px;
  color: var(--db-text-muted);
  margin: 0 0 14px;
  max-width: 360px;
  line-height: 1.6;
}

/* 卡片面：系统统一变量（白面 + 细边 + 圆角） */
.surface-card {
  background: var(--db-card);
  border: 1px solid var(--db-border);
  border-radius: var(--radius-lg, 12px);
}

/* 卡片网格 */
.kb-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  grid-auto-rows: 1fr;
  gap: var(--space-lg, 16px);
}

/* 卡片：家族统一尺寸（padding 16/16/12、flex column、hover 提边+阴影+微浮起），等高卡空白由 footer margin-top:auto 吸收 */
.kb-card {
  position: relative;
  background: var(--db-card);
  border: 1px solid var(--db-border);
  border-radius: var(--radius-lg, 12px);
  padding: 16px 16px 12px;
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-card);
  transition: border-color var(--transition-fast, 0.15s), box-shadow var(--transition-fast, 0.15s), transform var(--transition-fast, 0.15s);
  cursor: pointer;
  overflow: hidden;
}

.kb-card:hover {
  border-color: var(--db-border-strong);
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}

/* 卡头：名称 + 状态 tag 同行（图标 chip 已按用户要求移除），名称弹性占据剩余空间 */
.kb-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.kb-card-name {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 状态标签：圆角胶囊 + 圆点（与洞察/报告卡片统一），kb 状态较多故四型均给语义色 */
.kb-card-status.el-tag {
  margin-left: auto;
  border: none;
  border-radius: 20px;
  font-weight: 600;
  flex-shrink: 0;
}

/* el-tag 内容区默认是 inline span，圆点按基线对齐会偏离文字中线——改 flex 垂直居中 */
.kb-card-status :deep(.el-tag__content) {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.kb-card-status .status-dot {
  display: inline-block;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}

.kb-card-status.el-tag--success {
  background: color-mix(in srgb, var(--el-color-success) 12%, transparent);
  color: var(--el-color-success);
}

.kb-card-status.el-tag--warning {
  background: color-mix(in srgb, var(--el-color-warning) 14%, transparent);
  color: var(--el-color-warning);
}

.kb-card-status.el-tag--danger {
  background: var(--db-danger-bg);
  color: var(--db-danger);
}

.kb-card-status.el-tag--info {
  background: var(--db-hover);
  color: var(--db-text-muted);
}

/* 描述：上下间距用 margin（line-clamp 裁切边界是 padding-box，垂直 padding 会漏绘）；整数行高；截断态由测量逻辑驱动 tooltip */
.kb-card-desc {
  font-size: 12px;
  color: var(--db-text-secondary);
  margin: 8px 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 18px;
}

/* 底栏：安静元信息行（左）+ 纯文字操作（右）；margin-top:auto 吸收等高卡剩余空白 */
.kb-card-footer {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: auto;
  padding-top: 8px;
  border-top: 1px solid var(--db-border);
}

/* 统计元信息：材料/页面合并为一行安静小字（对齐技能卡 meta 行），不再用加粗数值强调 */
.kb-card-meta {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: var(--db-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}

.kb-card-meta .meta-sep {
  color: var(--db-border-strong);
}

/* 操作按钮常驻可见，纯文字轻量按钮（透明底、11.5px，hover 浅色底），margin-left:auto 右靠 */
.kb-card-actions {
  margin-left: auto;
  align-self: center;
  display: inline-flex;
  gap: 2px;
  flex-shrink: 0;
}

.kb-card-action {
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
  transition: color var(--transition-fast, 0.15s), background var(--transition-fast, 0.15s);
  white-space: nowrap;
}

.kb-card-action:hover {
  color: var(--db-text);
  background: var(--db-hover);
}

/* 删除按钮默认红色文字+图标（家族约定：技能/智能体/报告/洞察卡 action-delete 默认 #ef4444），hover 淡红底 */
.kb-card-action--danger {
  color: #ef4444;
}

.kb-card-action--danger:hover {
  color: #dc2626;
  background: var(--db-danger-bg);
}

/* ========== Workspace 视图 ========== */
.kb-workspace {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: transparent;
}

.workspace-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 0;
  background: transparent;
  flex-shrink: 0;
}

/* 详情页状态标签复用列表卡 kb-card-status 的圆点+语义色，但取消 margin-left:auto（头部非两端布局） */
.workspace-status.el-tag {
  margin-left: 0;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

/* 圆形返回图标按钮：30px 描边圆、透明底，hover 提边+浅底（家族幽灵按钮语言） */
.ws-back-btn {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 1px solid var(--db-border);
  background: transparent;
  color: var(--db-text-secondary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: border-color var(--transition-fast, 0.15s), background var(--transition-fast, 0.15s), color var(--transition-fast, 0.15s);
}

.ws-back-btn:hover {
  border-color: var(--db-border-strong);
  background: var(--db-hover);
  color: var(--db-text);
}

.header-kb-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--theme-text);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

/* 头部统计：安静元信息行（与列表卡 kb-card-meta 同语言），取代旧的图标+加粗数值 */
.workspace-meta {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--db-text-muted);
  white-space: nowrap;
}

.workspace-meta .meta-sep {
  color: var(--db-border-strong);
}

/* Tab 导航：透明底 + 细分隔线，与内容同处一张纸面 */
.workspace-tabs {
  display: flex;
  gap: 4px;
  background: transparent;
  border-bottom: 1px solid var(--db-border);
  flex-shrink: 0;
  overflow-x: auto;
  scrollbar-width: thin;
}

.tab-btn {
  padding: 8px 16px;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 13px;
  color: var(--theme-text-muted);
  border-radius: 6px 6px 0 0;
  transition: all 0.15s;
  position: relative;
  white-space: nowrap;
  flex-shrink: 0;
}

.tab-btn:hover {
  color: var(--theme-text-secondary);
}

.tab-btn.active {
  color: var(--main-orange);
  font-weight: 600;
}

.tab-btn.active::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 0;
  right: 0;
  height: 2px;
  background: var(--main-orange);
  border-radius: 2px 2px 0 0;
}

/* 内容区 */
.workspace-content {
  flex: 1;
  min-height: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
}

.tab-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 8px;
}

/* ========== Raw Panel ========== */
/* 摄取工具卡：上传条 + 动作行收进一张浅底内嵌卡，与内容白面形成层级 */
.raw-ingest {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  background: var(--db-bg);
  border: 1px solid var(--db-border);
  border-radius: 12px;
}

/* 瘦长上传条：圆形橙色图标 chip 做视觉焦点，拖拽态环描边 */
.upload-zone {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border: 1px dashed var(--db-border-strong);
  border-radius: 10px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s, color 0.15s;
  color: var(--db-text-muted);
  background: transparent;
}

.upload-zone:hover {
  border-color: var(--main-orange);
  background: color-mix(in srgb, var(--main-orange) 5%, transparent);
  color: var(--main-orange);
}

.upload-zone.is-dragging {
  border-color: var(--main-orange);
  background: color-mix(in srgb, var(--main-orange) 6%, transparent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--main-orange) 12%, transparent);
}

.upload-icon-chip {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--main-orange) 10%, transparent);
  color: var(--main-orange);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.upload-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.upload-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--db-text);
}

.upload-hint {
  font-size: 11px;
  color: var(--db-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.raw-ingest-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.add-text-btn {
  flex-shrink: 0;
}

.scan-input {
  flex: 1 1 240px;
  min-width: 0;
}

/* 扫描按钮并入输入框 append 槽：与输入框连体，操作归属一目了然 */
.scan-input :deep(.el-input-group__append) {
  background: var(--db-bg);
  color: var(--db-text-secondary);
  padding: 0 12px;
  cursor: pointer;
  transition: color 0.15s, background 0.15s;
}

.scan-input :deep(.el-input-group__append .scan-append-btn) {
  margin: 0 -12px;
  color: inherit;
}

.scan-input :deep(.el-input-group__append:hover) {
  background: color-mix(in srgb, var(--main-orange) 8%, transparent);
  color: var(--main-orange);
}

.scan-result {
  margin-top: -4px;
}

/* Raw list */
.raw-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 列表页眉：标题 + 计数 pill + 右靠主操作 */
.raw-list-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.raw-list-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--db-text);
  margin: 0;
}

.raw-list-count {
  min-width: 20px;
  padding: 1px 6px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--db-text-muted) 12%, transparent);
  color: var(--db-text-muted);
  font-size: 11px;
  font-weight: 600;
  text-align: center;
}

.process-all-btn {
  margin-left: auto;
}

/* 内联空态：家族线型 SVG chip + 安静文案（对齐数据配置/技能页 global-empty 语言） */
.inline-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  text-align: center;
  color: var(--db-text-muted);
}

.inline-empty-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--db-text-muted) 10%, transparent);
  color: var(--db-text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
}

.inline-empty p {
  font-size: 13px;
  margin: 0;
}

.raw-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 16px;
  background: var(--db-card);
  border: 1px solid var(--theme-border);
  border-radius: 10px;
  font-size: 13px;
  transition: border-color 0.15s;
  cursor: pointer;
}

.raw-item:hover {
  border-color: var(--db-border-strong);
}

.raw-item--active {
  border-color: var(--main-orange);
  background: color-mix(in srgb, var(--main-orange) 8%, transparent);
}

.raw-item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.raw-item-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.raw-item-title {
  font-weight: 500;
  color: var(--theme-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.raw-item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.page-count-chip {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: var(--theme-text-muted);
  background: var(--theme-bg);
  border-radius: 999px;
  padding: 2px 7px;
}

.error-hint {
  font-size: 11px;
  color: var(--db-danger);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.raw-item-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.15s;
}

.raw-item:hover .raw-item-actions {
  opacity: 1;
}

.raw-progress {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-top: 4px;
}

.progress-label {
  font-size: 11px;
  color: var(--theme-text-muted);
  white-space: nowrap;
  min-width: 60px;
  text-align: right;
}

/* ========== Pages Panel ========== */
.pages-panel {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.page-viewer {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-viewer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--theme-border);
}

.page-viewer-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--theme-text);
  margin: 0;
}

.page-viewer-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}

.page-viewer-slug {
  color: var(--theme-text-muted);
  font-size: 12px;
  font-family: monospace;
}

.page-viewer-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

/* 编辑入口与编辑态操作：跟随内容区，位于页面底部 */
.page-edit-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}

.page-edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}

.page-summary {
  margin-bottom: 8px;
}

.page-content {
  font-size: 15px;
  line-height: 1.8;
  color: var(--theme-text);
}

.page-content :deep(h1) { font-size: 24px; font-weight: 700; margin: 20px 0 12px; }
.page-content :deep(h2) { font-size: 20px; font-weight: 600; margin: 16px 0 10px; }
.page-content :deep(h3) { font-size: 17px; font-weight: 600; margin: 14px 0 8px; }
.page-content :deep(li) { margin-left: 20px; }
.page-content :deep(.wiki-link) { color: var(--main-orange); cursor: pointer; text-decoration: none; border-bottom: 1px dashed var(--main-orange); }
.page-content :deep(.wiki-link:hover) { text-decoration: underline; }

/* ========== Graph / Config / Transformations / HotCache ========== */
.graph-panel,
.config-panel,
.transformations-panel,
.hotcache-panel {
  background: var(--db-card);
  border-radius: 10px;
  /* padding: 24px; */
  min-height: 300px;
}

.config-section h4 {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
  color: var(--theme-text);
}

.config-actions {
  margin-top: 12px;
  text-align: right;
}

.placeholder,
.graph-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 300px;
  color: var(--theme-text-muted);
}

/* ========== Responsive ========== */
@media (max-width: 768px) {
  .kb-card-grid {
    grid-template-columns: 1fr;
  }

  .workspace-body {
    flex-direction: column;
  }

  .wiki-sidebar {
    width: 100%;
    min-width: 0;
    max-height: 260px;
    margin-right: 0;
  }

  /* 窄视口下动作行纵向堆叠，添加文本撑满便于点按 */
  .raw-ingest-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .add-text-btn {
    width: 100%;
  }
}

/* ========== Workspace Body & Sidebar ========== */
.workspace-body {
  flex: 1;
  display: flex;
  min-height: 0;
  overflow: hidden;
}

.workspace-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.wiki-sidebar {
  width: 260px;
  min-width: 260px;
  background: var(--db-card);
  border: 1px solid var(--db-border);
  border-radius: var(--radius-lg, 12px);
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  padding: 16px;
  gap: 12px;
  overflow: hidden;
  margin-right: 14px;
  flex-shrink: 0;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sidebar-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--theme-text);
}

.sidebar-count {
  font-size: 12px;
  color: var(--theme-text-muted);
  background: var(--db-hover);
  padding: 2px 8px;
  border-radius: 10px;
}

.sidebar-search {
  flex-shrink: 0;
}

.sidebar-pages {
  flex: 1;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

/* 分组标题行：撑满 el-collapse header，名称+计数左 / 箭头图标最右 */
.sidebar-group-row {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sidebar-group-left {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

/* 箭头图标默认指向右（收起），展开时转正（is-active 由 el-collapse 维护） */
.sidebar-group-arrow {
  font-size: 12px;
  transition: transform 0.2s ease;
  transform: rotate(-90deg);
}

.sidebar-collapse :deep(.el-collapse-item.is-active) .sidebar-group-arrow {
  transform: rotate(0deg);
}

/* el-collapse 去 EP 默认边框/底色/自带箭头，保留家族轻量分组外观（小字大写标题 + 计数 chip） */
.sidebar-collapse {
  border: none;
}

.sidebar-collapse :deep(.el-collapse-item__header) {
  height: 28px;
  line-height: 28px;
  background: transparent;
  border-bottom: none;
  font-size: 13px;
  font-weight: 600;
  color: var(--db-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 0 2px;
  margin-top: 4px;
}

.sidebar-collapse :deep(.el-collapse-item__header:hover),
.sidebar-collapse :deep(.el-collapse-item__header:focus) {
  color: var(--main-orange);
}

.sidebar-collapse :deep(.el-collapse-item__arrow) {
  display: none;
}

.sidebar-collapse :deep(.el-collapse-item__wrap) {
  background: transparent;
  border-bottom: none;
}

.sidebar-collapse :deep(.el-collapse-item__content) {
  padding-bottom: 6px;
  color: inherit;
  font-size: inherit;
  line-height: inherit;
}

.sidebar-group-count {
  flex-shrink: 0;
  min-width: 18px;
  height: 18px;
  line-height: 18px;
  text-align: center;
  border-radius: 9px;
  background: var(--db-hover);
  color: var(--theme-text-muted);
  font-size: 10px;
}

.sidebar-group-pages {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sidebar-page-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
  font-size: 13px;
  color: var(--theme-text-secondary);
}

.sidebar-page-item:hover {
  background: var(--theme-bg);
}

.sidebar-page-item.active {
  background: color-mix(in srgb, var(--main-orange) 10%, transparent);
  color: var(--main-orange);
  font-weight: 500;
}

.sidebar-page-title {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}

.sidebar-page-meta {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.page-flag {
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--theme-border);
  color: var(--theme-text-muted);
}

.page-flag--locked {
  background: var(--db-danger-bg);
  color: var(--db-danger);
}

.header-kb-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.header-kb-desc {
  font-size: 12px;
  color: var(--theme-text-muted);
  max-width: 300px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ========== Graph Panel ========== */
.graph-panel {
  position: relative;
  padding: 0;
}

.graph-panel.fullscreen {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 2000;
  background: var(--db-card);
}

.graph-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--db-card);
  border-bottom: 1px solid var(--theme-border);
  flex-shrink: 0;
}

.graph-toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.graph-toolbar-right {
  display: flex;
  gap: 8px;
}

.graph-stats {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--theme-text-muted);
}

.graph-stat-item {
  font-weight: 500;
}

.graph-stat-sep {
  color: var(--theme-text-muted);
  font-weight: 300;
}

.graph-stat-orphan {
  color: var(--db-danger);
}

.graph-canvas {
  flex: 1;
  min-height: 0;
}

.graph-node-panel {
  position: absolute;
  right: 16px;
  top: 60px;
  width: 260px;
  background: var(--db-card);
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.1);
  padding: 16px;
  z-index: 10;
}

.node-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.node-panel-header h4 {
  margin: 0;
  font-size: 15px;
}

.node-panel-type {
  font-size: 12px;
  color: var(--theme-text-muted);
  margin: 0 0 4px;
}

.node-panel-slug {
  font-size: 12px;
  color: var(--theme-text-muted);
  margin: 0 0 12px;
  font-family: monospace;
}

/* ========== Config Panel ========== */
.config-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
  gap: 16px;
}

.config-card {
  background: var(--db-card);
  border-radius: 12px;
  padding: 16px;
  border: 1px solid var(--theme-border);
}

.config-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.config-card-head h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.config-desc {
  font-size: 13px;
  color: var(--theme-text-muted);
  margin: 0 0 12px;
}

.config-hint {
  font-size: 12px;
  color: var(--theme-text-muted);
  margin-top: 8px;
}

.search-preview-row {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.search-preview-results {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.search-result-item {
  padding: 10px 12px;
  background: var(--theme-bg);
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}

.search-result-item:hover {
  background: var(--theme-border);
}

.search-result-title {
  font-weight: 500;
  font-size: 13px;
}

.search-result-score {
  float: right;
  font-size: 12px;
  color: var(--main-orange);
  font-weight: 600;
}

.search-result-snippet {
  font-size: 12px;
  color: var(--theme-text-muted);
  margin: 4px 0 0;
  line-height: 1.4;
}

/* ========== Transformations Panel ========== */
.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}

.panel-header h4 {
  margin: 0 0 4px;
  font-size: 15px;
}

.panel-desc {
  font-size: 12px;
  color: var(--theme-text-muted);
  margin: 0;
}

.panel-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
  gap: 12px;
  color: var(--theme-text-muted);
}

.panel-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px;
  gap: 12px;
  color: var(--theme-text-muted);
}

.panel-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.transformation-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.transformation-card {
  background: var(--db-card);
  border-radius: 12px;
  padding: 16px;
  border: 1px solid var(--theme-border);
}

.tpl-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.tpl-title {
  font-weight: 600;
  font-size: 14px;
}

.tpl-flags {
  display: flex;
  gap: 4px;
}

.tpl-desc {
  font-size: 13px;
  color: var(--theme-text-muted);
  margin: 0 0 12px;
}

.tpl-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}

.runs-details {
  border-top: 1px dashed var(--theme-border);
  padding-top: 8px;
}

.runs-details summary {
  font-size: 12px;
  color: var(--theme-text-muted);
  cursor: pointer;
  user-select: none;
}

.runs-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}

.run-item {
  padding: 10px 12px;
  background: var(--theme-bg);
  border-radius: 8px;
  font-size: 12px;
}

.run-header {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
}

.run-time {
  color: var(--theme-text-muted);
}

.run-duration {
  color: var(--theme-text-secondary);
  font-weight: 500;
}

.run-tokens {
  color: var(--main-orange);
}

.run-output pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  color: var(--theme-text-secondary);
  max-height: 200px;
  overflow: auto;
  background: var(--db-card);
  padding: 8px;
  border-radius: 6px;
}

.run-error {
  color: var(--db-danger);
  margin-top: 4px;
}

.run-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

/* ========== Hot Cache Panel ========== */
.hot-cache-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.meta-item {
  background: var(--db-card);
  border-radius: 10px;
  padding: 12px;
  border: 1px solid var(--theme-border);
}

.meta-label {
  display: block;
  font-size: 11px;
  color: var(--theme-text-muted);
  text-transform: uppercase;
  margin-bottom: 4px;
}

.meta-value {
  font-size: 14px;
  font-weight: 500;
  color: var(--theme-text);
}

.error-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: var(--db-danger-bg);
  color: var(--db-danger);
  border-radius: 8px;
  font-size: 13px;
}

.cache-content {
  background: var(--db-card);
  border: 1px solid var(--theme-border);
  border-radius: 10px;
  padding: 16px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 600px;
  overflow: auto;
  margin: 0;
}
</style>
