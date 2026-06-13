<template>
  <div class="knowledge-config-page">
    <!-- ========== 知识库列表视图 (Library) ========== -->
    <div v-if="!currentKB" class="kb-library">
      <!-- 工具栏 -->
      <div class="library-toolbar">
        <div class="library-title">
          <span class="title-text">{{ t('knowledgeConfig.wikiLibrary') }}</span>
          <span class="kb-count">{{ knowledgeBases.length }}</span>
        </div>
        <el-button type="primary" size="small" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          {{ t('knowledgeConfig.create') }}
        </el-button>
      </div>

      <!-- 加载中 -->
      <div v-if="loading && knowledgeBases.length === 0" class="page-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>{{ t('common.loading') }}</span>
      </div>

      <!-- 空状态 -->
      <div v-else-if="knowledgeBases.length === 0" class="library-empty">
        <el-icon :size="48" class="empty-icon"><Collection /></el-icon>
        <p class="empty-title">{{ t('knowledgeConfig.emptyTitle') }}</p>
        <p class="empty-desc">{{ t('knowledgeConfig.emptyDesc') }}</p>
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          {{ t('knowledgeConfig.create') }}
        </el-button>
      </div>

      <!-- 知识库卡片网格 -->
      <div v-else class="kb-card-grid">
        <div
          v-for="kb in knowledgeBases"
          :key="kb.id"
          class="kb-card"
          :class="{ 'kb-card--processing': kb.status === 'processing', 'kb-card--error': kb.status === 'error' }"
          @click="selectKB(kb)"
        >
          <div class="kb-card-header">
            <div class="kb-card-icon">
              <el-icon :size="20"><Document /></el-icon>
            </div>
            <el-tag :type="statusTagType(kb.status)" size="small" effect="plain">
              {{ statusLabel(kb.status) }}
            </el-tag>
          </div>
          <div class="kb-card-body">
            <h3 class="kb-card-name">{{ kb.name }}</h3>
            <p class="kb-card-desc">{{ kb.description || t('knowledgeConfig.noDescription') }}</p>
          </div>
          <div class="kb-card-footer">
            <div class="kb-stat">
              <span class="kb-stat-label">{{ t('knowledgeConfig.rawCount') }}</span>
              <span class="kb-stat-value">{{ kb.rawCount }}</span>
            </div>
            <div class="kb-stat">
              <span class="kb-stat-label">{{ t('knowledgeConfig.pageCount') }}</span>
              <span class="kb-stat-value">{{ kb.pageCount }}</span>
            </div>
          </div>
          <!-- 悬浮操作 -->
          <div class="kb-card-actions" @click.stop>
            <el-button size="small" circle @click="handleDelete(kb)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- ========== 知识库工作区视图 (Workspace) ========== -->
    <div v-else class="kb-workspace">
      <!-- 工作区头部 -->
      <div class="workspace-header">
        <div class="header-left">
          <el-button size="small" text @click="backToLibrary">
            <el-icon><ArrowLeft /></el-icon>
            {{ t('knowledgeConfig.backToLibrary') }}
          </el-button>
          <div class="header-divider" />
          <div class="header-kb-icon">{{ (currentKB.name || '?').charAt(0).toUpperCase() }}</div>
          <div class="header-kb-info">
            <span class="header-kb-name">{{ currentKB.name }}</span>
            <span v-if="currentKB.description" class="header-kb-desc">{{ currentKB.description }}</span>
          </div>
          <el-tag :type="statusTagType(currentKB.status)" size="small" effect="plain">
            {{ statusLabel(currentKB.status) }}
          </el-tag>
        </div>
        <div class="header-right">
          <div class="header-stats">
            <span class="header-stat">
              <el-icon><Document /></el-icon>
              <span class="header-stat-value">{{ currentKB.pageCount }}</span>
              <span class="header-stat-label">{{ t('knowledgeConfig.pageCount') }}</span>
            </span>
            <span class="header-stat">
              <el-icon><Files /></el-icon>
              <span class="header-stat-value">{{ currentKB.rawCount }}</span>
              <span class="header-stat-label">{{ t('knowledgeConfig.rawCount') }}</span>
            </span>
          </div>
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
            size="small"
            clearable
            class="sidebar-search"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <div class="sidebar-pages">
            <div v-for="[type, group] in groupedSidebarPages" :key="type" class="sidebar-group">
              <button class="sidebar-group-title" type="button" @click="toggleSidebarGroup(type)">
                <span class="sidebar-group-left">
                  <el-icon class="sidebar-group-arrow" :class="{ collapsed: collapsedSidebarGroups.has(type) }">
                    <ArrowDown />
                  </el-icon>
                  <span>{{ type }}</span>
                </span>
                <span class="sidebar-group-count">{{ group.length }}</span>
              </button>
              <div v-show="!collapsedSidebarGroups.has(type)" class="sidebar-group-pages">
                <div
                  v-for="page in group"
                  :key="page.id"
                  class="sidebar-page-item"
                  :class="{ active: currentPage?.slug === page.slug }"
                  @click="openPage(page); activeTab = 'pages'"
                >
                  <span class="sidebar-page-title">{{ page.title }}</span>
                  <span v-if="page.locked" class="page-flag page-flag--locked">L</span>
                </div>
              </div>
            </div>
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
          <!-- 上传区 + 添加文本 -->
          <div class="raw-toolbar">
            <div
              class="upload-zone"
              :class="{ 'is-dragging': isDragging }"
              @click="triggerFileInput"
              @dragover.prevent
              @dragenter.prevent="isDragging = true"
              @dragleave.prevent="isDragging = false"
              @drop.prevent="handleDrop"
            >
              <el-icon :size="24"><Upload /></el-icon>
              <div class="upload-text">
                <span class="upload-label">{{ t('knowledgeConfig.dropFiles') }}</span>
                <span class="upload-hint">.txt .md .csv .pdf .docx .xlsx .pptx .html</span>
              </div>
            </div>
            <input ref="fileInput" type="file" style="display:none" accept=".txt,.md,.csv,.pdf,.docx,.doc,.xlsx,.xls,.pptx,.ppt,.html,.htm" multiple @change="handleFileSelect" />
            <el-button class="add-text-btn" @click="showAddTextDialog = true">
              <el-icon><Plus /></el-icon>
              {{ t('knowledgeConfig.addText') }}
            </el-button>
          </div>

          <!-- 目录扫描 -->
          <div class="scan-row">
            <el-input
              v-model="scanPath"
              :placeholder="t('knowledgeConfig.dirPlaceholder')"
              size="small"
              clearable
              @keyup.enter="handleScanDir"
            >
              <template #prefix>
                <el-icon><Folder /></el-icon>
              </template>
            </el-input>
            <el-button size="small" :loading="scanning" @click="handleScanDir">
              <el-icon><Search /></el-icon>
              {{ t('knowledgeConfig.scan') }}
            </el-button>
          </div>

          <!-- 扫描结果 -->
          <div v-if="scanResult" class="scan-result">
            <el-alert type="success" :closable="true" @close="scanResult = null">
              {{ t('knowledgeConfig.scanResult', scanResult) }}
            </el-alert>
          </div>

          <!-- 原始材料列表 -->
          <div class="raw-list">
            <h4 class="raw-list-title">
              {{ t('knowledgeConfig.rawMaterials') }} ({{ rawMaterials.length }})</h4>
            <div v-if="rawMaterials.length === 0" class="raw-empty">
              {{ t('knowledgeConfig.noRawMaterials') }}
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
                  <el-button v-if="raw.processingStatus === 'processing'" size="small" text type="danger" @click="handleCancelRaw(raw)">
                    <el-icon><Close /></el-icon>
                  </el-button>
                  <el-button v-else-if="raw.processingStatus === 'failed' || raw.processingStatus === 'partial' || raw.processingStatus === 'completed' || raw.processingStatus === 'cancelled'" size="small" text @click="handleReprocess(raw)">
                    <el-icon><RefreshRight /></el-icon>
                  </el-button>
                  <el-button size="small" text @click="handleDownloadRaw(raw)">
                    <el-icon><Download /></el-icon>
                  </el-button>
                  <el-button size="small" text type="danger" @click="handleDeleteRaw(raw)">
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

          <!-- 全部处理按钮 -->
          <div v-if="rawMaterials.some(r => r.processingStatus === 'pending')" class="process-all-row">
            <el-button type="primary" @click="handleProcessAll">
              <el-icon><VideoPlay /></el-icon>
              {{ t('knowledgeConfig.processAll') }}
            </el-button>
          </div>
        </div>

        <!-- ===== Wiki 页面 Tab ===== -->
        <div v-if="activeTab === 'pages'" class="tab-panel pages-panel">
          <div v-if="!currentPage" class="page-empty">
            <el-icon :size="48"><Document /></el-icon>
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
                <el-button size="small" @click="editingPage = !editingPage">
                  {{ editingPage ? t('common.cancel') : t('common.edit') }}
                </el-button>
                <el-button v-if="editingPage" type="primary" size="small" @click="savePageEdit">
                  {{ t('common.save') }}
                </el-button>
                <el-button size="small" type="danger" text @click="handleDeletePage(currentPage)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>
            <div v-if="currentPage.summary && !editingPage" class="page-summary">
              <el-alert type="info" :title="currentPage.summary" :closable="false" />
            </div>
            <div v-if="!editingPage" class="page-content markdown-body" v-html="renderedPageContent" />
            <el-input v-else v-model="editContent" type="textarea" :rows="20" />
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
                <el-button size="small" type="primary" :loading="configSavingEmbedding" @click="saveEmbeddingModel">
                  {{ t('common.save') }}
                </el-button>
              </div>
              <p class="config-desc">{{ t('knowledgeConfig.embeddingDesc') }}</p>
              <el-input v-model="configEmbeddingModelId" :placeholder="t('knowledgeConfig.modelIdPlaceholder')" />
            </div>

            <!-- Ingest Mode -->
            <div class="config-card">
              <div class="config-card-head">
                <h4>{{ t('knowledgeConfig.ingestMode') }}</h4>
                <el-button size="small" type="primary" :loading="configSavingIngest" @click="saveIngestMode">
                  {{ t('common.save') }}
                </el-button>
              </div>
              <el-radio-group v-model="configIngestMode">
                <el-radio-button label="eager">{{ t('knowledgeConfig.eager') }}</el-radio-button>
                <el-radio-button label="lazy">{{ t('knowledgeConfig.lazy') }}</el-radio-button>
              </el-radio-group>
              <p class="config-hint">{{ configIngestMode === 'eager' ? t('knowledgeConfig.eagerHint') : t('knowledgeConfig.lazyHint') }}</p>
            </div>

            <!-- Processing Rules -->
            <div class="config-card">
              <div class="config-card-head">
                <h4>{{ t('knowledgeConfig.processingRules') }}</h4>
                <el-button size="small" type="primary" @click="handleSaveConfig">
                  {{ t('common.save') }}
                </el-button>
              </div>
              <el-input
                v-model="kbConfigContent"
                type="textarea"
                :rows="12"
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
            <el-button type="primary" size="small" @click="openTransformEditor()">
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
                <el-select v-model="selectedRawForTransform[tpl.id]" size="small" clearable :placeholder="t('knowledgeConfig.selectRaw')" style="width:200px">
                  <el-option v-for="raw in rawMaterials.filter(r => r.processingStatus === 'completed' || r.processingStatus === 'partial')" :key="raw.id" :label="raw.title" :value="raw.id" />
                </el-select>
                <el-button size="small" type="primary" @click="onApplyTransform(tpl)">{{ t('knowledgeConfig.run') }}</el-button>
                <el-button size="small" @click="onAggregateTransform(tpl)">{{ t('knowledgeConfig.aggregate') }}</el-button>
                <el-button size="small" text @click="openTransformEditor(tpl)">
                  <el-icon><EditPen /></el-icon>
                </el-button>
                <el-button size="small" text type="danger" @click="onDeleteTransform(tpl)">
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
                      <el-button v-if="run.status === 'running' || run.status === 'pending'" size="small" text @click="onCancelTransformRun(run.id)">{{ t('knowledgeConfig.cancel') }}</el-button>
                      <el-button v-if="run.status === 'completed' && run.output" size="small" text type="primary" @click="onSaveRunAsPage(run.id)">{{ t('knowledgeConfig.saveAsPage') }}</el-button>
                      <el-button size="small" text type="danger" @click="onDeleteTransformRun(run.id)">{{ t('knowledgeConfig.delete') }}</el-button>
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
            <el-button type="primary" :loading="hotCacheRegenerating" @click="onRegenerateHotCache">
              {{ t('knowledgeConfig.regenerate') }}
            </el-button>
          </div>
          <div v-else class="hot-cache-body">
            <div class="panel-actions">
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
            <el-radio-button label="none">{{ t('knowledgeConfig.outputNone') }}</el-radio-button>
            <el-radio-button label="page">{{ t('knowledgeConfig.outputPage') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('knowledgeConfig.transFormat')">
          <el-radio-group v-model="transformForm.outputFormat">
            <el-radio-button label="markdown">Markdown</el-radio-button>
            <el-radio-button label="json">JSON</el-radio-button>
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
import * as echarts from 'echarts'
import {
  Plus, Delete, Loading, Collection, Document, Files, ArrowLeft,
  Upload, Folder, Search, Close, RefreshRight, Download, VideoPlay,
  Share, SetUp, Clock, Grid, EditPen, Monitor, FullScreen, Refresh,
  ArrowDown,
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
const collapsedSidebarGroups = ref<Set<string>>(new Set())

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

function toggleSidebarGroup(type: string) {
  const next = new Set(collapsedSidebarGroups.value)
  if (next.has(type)) {
    next.delete(type)
  } else {
    next.add(type)
  }
  collapsedSidebarGroups.value = next
}

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
    concept: '#f05a23', technology: '#409eff', process: '#67c23a',
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
})

onBeforeUnmount(() => {
  closeSSE()
  window.removeEventListener('resize', onGraphResize)
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
}

/* ========== Library 视图 ========== */
.kb-library {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 16px 24px 24px;
  overflow: auto;
}

.library-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.library-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title-text {
  font-size: 16px;
  font-weight: 600;
  color: #1d2129;
}

.kb-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  background: #f0f0f0;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
  color: #86909c;
}

.page-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 48px 0;
  color: #86909c;
}

.library-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 0;
  color: #86909c;
  gap: 12px;
}

.empty-icon {
  color: #c9cdd4;
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: #4e5969;
  margin: 0;
}

.empty-desc {
  font-size: 14px;
  color: #86909c;
  margin: 0;
}

/* 卡片网格 */
.kb-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.kb-card {
  position: relative;
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  transition: box-shadow 0.2s, transform 0.15s;
  cursor: pointer;
  border-left: 3px solid transparent;
}

.kb-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.kb-card--processing {
  border-left-color: #e6a23c;
}

.kb-card--error {
  border-left-color: #f56c6c;
}

.kb-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.kb-card-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff5f0;
  border-radius: 8px;
  color: #f05a23;
}

.kb-card-name {
  font-size: 15px;
  font-weight: 600;
  color: #1d2129;
  margin: 0 0 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kb-card-desc {
  font-size: 13px;
  color: #86909c;
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 36px;
}

.kb-card-footer {
  display: flex;
  gap: 16px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.kb-stat {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
}

.kb-stat-label {
  color: #86909c;
}

.kb-stat-value {
  font-weight: 600;
  color: #4e5969;
}

.kb-card-actions {
  position: absolute;
  top: 12px;
  right: 12px;
  opacity: 0;
  transition: opacity 0.15s;
}

.kb-card:hover .kb-card-actions {
  opacity: 1;
}

/* ========== Workspace 视图 ========== */
.kb-workspace {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: #f5f6f8;
}

.workspace-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  background: #fff;
  border-bottom: 1px solid #e8ecf2;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-divider {
  width: 1px;
  height: 20px;
  background: #e8ecf2;
}

.header-kb-name {
  font-size: 16px;
  font-weight: 600;
  color: #1d2129;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-stats {
  display: flex;
  gap: 16px;
}

.header-stat {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #86909c;
}

/* Tab 导航 */
.workspace-tabs {
  display: flex;
  gap: 4px;
  padding: 8px 24px 0;
  background: #fff;
  border-bottom: 1px solid #e8ecf2;
  flex-shrink: 0;
}

.tab-btn {
  padding: 8px 16px;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 13px;
  color: #86909c;
  border-radius: 6px 6px 0 0;
  transition: all 0.15s;
  position: relative;
}

.tab-btn:hover {
  color: #4e5969;
}

.tab-btn.active {
  color: #f05a23;
  font-weight: 600;
}

.tab-btn.active::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 0;
  right: 0;
  height: 2px;
  background: #f05a23;
  border-radius: 2px 2px 0 0;
}

/* 内容区 */
.workspace-content {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 16px 24px;
  display: flex;
  flex-direction: column;
}

.tab-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ========== Raw Panel ========== */
.raw-toolbar {
  display: flex;
  gap: 12px;
  align-items: stretch;
}

.upload-zone {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border: 1px dashed #d9d9d9;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.15s;
  color: #86909c;
  background: #fff;
}

.upload-zone:hover {
  border-color: #f05a23;
  background: #fff5f0;
  color: #f05a23;
}

.upload-zone.is-dragging {
  border-color: #f05a23;
  background: #fff5f0;
  box-shadow: 0 0 0 3px rgba(240, 90, 35, 0.1);
}

.upload-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.upload-label {
  font-size: 14px;
  color: #4e5969;
}

.upload-hint {
  font-size: 12px;
  color: #86909c;
}

.add-text-btn {
  flex-shrink: 0;
  height: auto;
}

.scan-row {
  display: flex;
  gap: 10px;
  align-items: center;
}

.scan-result {
  margin-top: 4px;
}

/* Raw list */
.raw-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.raw-list-title {
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #86909c;
  margin: 0 0 4px;
}

.raw-empty {
  text-align: center;
  padding: 24px 0;
  font-size: 14px;
  color: #86909c;
}

.raw-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #e8ecf2;
  border-radius: 10px;
  font-size: 13px;
  transition: border-color 0.15s;
  cursor: pointer;
}

.raw-item:hover {
  border-color: #d9d9d9;
}

.raw-item--active {
  border-color: #f05a23;
  background: #fff5f0;
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
  color: #1d2129;
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
  color: #86909c;
  background: #f5f6f8;
  border-radius: 999px;
  padding: 2px 7px;
}

.error-hint {
  font-size: 11px;
  color: #f56c6c;
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
  color: #86909c;
  white-space: nowrap;
  min-width: 60px;
  text-align: right;
}

.process-all-row {
  display: flex;
  justify-content: center;
  padding: 16px 0;
}

/* ========== Pages Panel ========== */
.pages-panel {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.page-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 300px;
  color: #86909c;
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
  border-bottom: 1px solid #e8ecf2;
}

.page-viewer-title {
  font-size: 20px;
  font-weight: 600;
  color: #1d2129;
  margin: 0;
}

.page-viewer-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}

.page-viewer-slug {
  color: #86909c;
  font-size: 12px;
  font-family: monospace;
}

.page-viewer-actions {
  display: flex;
  gap: 8px;
}

.page-summary {
  margin-bottom: 8px;
}

.page-content {
  font-size: 15px;
  line-height: 1.8;
  color: #1d2129;
}

.page-content :deep(h1) { font-size: 24px; font-weight: 700; margin: 20px 0 12px; }
.page-content :deep(h2) { font-size: 20px; font-weight: 600; margin: 16px 0 10px; }
.page-content :deep(h3) { font-size: 17px; font-weight: 600; margin: 14px 0 8px; }
.page-content :deep(li) { margin-left: 20px; }
.page-content :deep(.wiki-link) { color: #f05a23; cursor: pointer; text-decoration: none; border-bottom: 1px dashed #f05a23; }
.page-content :deep(.wiki-link:hover) { text-decoration: underline; }

/* ========== Graph / Config / Transformations / HotCache ========== */
.graph-panel,
.config-panel,
.transformations-panel,
.hotcache-panel {
  background: #fff;
  border-radius: 10px;
  padding: 24px;
  min-height: 300px;
}

.config-section h4 {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
  color: #1d2129;
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
  color: #86909c;
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
    border-right: none;
    border-bottom: 1px solid #e8ecf2;
  }

  .raw-toolbar {
    flex-direction: column;
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
  background: #fff;
  border-right: 1px solid #e8ecf2;
  display: flex;
  flex-direction: column;
  padding: 16px;
  gap: 12px;
  overflow: hidden;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sidebar-title {
  font-size: 14px;
  font-weight: 600;
  color: #1d2129;
}

.sidebar-count {
  font-size: 12px;
  color: #86909c;
  background: #f0f0f0;
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

.sidebar-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sidebar-group-title {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: none;
  background: transparent;
  font-size: 11px;
  font-weight: 600;
  color: #86909c;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 8px 10px 4px;
  margin-top: 4px;
  cursor: pointer;
}

.sidebar-group-title:hover {
  color: #f05a23;
}

.sidebar-group-left {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.sidebar-group-arrow {
  font-size: 12px;
  transition: transform 0.15s ease;
}

.sidebar-group-arrow.collapsed {
  transform: rotate(-90deg);
}

.sidebar-group-count {
  flex-shrink: 0;
  min-width: 18px;
  height: 18px;
  line-height: 18px;
  text-align: center;
  border-radius: 9px;
  background: #f2f3f5;
  color: #86909c;
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
  color: #4e5969;
}

.sidebar-page-item:hover {
  background: #f5f6f8;
}

.sidebar-page-item.active {
  background: #fff5f0;
  color: #f05a23;
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
  background: #e8ecf2;
  color: #86909c;
}

.page-flag--locked {
  background: #fde2e2;
  color: #f56c6c;
}

/* Header improvements */
.header-kb-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, #f05a23 0%, #ff8a5c 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 700;
}

.header-kb-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.header-kb-desc {
  font-size: 12px;
  color: #86909c;
  max-width: 300px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.header-stat-value {
  font-weight: 600;
  color: #1d2129;
}

.header-stat-label {
  color: #86909c;
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
  background: #fff;
}

.graph-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #fff;
  border-bottom: 1px solid #e8ecf2;
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
  color: #86909c;
}

.graph-stat-item {
  font-weight: 500;
}

.graph-stat-sep {
  color: #c9cdd4;
  font-weight: 300;
}

.graph-stat-orphan {
  color: #f56c6c;
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
  background: #fff;
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
  color: #86909c;
  margin: 0 0 4px;
}

.node-panel-slug {
  font-size: 12px;
  color: #c9cdd4;
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
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  border: 1px solid #e8ecf2;
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
  color: #86909c;
  margin: 0 0 12px;
}

.config-hint {
  font-size: 12px;
  color: #86909c;
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
  background: #f5f6f8;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}

.search-result-item:hover {
  background: #e8ecf2;
}

.search-result-title {
  font-weight: 500;
  font-size: 13px;
}

.search-result-score {
  float: right;
  font-size: 12px;
  color: #f05a23;
  font-weight: 600;
}

.search-result-snippet {
  font-size: 12px;
  color: #86909c;
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
  color: #86909c;
  margin: 0;
}

.panel-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
  gap: 12px;
  color: #86909c;
}

.panel-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px;
  gap: 12px;
  color: #86909c;
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
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  border: 1px solid #e8ecf2;
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
  color: #86909c;
  margin: 0 0 12px;
}

.tpl-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}

.runs-details {
  border-top: 1px dashed #e8ecf2;
  padding-top: 8px;
}

.runs-details summary {
  font-size: 12px;
  color: #86909c;
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
  background: #f5f6f8;
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
  color: #86909c;
}

.run-duration {
  color: #4e5969;
  font-weight: 500;
}

.run-tokens {
  color: #f05a23;
}

.run-output pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  color: #4e5969;
  max-height: 200px;
  overflow: auto;
  background: #fff;
  padding: 8px;
  border-radius: 6px;
}

.run-error {
  color: #f56c6c;
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
  background: #fff;
  border-radius: 10px;
  padding: 12px;
  border: 1px solid #e8ecf2;
}

.meta-label {
  display: block;
  font-size: 11px;
  color: #86909c;
  text-transform: uppercase;
  margin-bottom: 4px;
}

.meta-value {
  font-size: 14px;
  font-weight: 500;
  color: #1d2129;
}

.error-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: #fde2e2;
  color: #f56c6c;
  border-radius: 8px;
  font-size: 13px;
}

.cache-content {
  background: #fff;
  border: 1px solid #e8ecf2;
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
