<template>
  <div class="property-panel">
    <div class="panel-header">
      <span>{{ t('insight.propertyTitle') }}</span>
      <button type="button" class="property-collapse-btn" title="收起面板" aria-label="收起面板" @click="emit('collapse')">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="13 17 18 12 13 7"/><polyline points="6 17 11 12 6 7"/></svg>
      </button>
    </div>

    <div v-if="!component" class="panel-empty">
      <div class="empty-icon" aria-hidden="true">—</div>
      <div class="empty-text">{{ t('insight.propertyEmpty') }}</div>
    </div>

    <div v-else class="panel-body">
      <!-- 标题 -->
      <div class="form-group">
        <label class="form-label">{{ t('insight.property.componentTitle') }}</label>
        <el-input v-model="localComponent.title" :aria-label="t('insight.property.componentTitle')" @change="emitChange" />
      </div>

      <div class="form-group form-group-row">
        <label class="form-label">显示标题栏</label>
        <el-switch v-model="localComponent.showTitle" aria-label="显示标题栏" @change="emitChange" />
      </div>
      <div class="form-group">
        <label class="form-label">标题栏样式</label>
        <el-select v-model="localComponent.titleBarStyle" aria-label="标题栏样式" style="width: 100%" @change="emitChange">
          <el-option value="standard" label="标准卡片" />
          <el-option value="minimal" label="简洁文本" />
          <el-option value="accent" label="强调色" />
          <el-option value="section" label="分组标题" />
        </el-select>
      </div>
      <div class="style-section-title">组件展示样式</div>
      <div class="form-group">
        <label class="form-label">边框</label>
        <el-select v-model="localComponent.visualStyle!.border!.mode" aria-label="组件边框" style="width: 100%" @change="emitChange">
          <el-option value="theme" label="跟随主题" />
          <el-option value="visible" label="显示" />
          <el-option value="hidden" label="隐藏" />
        </el-select>
      </div>
      <template v-if="localComponent.visualStyle?.border?.mode === 'visible'">
        <div class="form-group">
          <label class="form-label">边框颜色</label>
          <el-select v-model="localComponent.visualStyle.border.colorMode" aria-label="组件边框颜色" style="width: 100%" @change="emitChange">
            <el-option value="theme" label="跟随主题" />
            <el-option value="custom" label="自定义颜色" />
          </el-select>
        </div>
        <div v-if="localComponent.visualStyle.border.colorMode === 'custom'" class="form-group">
          <label class="form-label">自定义边框颜色</label>
          <el-color-picker v-model="localComponent.visualStyle.border.color" aria-label="自定义边框颜色" @change="emitChange" />
        </div>
        <div class="form-group">
          <label class="form-label">边框粗细</label>
          <el-select v-model="localComponent.visualStyle.border.width" aria-label="组件边框粗细" style="width: 100%" @change="emitChange">
            <el-option :value="1" label="1px" />
            <el-option :value="2" label="2px" />
          </el-select>
        </div>
        <div class="form-group">
          <label class="form-label">边框样式</label>
          <el-select v-model="localComponent.visualStyle.border.style" aria-label="组件边框样式" style="width: 100%" @change="emitChange">
            <el-option value="solid" label="实线" />
            <el-option value="dashed" label="虚线" />
          </el-select>
        </div>
      </template>
      <div class="form-group">
        <label class="form-label">背景</label>
        <el-select v-model="localComponent.visualStyle!.background!.mode" aria-label="组件背景" style="width: 100%" @change="emitChange">
          <el-option value="theme" label="跟随主题" />
          <el-option value="transparent" label="透明" />
          <el-option value="custom" label="自定义颜色" />
        </el-select>
      </div>
      <div v-if="localComponent.visualStyle?.background?.mode === 'custom'" class="form-group">
        <label class="form-label">自定义背景色</label>
        <el-color-picker v-model="localComponent.visualStyle.background.color" aria-label="自定义背景色" @change="emitChange" />
      </div>
      <div class="form-group">
        <label class="form-label">圆角</label>
        <el-select v-model="localComponent.visualStyle!.radius" aria-label="组件圆角" style="width: 100%" @change="emitChange">
          <el-option :value="0" label="无圆角" />
          <el-option :value="8" label="小圆角" />
          <el-option :value="12" label="标准圆角" />
          <el-option :value="16" label="大圆角" />
        </el-select>
      </div>
      <div class="form-group">
        <label class="form-label">阴影</label>
        <el-select v-model="localComponent.visualStyle!.shadow" aria-label="组件阴影" style="width: 100%" @change="emitChange">
          <el-option value="none" label="无阴影" />
          <el-option value="subtle" label="轻微阴影" />
          <el-option value="medium" label="标准阴影" />
        </el-select>
      </div>
      <div class="form-group">
        <label class="form-label">内边距</label>
        <el-select v-model="localComponent.visualStyle!.padding" aria-label="组件内边距" style="width: 100%" @change="emitChange">
          <el-option :value="0" label="紧凑" />
          <el-option :value="8" label="标准" />
          <el-option :value="16" label="宽松" />
        </el-select>
      </div>
      <div v-if="component.type === 'table'" class="form-group form-group-row">
        <label class="form-label">显示表头</label>
        <el-switch v-model="localComponent.showHeader" aria-label="显示表头" @change="emitChange" />
      </div>

      <!-- 组合卡片：容器配置 -->
      <template v-if="component.type === 'combination' && localComponent.containerConfig">
        <div class="form-group">
          <label class="form-label">{{ t('insight.combination.showTitle') }}</label>
          <el-switch v-model="localComponent.containerConfig.showTitle" :aria-label="t('insight.combination.showTitle')" @change="emitChange" />
        </div>
        <div class="form-group">
          <label class="form-label">{{ t('insight.combination.background') }}</label>
          <el-radio-group v-model="localComponent.containerConfig.backgroundMode" aria-label="组合卡片背景来源" @change="emitChange">
            <el-radio value="theme">跟随主题</el-radio>
            <el-radio value="custom">自定义颜色</el-radio>
          </el-radio-group>
        </div>
        <div v-if="localComponent.containerConfig.backgroundMode === 'custom'" class="form-group">
          <label class="form-label">自定义背景色</label>
          <div class="combination-color-row">
            <el-color-picker
              v-model="localComponent.containerConfig.background"
              :predefine="COMBINATION_BG_PRESETS"
              @change="emitChange"
            />
            <el-input
              v-model="localComponent.containerConfig.background"
              placeholder="#ffffff"
              :aria-label="t('insight.combination.background')"
              @change="emitChange"
            />
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">容器边框</label>
          <el-select v-model="localComponent.containerConfig.style.border.mode" aria-label="组合容器边框" style="width: 100%" @change="handleContainerBorderModeChange">
            <el-option value="theme" label="跟随主题" />
            <el-option value="visible" label="显示" />
            <el-option value="hidden" label="隐藏" />
          </el-select>
        </div>
        <template v-if="localComponent.containerConfig.style.border.mode === 'visible'">
          <div class="form-group">
            <label class="form-label">容器边框颜色</label>
            <el-select v-model="localComponent.containerConfig.style.border.colorMode" aria-label="组合容器边框颜色" style="width: 100%" @change="emitChange">
              <el-option value="theme" label="跟随主题" />
              <el-option value="custom" label="自定义颜色" />
            </el-select>
          </div>
          <div v-if="localComponent.containerConfig.style.border.colorMode === 'custom'" class="form-group">
            <label class="form-label">自定义容器边框颜色</label>
            <el-color-picker v-model="localComponent.containerConfig.style.border.color" aria-label="自定义容器边框颜色" @change="emitChange" />
          </div>
          <div class="form-group">
            <label class="form-label">容器边框粗细</label>
            <el-select v-model="localComponent.containerConfig.style.border.width" aria-label="组合容器边框粗细" style="width: 100%" @change="emitChange">
              <el-option :value="1" label="1px" />
              <el-option :value="2" label="2px" />
            </el-select>
          </div>
          <div class="form-group">
            <label class="form-label">容器边框样式</label>
            <el-select v-model="localComponent.containerConfig.style.border.style" aria-label="组合容器边框样式" style="width: 100%" @change="emitChange">
              <el-option value="solid" label="实线" />
              <el-option value="dashed" label="虚线" />
            </el-select>
          </div>
        </template>
        <div class="form-group">
          <label class="form-label">{{ t('insight.combination.radius') }}</label>
          <el-input-number v-model="localComponent.containerConfig.radius" :min="0" :max="48" :aria-label="t('insight.combination.radius')" @change="emitChange" />
        </div>
        <div class="form-group">
          <label class="form-label">{{ t('insight.combination.padding') }}</label>
          <el-input-number v-model="localComponent.containerConfig.padding" :min="0" :max="48" :aria-label="t('insight.combination.padding')" @change="emitChange" />
        </div>
        <div class="form-group">
          <label class="form-label">容器阴影</label>
          <el-select v-model="localComponent.containerConfig.shadow" aria-label="组合容器阴影" style="width: 100%" @change="emitChange">
            <el-option value="none" label="无" />
            <el-option value="subtle" label="轻微" />
            <el-option value="medium" label="明显" />
          </el-select>
        </div>
        <div class="form-group">
          <label class="form-label">{{ t('insight.combination.layoutMode') }}</label>
          <el-radio-group v-model="localComponent.containerConfig.layoutMode" @change="emitChange">
            <el-radio value="free">{{ t('insight.combination.layoutFree') }}</el-radio>
            <el-radio value="grid">{{ t('insight.combination.layoutGrid') }}</el-radio>
            <el-radio value="vertical">{{ t('insight.combination.layoutVertical') }}</el-radio>
          </el-radio-group>
        </div>
        <!-- 页签管理 -->
        <div class="form-group form-group-column">
          <label class="form-label">{{ t('insight.combination.tabs') }}</label>
          <div class="combination-tab-editor">
            <div
              v-for="tab in localComponent.containerConfig.tabs"
              :key="tab.id"
              class="combination-tab-row"
              :class="{ active: localComponent.containerConfig.activeTab === tab.id }"
            >
              <el-input v-model="tab.title" size="small" :aria-label="t('insight.combination.tabName')" @change="emitChange" />
              <button class="combination-tab-set" :class="{ on: localComponent.containerConfig.activeTab === tab.id }" @click="localComponent.containerConfig!.activeTab = tab.id; emitChange()">{{ t('insight.combination.activeTab') }}</button>
              <button class="combination-tab-del" @click="deleteCombinationTab(tab.id)">✕</button>
            </div>
            <button class="combination-tab-add" @click="addCombinationTab">{{ t('insight.combination.addTab') }}</button>
          </div>
        </div>
      </template>

      <!-- 图表类型（仅 chart 组件） -->
      <div v-if="component.type === 'chart'" class="form-group">
        <label class="form-label">{{ t('insight.property.chartType') }}</label>
        <el-select v-model="localComponent.chartType" :aria-label="t('insight.property.chartType')" filterable style="width: 100%" @change="emitChange">
          <el-option value="line" :label="t('insight.component.line')" />
          <el-option value="bar" :label="t('insight.component.bar')" />
          <el-option value="pie" :label="t('insight.component.pie')" />
          <el-option value="area" :label="t('insight.component.area')" />
          <el-option value="scatter" :label="t('insight.component.scatter')" />
          <el-option value="effectScatter" :label="t('insight.component.effectScatter')" />
          <el-option value="candlestick" :label="t('insight.component.candlestick')" />
          <el-option value="radar" :label="t('insight.component.radar')" />
          <el-option value="heatmap" :label="t('insight.component.heatmap')" />
          <el-option value="boxplot" :label="t('insight.component.boxplot')" />
          <el-option value="map" :label="t('insight.component.map')" />
          <el-option value="lines" :label="t('insight.component.lines')" />
          <el-option value="graph" :label="t('insight.component.graph')" />
          <el-option value="tree" :label="t('insight.component.tree')" />
          <el-option value="treemap" :label="t('insight.component.treemap')" />
          <el-option value="sunburst" :label="t('insight.component.sunburst')" />
          <el-option value="parallel" :label="t('insight.component.parallel')" />
          <el-option value="gauge" :label="t('insight.component.gauge')" />
          <el-option value="funnel" :label="t('insight.component.funnel')" />
          <el-option value="sankey" :label="t('insight.component.sankey')" />
          <el-option value="themeRiver" :label="t('insight.component.themeRiver')" />
          <el-option value="pictorialBar" :label="t('insight.component.pictorialBar')" />
        </el-select>
      </div>

      <!-- 数据绑定（kpi/chart/table 组件；筛选器、时间筛选与组合卡片无需数据源/指标） -->
      <template v-if="!useDatasetPipeline && component.type !== 'filter' && component.type !== 'timeFilter' && component.type !== 'combination'">
        <!-- 多指标模式开关（仅 kpi 组件） -->
        <div v-if="component.type === 'kpi'" class="form-group">
          <label class="form-label">{{ t('insight.property.multiKpi') }}</label>
          <el-switch
            v-model="localMultiKpi"
            :aria-label="t('insight.property.multiKpi')"
            
            @change="emitChange"
          />
          <InlineHelp label="多指标模式" :content="t('insight.property.multiKpiHint')" />
        </div>

        <!-- 多 Tab 模式开关 -->
        <div v-if="component.type === 'table' || component.type === 'chart' || component.type === 'kpi'" class="form-group">
          <label class="form-label">多 Tab 模式</label>
          <el-switch
            v-model="tabModeEnabled"
            aria-label="多 Tab 模式"
            
            @change="handleTabModeToggle"
          />
          <InlineHelp label="多 Tab 模式" content="开启后组件支持多个 Tab 切换不同数据源。" />
        </div>

        <!-- Tab 管理区域（多 Tab 模式开启时显示） -->
        <template v-if="tabModeEnabled">
          <div class="form-group form-group-column">
            <label class="form-label">Tab 列表</label>
          <div class="tab-list-editor" role="tablist" aria-label="属性面板 Tab 列表">
              <div
                v-for="(tab, idx) in localTabs"
                :key="tab.id"
                class="tab-item-row"
                :class="{ active: activeTabIndex === idx }"
                role="tab"
                :aria-selected="activeTabIndex === idx"
                :tabindex="activeTabIndex === idx ? 0 : -1"
                :aria-label="`Tab ${tab.title || idx + 1}`"
                @click="activeTabIndex = idx"
                @keydown="handleTabEditorKeydown($event, idx)"
              >
                <el-input
                  v-model="tab.title"
                
                  placeholder="Tab 标题"
                  style="flex: 1"
                  @change="emitTabChange"
                />
                <el-button
                  text
                  
                  @click.stop="removeTab(idx)"
                >✕</el-button>
              </div>
              <el-button
                text
                
                @click="addTab"
              >+ 添加 Tab</el-button>
            </div>
          </div>

          <!-- 当前选中 Tab 的数据源配置 -->
          <div v-if="activeTab" class="tab-datasource-section">
            <div class="tab-datasource-title">Tab「{{ activeTab.title || '未命名' }}」数据源</div>
            <div class="form-group">
              <label class="form-label">{{ t('insight.property.datasource') }}</label>
              <el-select
                v-model="activeTab.dataSource.datasourceId"
                :placeholder="t('insight.property.selectDatasource')"
                :aria-label="t('insight.property.datasource')"
                
                filterable
                style="width: 100%"
                @change="handleTabDatasourceChange"
              >
                <el-option-group v-for="group in datasourceGroups" :key="`tab-${group.category}`" :label="group.label">
                  <el-option v-for="ds in group.items" :key="ds.id" :label="ds.name" :value="ds.id" />
                </el-option-group>
              </el-select>
            </div>

            <div v-if="activeTab.dataSource.datasourceId && classifyDatasourceTypeFor(activeTab.dataSource.datasourceId) === 'aloudata'" class="form-group">
              <label class="form-label">{{ t('insight.property.metrics') }}</label>
              <el-select
                v-model="activeTab.dataSource.metrics"
                :placeholder="t('insight.property.selectMetrics')"
                :aria-label="t('insight.property.metrics')"
                
                multiple
                filterable
                remote
                :remote-method="searchMetrics"
                :loading="metricsLoading"
                style="width: 100%"
                @change="emitTabChange"
              >
                <el-option
                  v-for="m in metricsOptions"
                  :key="m.metricName"
                  :label="m.metricDisplayName || m.metricName"
                  :value="m.metricName"
                />
              </el-select>
            </div>

            <div v-if="activeTab.dataSource.datasourceId && classifyDatasourceTypeFor(activeTab.dataSource.datasourceId) === 'aloudata' && activeTab.dataSource.metrics.length" class="form-group">
              <label class="form-label">{{ t('insight.property.dimensions') }}</label>
              <el-select
                v-model="activeTab.dataSource.dimensions"
                :placeholder="t('insight.property.selectDimensions')"
                :aria-label="t('insight.property.dimensions')"
                
                multiple
                filterable
                remote
                :remote-method="searchDimensions"
                :loading="dimensionsLoading"
                style="width: 100%"
                @change="emitTabChange"
              >
                <el-option
                  v-for="d in dimensionsOptions"
                  :key="d.dimName"
                  :label="d.dimDisplayName || d.dimName"
                  :value="d.dimName"
                />
              </el-select>
            </div>

            <div class="form-group">
              <label class="form-label">{{ t('insight.property.limit') }}</label>
              <el-input-number
                v-model="activeTab.dataSource.limit"
                :aria-label="t('insight.property.limit')"
                :min="1"
                :max="500"
                
                style="width: 100%"
                @change="emitTabChange"
              />
            </div>
            <div v-if="activeTab.dataSource.datasourceId && classifyDatasourceTypeFor(activeTab.dataSource.datasourceId) === 'jdbc'" class="form-group jdbc-query-config">
              <label class="form-label">SQL 查询（仅 JDBC）</label>
              <el-input v-model="activeTab.dataSource.sql" type="textarea" :rows="6" aria-label="JDBC SQL 查询" placeholder="select ... from ..." @change="emitTabChange" />
              <span class="form-hint">当前 SQL 只在所选 JDBC 数据源上执行；跨源组合请在 Python 预处理中完成。</span>
            </div>
            <div v-else-if="activeTab.dataSource.datasourceId" class="form-hint source-binding-hint">
              {{ datasetCategoryLabel(classifyDatasourceTypeFor(activeTab.dataSource.datasourceId)) }} 数据源请通过下方统一数据集输入进行选择和预览。
            </div>
          </div>
        </template>

        <!-- 单数据源模式（原有逻辑） -->
        <template v-else>
          <div class="form-group">
            <label class="form-label">{{ t('insight.property.datasource') }}</label>
            <el-select
              v-model="localDataSource.datasourceId"
              :placeholder="t('insight.property.selectDatasource')"
              :aria-label="t('insight.property.datasource')"
              
              filterable
              style="width: 100%"
              @change="handleDatasourceChange"
            >
              <el-option-group v-for="group in datasourceGroups" :key="group.category" :label="group.label">
                <el-option v-for="ds in group.items" :key="ds.id" :label="ds.name" :value="ds.id" />
              </el-option-group>
            </el-select>
          </div>

          <div v-if="localDataSource.datasourceId && selectedDatasourceCategory === 'aloudata'" class="form-group">
            <label class="form-label">{{ t('insight.property.metrics') }}</label>
            <el-select
              v-model="localDataSource.metrics"
              :placeholder="t('insight.property.selectMetrics')"
              :aria-label="t('insight.property.metrics')"
              
              multiple
              filterable
              remote
              :remote-method="searchMetrics"
              :loading="metricsLoading"
              style="width: 100%"
              @change="handleMetricsChange"
            >
              <el-option
                v-for="m in metricsOptions"
                :key="m.metricName"
                :label="m.metricDisplayName || m.metricName"
                :value="m.metricName"
              />
            </el-select>
          </div>

          <div v-if="localDataSource.datasourceId && selectedDatasourceCategory === 'aloudata' && localDataSource.metrics.length" class="form-group">
            <label class="form-label">{{ t('insight.property.dimensions') }}</label>
            <el-select
              v-model="localDataSource.dimensions"
              :placeholder="t('insight.property.selectDimensions')"
              :aria-label="t('insight.property.dimensions')"
              
              multiple
              filterable
              remote
              :remote-method="searchDimensions"
              :loading="dimensionsLoading"
              style="width: 100%"
              @change="emitChange"
            >
              <el-option
                v-for="d in dimensionsOptions"
                :key="d.dimName"
                :label="d.dimDisplayName || d.dimName"
                :value="d.dimName"
              />
            </el-select>
          </div>

          <div class="form-group">
            <label class="form-label">{{ t('insight.property.limit') }}</label>
            <el-input-number
              v-model="localDataSource.limit"
              :aria-label="t('insight.property.limit')"
              :min="1"
              :max="500"
              
              style="width: 100%"
              @change="emitChange"
            />
          </div>

          <div v-if="localDataSource.datasourceId && selectedDatasourceCategory === 'jdbc'" class="form-group jdbc-query-config">
            <label class="form-label">SQL 查询（仅 JDBC）</label>
            <el-input v-model="localDataSource.sql" type="textarea" :rows="6" aria-label="JDBC SQL 查询" placeholder="select ... from ..." @change="emitChange" />
            <span class="form-hint">当前 SQL 只在所选 JDBC 数据源上执行；跨源组合请在 Python 预处理中完成。点击“验证数据”可先预览当前查询，确认后再固化为可复用数据集。</span>
          </div>
          <div v-else-if="localDataSource.datasourceId" class="form-hint source-binding-hint">
            {{ datasetCategoryLabel(selectedDatasourceCategory) }} 数据源请通过下方统一数据集输入进行选择和预览。
          </div>

          <!-- 验证数据按钮 -->
          <div v-if="canPreview" class="form-group preview-group">
            <el-button
              
              type="primary"
              :loading="previewLoading"
              @click="handlePreviewData"
            >
              {{ t('insight.property.previewData') }}
            </el-button>
          </div>

          <!-- 验证数据结果（简要状态） -->
          <div v-if="previewResult" class="preview-result">
            <div v-if="previewResult.error" class="preview-error">
              {{ previewResult.error }}
            </div>
            <div v-else class="preview-ok">
              {{ t('insight.property.previewChartOk') }}
            </div>
          </div>
        </template>
      </template>

      <!-- 筛选组件配置（仅 filter 组件） -->
      <template v-if="component.type === 'filter'">
        <!-- 选项来源优先配置；默认动态，静态模式才展开手动选项编辑。 -->
        <div class="form-group">
          <label class="form-label">{{ t('insight.property.filterOptions') }}</label>
          <div class="form-group-column" style="display: flex;align-items: unset;">
            <el-radio-group
              v-model="localFilterConfig.optionSource"
              @change="handleFilterOptionSourceChange"
            >
              <el-radio-button value="static">{{ t('insight.property.filterOptionStatic') }}</el-radio-button>
              <el-radio-button value="dynamic">{{ t('insight.property.filterOptionDynamic') }}</el-radio-button>
            </el-radio-group>
          </div>
        </div>

        <!-- 静态选项编辑：紧跟在静态选项来源按钮下方。 -->
        <div v-if="localFilterConfig.optionSource === 'static'" class="form-group form-group-column">
          <label class="form-label">{{ t('insight.property.filterStaticOptions') }}</label>
          <div class="static-options-list">
            <div
              v-for="(opt, idx) in localFilterConfig.staticOptions"
              :key="idx"
              class="static-option-row"
            >
              <el-input
                v-model="opt.label"
                :placeholder="t('insight.property.optionLabel')"
                style="flex: 1"
                @change="emitFilterConfigChange"
              />
              <el-input
                v-model="opt.value"
                :placeholder="t('insight.property.optionValue')"
                style="flex: 1"
                @change="emitFilterConfigChange"
              />
              <el-button
                text
                @click="removeStaticOption(idx)"
              >
                ✕
              </el-button>
            </div>
          </div>
          <el-button text @click="addStaticOption">
            + {{ t('insight.property.addOption') }}
          </el-button>
        </div>

        <template v-if="localFilterConfig.optionSource === 'dynamic'">
          <!-- 数据源（用于加载筛选字段维度列表）-->
          <div class="form-group">
            <label class="form-label">{{ t('insight.property.datasource') }}</label>
            <el-select
              v-model="localFilterDatasourceId"
              :placeholder="t('insight.property.selectDatasource')"
              :aria-label="t('insight.property.datasource')"
              filterable
              style="width: 100%"
              @change="handleFilterDatasourceChange"
            >
              <el-option-group v-for="group in filterDatasourceGroups" :key="`filter-${group.category}`" :label="group.label">
                <el-option v-for="ds in group.items" :key="ds.id" :label="ds.name" :value="ds.id" />
              </el-option-group>
            </el-select>
          </div>

          <!-- 筛选字段（从维度下拉选择）-->
          <div v-if="localFilterDatasourceId" class="form-group">
            <label class="form-label">{{ t('insight.property.filterField') }}</label>
            <el-select
              v-model="localFilterConfig.field"
              :placeholder="t('insight.property.selectDimensions')"
              :aria-label="t('insight.property.filterField')"
              filterable
              remote
              :remote-method="searchFilterDimensions"
              :loading="filterDimensionsLoading"
              style="width: 100%"
              @change="handleFilterFieldChange"
            >
              <el-option
                v-for="d in filterDimensionsOptions"
                :key="d.dimName"
                :label="d.dimDisplayName || d.dimName"
                :value="d.dimName"
              />
            </el-select>
          </div>
        </template>

        <!-- 选择行为：按业务字段决定是否允许多选、全部和不筛选 -->
        <div class="form-group form-group-column">
          <label class="form-label">{{ t('insight.property.filterSelectionMode') }}</label>
          <el-radio-group v-model="localFilterSelectionMode" @change="emitFilterConfigChange">
            <el-radio-button value="single">{{ t('insight.property.filterSelectionSingle') }}</el-radio-button>
            <el-radio-button value="multiple">{{ t('insight.property.filterSelectionMultiple') }}</el-radio-button>
          </el-radio-group>
        </div>

        <div class="form-group form-group-column">
          <label class="form-label">{{ t('insight.property.filterSelectionOptions') }}</label>
          <el-checkbox v-model="localFilterAllowSelectAll" @change="emitFilterConfigChange">
            {{ t('insight.property.filterAllowSelectAll') }}
          </el-checkbox>
          <el-checkbox v-model="localFilterAllowNoFilter" @change="emitFilterConfigChange">
            {{ t('insight.property.filterAllowNoFilter') }}
          </el-checkbox>
          <span class="form-hint">{{ t('insight.property.filterSelectionHint') }}</span>
        </div>

        <div class="form-group">
          <label class="form-label">{{ t('insight.property.filterDefaultValue') }}</label>
          <el-select
            v-model="localFilterDefaultValue"
            :multiple="localFilterSelectionMode === 'multiple'"
            :clearable="localFilterAllowNoFilter"
            :placeholder="t('insight.property.filterDefaultValuePlaceholder')"
            filterable
            :remote="localFilterConfig.optionSource === 'dynamic'"
            :remote-method="searchFilterDefaultValues"
            :loading="filterDefaultValuesLoading"
            :allow-create="localFilterConfig.optionSource === 'static'"
            default-first-option
            style="width: 100%"
            @change="emitFilterConfigChange"
            @visible-change="handleFilterDefaultVisibleChange"
          >
            <el-option
              v-for="opt in filterDefaultOptions"
              :key="opt.value"
              :label="opt.label || opt.value"
              :value="opt.value"
            />
          </el-select>
        </div>

        <!-- 筛选器作用范围 -->
        <div class="form-group">
          <label class="form-label">{{ t('insight.property.filterScope') }}</label>
          <el-radio-group
            v-model="localFilterScope"
            
            @change="emitFilterConfigChange"
          >
            <el-radio-button value="global">{{ t('insight.property.filterScopeGlobal') }}</el-radio-button>
            <el-radio-button value="scoped">{{ t('insight.property.filterScopeScoped') }}</el-radio-button>
          </el-radio-group>
        </div>

        <!-- 作用范围=指定组件时，选择目标组件 -->
        <div v-if="localFilterScope === 'scoped'" class="form-group">
          <label class="form-label">{{ t('insight.property.filterTargetComponents') }}</label>
          <el-select
            v-model="localTargetComponentIds"
            :placeholder="t('insight.property.filterTargetComponentsPlaceholder')"
            :aria-label="t('insight.property.filterTargetComponents')"
            
            multiple
            style="width: 100%"
            @change="emitFilterConfigChange"
          >
            <el-option
              v-for="c in selectableDataComponents"
              :key="c.id"
              :label="c.title || c.id"
              :value="c.id"
            />
          </el-select>
        </div>
      </template>

      <!-- 时间筛选组件配置（仅 timeFilter 组件；时间字段固定 metric_time，无需数据源/指标） -->
      <template v-if="component.type === 'timeFilter'">
        <div class="form-group form-group-column">
          <label class="form-label">{{ t('insight.property.timeFilterPresets') }}</label>
          <el-checkbox-group
            v-model="localTimeFilterPresets"
            
            @change="emitTimeFilterConfigChange"
          >
            <el-checkbox value="today">{{ t('insight.timeRange.today') }}</el-checkbox>
            <el-checkbox value="7d">{{ t('insight.timeRange.7d') }}</el-checkbox>
            <el-checkbox value="30d">{{ t('insight.timeRange.30d') }}</el-checkbox>
            <el-checkbox value="90d">{{ t('insight.timeRange.90d') }}</el-checkbox>
            <el-checkbox value="custom">{{ t('insight.timeRange.custom') }}</el-checkbox>
          </el-checkbox-group>
        </div>

        <div class="form-group">
          <label class="form-label">{{ t('insight.property.filterScope') }}</label>
          <el-radio-group
            v-model="localFilterScope"
            
            @change="emitTimeFilterConfigChange"
          >
            <el-radio-button value="global">{{ t('insight.property.filterScopeGlobal') }}</el-radio-button>
            <el-radio-button value="scoped">{{ t('insight.property.filterScopeScoped') }}</el-radio-button>
          </el-radio-group>
        </div>

        <div v-if="localFilterScope === 'scoped'" class="form-group">
          <label class="form-label">{{ t('insight.property.filterTargetComponents') }}</label>
          <el-select
            v-model="localTargetComponentIds"
            :placeholder="t('insight.property.filterTargetComponentsPlaceholder')"
            :aria-label="t('insight.property.filterTargetComponents')"
            
            multiple
            style="width: 100%"
            @change="emitTimeFilterConfigChange"
          >
            <el-option
              v-for="c in selectableDataComponents"
              :key="c.id"
              :label="c.title || c.id"
              :value="c.id"
            />
          </el-select>
        </div>
      </template>

      <!-- AI 分析组件配置（仅 aiAnalysis 组件） -->
      <template v-if="component.type === 'aiAnalysis'">
        <div class="form-group form-group-column">
          <label class="form-label">{{ t('insight.property.aiAnalysisPrompt') }}</label>
          <el-input
            v-model="localAiAnalysisPrompt"
            type="textarea"
            :rows="3"
            
            :placeholder="t('insight.property.aiAnalysisPromptPlaceholder')"
            @change="emitAiAnalysisConfigChange"
          />
        </div>
        <div class="form-group">
          <label class="form-label">{{ t('insight.property.aiAnalysisAutoGenerate') }}</label>
          <el-switch
            v-model="localAiAnalysisAutoGenerate"
            :aria-label="t('insight.property.aiAnalysisAutoGenerate')"
            
            @change="emitAiAnalysisConfigChange"
          />
        </div>
      </template>

      <!-- 数据组件绑定筛选器（kpi/chart/table 组件；组合卡片容器本身不参与筛选绑定） -->
      <template v-if="component.type !== 'filter' && component.type !== 'timeFilter' && component.type !== 'aiAnalysis' && component.type !== 'combination'">
        <div class="form-group">
          <label class="form-label">{{ t('insight.property.boundFilters') }}</label>
          <el-select
            v-model="localBoundFilterIds"
            :placeholder="t('insight.property.boundFiltersPlaceholder')"
            :aria-label="t('insight.property.boundFilters')"
            
            multiple
            clearable
            style="width: 100%"
            @change="emitChange"
          >
            <el-option
              v-for="f in selectableFilterComponents"
              :key="f.id"
              :label="f.title || f.id"
              :value="f.id"
            />
          </el-select>
        </div>

        <!-- 组件级时间筛选开关 -->
        <div class="form-group">
          <label class="form-label">{{ t('insight.property.enableTimeFilter') }}</label>
          <el-switch
            v-model="localEnableTimeFilter"
            :aria-label="t('insight.property.enableTimeFilter')"
            
            @change="emitChange"
          />
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, reactive, watch, ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { InsightComponent, ComponentDataSource, ComponentTab, InsightComponentData, FilterComponentConfig, TimeFilterComponentConfig, AIAnalysisComponentConfig, TimeRangePreset, FilterScope } from '@/types'
import { useDatasourceStore } from '@/stores/useDatasourceStore'
import * as datasourceApi from '@/api/datasource'
import * as semanticModelApi from '@/api/semantic-model'
import * as insightDashboardApi from '@/api/insight-dashboard'
import { classifyDatasourceType, datasetCategoryLabel, groupDatasources, type DatasourceCategory } from '@/utils/data-binding'
import { CARD_BG_PRESETS } from '@/utils/color-presets'
import { normalizeCombinationBackgroundMode } from '@/utils/combination-theme'
import { normalizeComponentVisualStyle } from '@/utils/component-visual-style'
import InlineHelp from './property/InlineHelp.vue'

defineOptions({
  name: 'PropertyPanel',
})

const { t } = useI18n()

const props = defineProps<{
  /** 当前选中的组件 */
  component: InsightComponent | null
  /** 仪表盘所有组件列表（用于筛选器绑定配置） */
  allComponents?: InsightComponent[]
  /** 展示组件是否使用新的组件级数据集编排入口。 */
  useDatasetPipeline?: boolean
}>()

const useDatasetPipeline = computed(() => Boolean(props.useDatasetPipeline))

const emit = defineEmits<{
  (e: 'change', component: InsightComponent): void
  (e: 'preview', data: InsightComponentData): void
  (e: 'collapse'): void
  /** 组合卡片：请求新增页签（由编辑器对 schema 执行，含首次添加时的子卡片平移） */
  (e: 'combination-add-tab'): void
  /** 组合卡片：请求删除页签（由编辑器执行二次确认与最后一个页签的组件平移） */
  (e: 'combination-remove-tab', tabId: string): void
}>()

const datasourceStore = useDatasourceStore()
const datasourceGroups = computed(() => groupDatasources(datasourceStore.datasources))
/** 筛选器当前仅支持从 Aloudata 数据源选择维度。 */
const filterDatasourceGroups = computed(() => datasourceGroups.value.filter(group => group.category === 'aloudata'))

/** 组合卡片背景色预设 · 统一收敛到 utils/color-presets.ts（与指标样式弹窗共用一处来源） */
const COMBINATION_BG_PRESETS = CARD_BG_PRESETS

function handleContainerBorderModeChange(mode: 'theme' | 'visible' | 'hidden'): void {
  if (!localComponent.containerConfig) return
  localComponent.containerConfig.style.border.enabled = mode === 'visible'
  localComponent.containerConfig.style.border.colorMode = mode === 'theme' ? 'theme' : localComponent.containerConfig.style.border.colorMode ?? 'custom'
  emitChange()
}

/** 本地编辑副本（深拷贝） */
const localComponent = reactive<InsightComponent>({
  id: '',
  type: 'kpi',
  title: '',
  position: { x: 0, y: 0, w: 6, h: 4 },
})

const localDataSource = reactive<ComponentDataSource>({
  datasourceId: '',
  metrics: [],
  dimensions: [],
  filters: [],
  limit: 100,
})

/** 筛选组件配置本地副本 */
const localFilterConfig = reactive<FilterComponentConfig>({
  field: '',
  optionSource: 'dynamic',
  staticOptions: [],
})
const localFilterSelectionMode = ref<'single' | 'multiple'>('single')
const localFilterAllowSelectAll = ref(false)
const localFilterAllowNoFilter = ref(true)
const localFilterDefaultValue = ref<string | string[] | null>(null)
/** 筛选器数据源 ID */
const localFilterDatasourceId = ref<string>('')
/** 筛选器维度选项与加载状态 */
const filterDimensionsOptions = ref<Array<{ dimName: string; dimDisplayName: string }>>([])
const filterDimensionsLoading = ref(false)
const filterDefaultValues = ref<Array<{ label: string; value: string }>>([])
const filterDefaultValuesLoading = ref(false)
let filterDefaultValuesSearchTimer: ReturnType<typeof setTimeout> | null = null
const filterDefaultOptions = computed(() => localFilterConfig.optionSource === 'static'
  ? (localFilterConfig.staticOptions ?? [])
  : filterDefaultValues.value)

/** 时间筛选组件配置本地副本 */
const localTimeFilterConfig = reactive<TimeFilterComponentConfig>({
  field: 'metric_time',
})
const localTimeFilterPresets = ref<TimeRangePreset[]>(['today', '7d', '30d', '90d', 'custom'])

/** 筛选器作用范围本地副本 */
const localFilterScope = ref<FilterScope>('global')
const localTargetComponentIds = ref<string[]>([])
/** 图表组件绑定的筛选器 ID 列表 */
const localBoundFilterIds = ref<string[]>([])
/** 组件级时间筛选开关 */
const localEnableTimeFilter = ref(false)
/** 多指标模式开关（仅 kpi 组件） */
const localMultiKpi = ref(false)
/** AI 分析组件配置本地副本 */
const localAiAnalysisPrompt = ref('')
const localAiAnalysisAutoGenerate = ref(false)

/** 多 Tab 模式状态 */
const tabModeEnabled = ref(false)
const localTabs = ref<ComponentTab[]>([])
const activeTabIndex = ref(0)
const activeTab = computed(() => localTabs.value[activeTabIndex.value] ?? null)

function handleTabEditorKeydown(event: KeyboardEvent, index: number): void {
  if (localTabs.value.length < 2) return
  let nextIndex = index
  if (event.key === 'ArrowRight' || event.key === 'ArrowDown') nextIndex = (index + 1) % localTabs.value.length
  else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') nextIndex = (index - 1 + localTabs.value.length) % localTabs.value.length
  else if (event.key === 'Home') nextIndex = 0
  else if (event.key === 'End') nextIndex = localTabs.value.length - 1
  else if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); activeTabIndex.value = index; return }
  else return
  event.preventDefault()
  activeTabIndex.value = nextIndex
  nextTick(() => {
    const tab = Array.from(document.querySelectorAll<HTMLElement>('.property-panel [role="tab"]'))[nextIndex]
    tab?.focus()
  })
}

/** 指标/维度选项与加载状态 */
const metricsOptions = ref<Array<{ metricName: string; metricDisplayName: string }>>([])
const dimensionsOptions = ref<Array<{ dimName: string; dimDisplayName: string }>>([])
const metricsLoading = ref(false)
const dimensionsLoading = ref(false)

/** 验证数据相关 */
const previewLoading = ref(false)
const previewResult = ref<InsightComponentData | null>(null)

function classifyDatasourceTypeFor(datasourceId: string): DatasourceCategory {
  const datasource = datasourceStore.datasources.find(ds => String(ds.id) === String(datasourceId))
  return classifyDatasourceType(datasource?.sourceType)
}

const selectedDatasourceCategory = computed<DatasourceCategory>(() =>
  classifyDatasourceTypeFor(localDataSource.datasourceId),
)

/** 是否可以预览（按数据源类型检查对应配置） */
const canPreview = computed(() => {
  if (!localDataSource.datasourceId) return false
  if (selectedDatasourceCategory.value === 'aloudata') return localDataSource.metrics.length > 0
  if (selectedDatasourceCategory.value === 'jdbc') return Boolean(localDataSource.sql?.trim())
  return Boolean(localDataSource.datasetId)
})

/** 可选的目标组件列表（数据组件：kpi/chart/table，排除自身） */
const selectableDataComponents = computed(() => {
  return (props.allComponents ?? []).filter(c =>
    c.type !== 'filter' && c.type !== 'timeFilter' && c.id !== localComponent.id
  )
})

/** 可选的筛选器组件列表（filter/timeFilter，排除自身） */
const selectableFilterComponents = computed(() => {
  return (props.allComponents ?? []).filter(c =>
    (c.type === 'filter' || c.type === 'timeFilter') && c.id !== localComponent.id
  )
})

/** 搜索防抖定时器 */
let metricsSearchTimer: ReturnType<typeof setTimeout> | null = null
let dimensionsSearchTimer: ReturnType<typeof setTimeout> | null = null
let filterDimensionsSearchTimer: ReturnType<typeof setTimeout> | null = null

/**
 * 结构签名：组合卡片的容器内结构（页签 id + 各自子卡片数 + 顶层 children 数）。
 * 画布内的容器组件会「原地」增删子卡片/页签（props.component 引用不变），
 * 只依赖引用变化会让本地副本过期，之后任意 emitChange 会把旧 children 覆盖回 schema，导致子卡片丢失。
 */
function componentSignature(c: InsightComponent | null | undefined): string {
  if (!c || c.type !== 'combination' || !c.containerConfig) return ''
  const tabs = (c.containerConfig.tabs ?? []).map((t) => `${t.id}:${t.children?.length ?? 0}`).join(',')
  return `${tabs}|${c.children?.length ?? 0}`
}

/** 监听外部 component 变化（引用或组合卡片结构变化），同步到本地，避免 emitChange 导致的循环 */
function defaultCombinationConfig(): NonNullable<InsightComponent['containerConfig']> {
  return {
    title: '',
    showTitle: true,
    background: '#ffffff',
    backgroundMode: 'theme',
    radius: 12,
    padding: 16,
    shadow: 'none',
    layoutMode: 'free',
    tabs: [],
    activeTab: undefined,
    style: { border: { enabled: false, color: 'transparent', mode: 'hidden', colorMode: 'theme', width: 1, style: 'solid' } },
  }
}

function replaceLocalComponent(component: InsightComponent): void {
  const next = JSON.parse(JSON.stringify(component)) as InsightComponent
  next.titleBarStyle ??= 'standard'
  next.visualStyle = normalizeComponentVisualStyle(next.visualStyle, next.type)
  if (next.type === 'combination') {
    const defaults = defaultCombinationConfig()
    const config = next.containerConfig ?? {}
    const background = typeof config.background === 'string' ? config.background : defaults.background
    const backgroundMode = normalizeCombinationBackgroundMode(config.backgroundMode, background)
    next.containerConfig = {
      ...defaults,
      ...config,
      background,
      backgroundMode,
      tabs: config.tabs ?? defaults.tabs,
      style: {
        ...defaults.style,
        ...config.style,
        border: {
          ...defaults.style.border,
          ...config.style?.border,
          mode: config.style?.border?.mode ?? (config.style?.border?.enabled ? 'visible' : 'hidden'),
          colorMode: config.style?.border?.colorMode ?? (config.style?.border?.color && config.style.border.color !== 'transparent' ? 'custom' : 'theme'),
        },
      },
    }
  }
  const localRecord = localComponent as unknown as Record<string, unknown>
  for (const key of Object.keys(localRecord)) {
    if (!(key in next)) delete localRecord[key]
  }
  Object.assign(localComponent, next)
}

watch(
  () => [props.component, componentSignature(props.component)] as const,
  ([newComp]) => {
    if (!newComp) {
      return
    }
    previewResult.value = null
    replaceLocalComponent(newComp)
    if (newComp.dataSource) {
      Object.assign(localDataSource, JSON.parse(JSON.stringify(newComp.dataSource)))
      if (classifyDatasourceTypeFor(localDataSource.datasourceId) === 'aloudata') {
        loadMetrics(localDataSource.datasourceId)
        loadDimensions(localDataSource.datasourceId, localDataSource.metrics)
      }
    } else {
      localDataSource.datasourceId = ''
      localDataSource.metrics = []
      localDataSource.dimensions = []
      metricsOptions.value = []
      dimensionsOptions.value = []
    }
    // 同步筛选组件配置
    if (newComp.type === 'filter') {
      const config = newComp.config as FilterComponentConfig | undefined
      localFilterConfig.field = config?.field ?? ''
      localFilterConfig.optionSource = config?.optionSource ?? 'dynamic'
      localFilterConfig.staticOptions = config?.staticOptions ? JSON.parse(JSON.stringify(config.staticOptions)) : []
      localFilterSelectionMode.value = config?.selectionMode ?? 'single'
      localFilterAllowSelectAll.value = config?.allowSelectAll ?? false
      localFilterAllowNoFilter.value = config?.allowNoFilter ?? true
      localFilterDefaultValue.value = config?.defaultValue == null
        ? null
        : (Array.isArray(config.defaultValue) ? [...config.defaultValue] : config.defaultValue)
      // 同步筛选器数据源 ID（优先使用 config.datasourceId，兼容旧数据）
      localFilterDatasourceId.value = config?.datasourceId ?? ''
      if (localFilterConfig.optionSource === 'dynamic' && localFilterDatasourceId.value) {
        loadFilterDimensions(localFilterDatasourceId.value)
      } else {
        filterDimensionsOptions.value = []
      }
      if (localFilterConfig.optionSource === 'dynamic' && localFilterDatasourceId.value && localFilterConfig.field) {
        loadFilterDefaultValues()
      } else {
        filterDefaultValues.value = []
      }
    }
    // 同步时间筛选组件配置
    if (newComp.type === 'timeFilter') {
      const config = newComp.config as TimeFilterComponentConfig | undefined
      localTimeFilterConfig.field = config?.field ?? 'metric_time'
      localTimeFilterPresets.value = config?.availablePresets ?? ['today', '7d', '30d', '90d', 'custom']
    }
    // 同步筛选器作用范围配置
    if (newComp.type === 'filter') {
      const config = newComp.config as FilterComponentConfig | undefined
      localFilterScope.value = config?.scope ?? 'global'
      localTargetComponentIds.value = config?.targetComponentIds ?? []
    }
    if (newComp.type === 'timeFilter') {
      const config = newComp.config as TimeFilterComponentConfig | undefined
      localFilterScope.value = config?.scope ?? 'global'
      localTargetComponentIds.value = config?.targetComponentIds ?? []
    }
    // 同步数据组件的绑定筛选器
    if (newComp.type !== 'filter' && newComp.type !== 'timeFilter') {
      localBoundFilterIds.value = newComp.boundFilterIds ?? []
      localEnableTimeFilter.value = newComp.enableTimeFilter ?? false
    }
    // 同步多指标模式配置（仅 kpi 组件）
    if (newComp.type === 'kpi') {
      localMultiKpi.value = newComp.multiKpi ?? false
    }
    // 同步 AI 分析组件配置
    if (newComp.type === 'aiAnalysis') {
      const config = newComp.config as AIAnalysisComponentConfig | undefined
      localAiAnalysisPrompt.value = config?.promptTemplate ?? ''
      localAiAnalysisAutoGenerate.value = config?.autoGenerate ?? false
    }
    // 同步多 Tab 配置（仅在外部组件引用变化时同步，避免 emitTabChange 导致的循环重置）
    if (newComp.tabs && newComp.tabs.length > 0) {
      tabModeEnabled.value = true
      // 仅在 tabs 引用变化时才深拷贝覆盖，避免编辑中覆盖本地状态
      const newTabsJson = JSON.stringify(newComp.tabs)
      const localTabsJson = JSON.stringify(localTabs.value)
      if (newTabsJson !== localTabsJson) {
        localTabs.value = JSON.parse(JSON.stringify(newComp.tabs))
        // 保持当前选中 Tab 不变，仅在越界时修正
        if (activeTabIndex.value >= localTabs.value.length) {
          activeTabIndex.value = 0
        }
        // 加载当前 Tab 的指标/维度选项
        const currentTab = localTabs.value[activeTabIndex.value]
        if (currentTab?.dataSource?.datasourceId) {
          loadMetrics(currentTab.dataSource.datasourceId)
          if (currentTab.dataSource.metrics.length) {
            loadDimensions(currentTab.dataSource.datasourceId, currentTab.dataSource.metrics)
          }
        }
      }
    } else {
      tabModeEnabled.value = false
      localTabs.value = []
      activeTabIndex.value = 0
    }
  },
  { immediate: true }
)

/** 加载指标选项（初始加载，不带关键字） */
async function loadMetrics(datasourceId: string): Promise<void> {
  if (!datasourceId) {
    metricsOptions.value = []
    return
  }
  try {
    const result = await datasourceApi.listSyncedMetrics(datasourceId, 1, 50)
    metricsOptions.value = (result as unknown as Array<{ metricName: string; metricDisplayName: string }>) ?? []
  } catch (e) {
    console.error('[PropertyPanel] load metrics error:', e)
  }
}

/** 加载维度选项（基于已选指标关联的维度） */
async function loadDimensions(datasourceId: string, metricNames: string[], keyword?: string): Promise<void> {
  if (!datasourceId || !metricNames.length) {
    dimensionsOptions.value = []
    return
  }
  try {
    const result = await datasourceApi.listMetricsDimensionDetails(datasourceId, metricNames, keyword)
    dimensionsOptions.value = (result as unknown as Array<{ dimName: string; dimDisplayName: string }>) ?? []
  } catch (e) {
    console.error('[PropertyPanel] load dimensions error:', e)
  }
}

/** 获取当前生效的数据源 ID（Tab 模式取 activeTab，否则取 localDataSource） */
function getEffectiveDatasourceId(): string {
  return tabModeEnabled.value && activeTab.value
    ? activeTab.value.dataSource.datasourceId
    : localDataSource.datasourceId
}

/** 获取当前生效的指标列表 */
function getEffectiveMetrics(): string[] {
  return tabModeEnabled.value && activeTab.value
    ? activeTab.value.dataSource.metrics
    : localDataSource.metrics
}

/** 远程搜索指标（防抖 300ms） */
function searchMetrics(query: string): void {
  if (metricsSearchTimer) {
    clearTimeout(metricsSearchTimer)
  }
  const dsId = getEffectiveDatasourceId()
  if (!dsId) {
    return
  }
  metricsSearchTimer = setTimeout(async () => {
    metricsLoading.value = true
    try {
      const keyword = query.trim() || undefined
      const result = await datasourceApi.listSyncedMetrics(dsId, 1, 50, keyword)
      metricsOptions.value = (result as unknown as Array<{ metricName: string; metricDisplayName: string }>) ?? []
    } catch (e) {
      console.error('[PropertyPanel] search metrics error:', e)
    } finally {
      metricsLoading.value = false
    }
  }, 300)
}

/** 远程搜索维度（基于已选指标关联维度，防抖 300ms） */
function searchDimensions(query: string): void {
  if (dimensionsSearchTimer) {
    clearTimeout(dimensionsSearchTimer)
  }
  const dsId = getEffectiveDatasourceId()
  const metrics = getEffectiveMetrics()
  if (!dsId || !metrics.length) {
    return
  }
  dimensionsSearchTimer = setTimeout(async () => {
    dimensionsLoading.value = true
    try {
      const keyword = query.trim() || undefined
      await loadDimensions(dsId, metrics, keyword)
    } catch (e) {
      console.error('[PropertyPanel] search dimensions error:', e)
    } finally {
      dimensionsLoading.value = false
    }
  }, 300)
}

/** 验证数据：调用 preview-component 端点，结果 emit 到画布渲染 */
async function handlePreviewData(): Promise<void> {
  if (!canPreview.value) {
    return
  }
  previewLoading.value = true
  previewResult.value = null
  try {
    const comp: InsightComponent = {
      ...JSON.parse(JSON.stringify(localComponent)),
      dataSource: JSON.parse(JSON.stringify(localDataSource)),
    }
    const result = await insightDashboardApi.previewComponent(comp) as unknown as InsightComponentData
    previewResult.value = result
    // 将渲染数据 emit 给 Editor，写入 componentDataMap，画布组件自动渲染
    emit('preview', result)
    if (result.error) {
      ElMessage.warning(result.error)
    } else {
      ElMessage.success(t('insight.property.previewChartOk'))
    }
  } catch (e: any) {
    const errorData: InsightComponentData = { componentId: localComponent.id, renderType: 'table', error: e.message ?? t('insight.previewDataFailed') }
    previewResult.value = errorData
    emit('preview', errorData)
  } finally {
    previewLoading.value = false
  }
}

/** 数据源变更时重新加载指标/维度 */
function handleDatasourceChange(): void {
  const category = selectedDatasourceCategory.value
  localDataSource.sourceType = category === 'unknown' ? undefined : category.toUpperCase()
  localDataSource.metrics = []
  localDataSource.dimensions = []
  localDataSource.sql = category === 'jdbc' ? (localDataSource.sql ?? '') : undefined
  localDataSource.datasetId = category === 'jdbc' ? localDataSource.datasetId : undefined
  previewResult.value = null
  dimensionsOptions.value = []
  if (category === 'aloudata') loadMetrics(localDataSource.datasourceId)
  emitChange()
}

/** 指标变更时重新加载关联维度，并清除不在关联范围内的已选维度 */
function handleMetricsChange(): void {
  previewResult.value = null
  if (localDataSource.datasourceId && localDataSource.metrics.length) {
    loadDimensions(localDataSource.datasourceId, localDataSource.metrics).then(() => {
      // 清除不在关联维度选项中的已选维度
      const validDimNames = new Set(dimensionsOptions.value.map(d => d.dimName))
      const filtered = localDataSource.dimensions.filter(d => validDimNames.has(d))
      if (filtered.length !== localDataSource.dimensions.length) {
        localDataSource.dimensions = filtered
      }
    })
  } else {
    localDataSource.dimensions = []
    dimensionsOptions.value = []
  }
  emitChange()
}

/** 触发变更事件（不包含 position，position 由画布拖拽管理） */
function emitChange(): void {
  previewResult.value = null
  const updated: InsightComponent = {
    ...JSON.parse(JSON.stringify(localComponent)),
    // position 不 emit，由画布拖拽/缩放管理
    dataSource: tabModeEnabled.value ? undefined : (localDataSource.datasourceId ? JSON.parse(JSON.stringify({
      ...localDataSource,
      sourceType: selectedDatasourceCategory.value === 'unknown' ? undefined : selectedDatasourceCategory.value.toUpperCase(),
    })) : undefined),
    tabs: tabModeEnabled.value && localTabs.value.length > 0
      ? JSON.parse(JSON.stringify(localTabs.value))
      : undefined,
    boundFilterIds: localBoundFilterIds.value.length > 0 ? [...localBoundFilterIds.value] : undefined,
    enableTimeFilter: localEnableTimeFilter.value || undefined,
    multiKpi: localComponent.type === 'kpi' ? (localMultiKpi.value || undefined) : undefined,
  }
  delete (updated as any).position
  emit('change', updated)
}

/** 筛选器数据源变更时清空已选字段并重新加载维度列表 */
function handleFilterDatasourceChange(): void {
  localFilterConfig.field = ''
  filterDimensionsOptions.value = []
  filterDefaultValues.value = []
  if (localFilterDatasourceId.value) {
    loadFilterDimensions(localFilterDatasourceId.value)
  }
  emitFilterConfigChange()
}

/** 维度字段变更后，默认值候选必须来自该维度的真实值。 */
function handleFilterFieldChange(): void {
  localFilterDefaultValue.value = localFilterSelectionMode.value === 'multiple' ? [] : null
  filterDefaultValues.value = []
  if (localFilterConfig.optionSource === 'dynamic') {
    loadFilterDefaultValues()
  }
  emitFilterConfigChange()
}

/** 切换选项来源时清理不属于当前来源的默认值并刷新候选。 */
function handleFilterOptionSourceChange(): void {
  localFilterDefaultValue.value = localFilterSelectionMode.value === 'multiple' ? [] : null
  filterDefaultValues.value = []
  if (localFilterConfig.optionSource === 'dynamic') {
    if (localFilterDatasourceId.value) loadFilterDimensions(localFilterDatasourceId.value)
    loadFilterDefaultValues()
  } else {
    filterDimensionsOptions.value = []
  }
  emitFilterConfigChange()
}

async function loadFilterDefaultValues(keyword?: string): Promise<void> {
  if (localFilterConfig.optionSource !== 'dynamic' || !localFilterDatasourceId.value || !localFilterConfig.field) {
    filterDefaultValues.value = []
    return
  }
  filterDefaultValuesLoading.value = true
  try {
    const result = await datasourceApi.listDimensionValues(
      localFilterDatasourceId.value,
      localFilterConfig.field,
      keyword?.trim() || undefined,
      200,
    )
    const values = (result as unknown as string[]) ?? []
    filterDefaultValues.value = values.map(value => ({ label: value, value }))
  } catch (e) {
    console.error('[PropertyPanel] load filter default values error:', e)
    filterDefaultValues.value = []
  } finally {
    filterDefaultValuesLoading.value = false
  }
}

function searchFilterDefaultValues(query: string): void {
  if (filterDefaultValuesSearchTimer) clearTimeout(filterDefaultValuesSearchTimer)
  filterDefaultValuesSearchTimer = setTimeout(() => {
    loadFilterDefaultValues(query)
  }, 300)
}

function handleFilterDefaultVisibleChange(visible: boolean): void {
  if (visible && localFilterConfig.optionSource === 'dynamic' && filterDefaultValues.value.length === 0) {
    loadFilterDefaultValues()
  }
}

/** 加载筛选器维度选项（复用已同步维度列表接口，支持关键字服务端搜索）*/
async function loadFilterDimensions(datasourceId: string, keyword?: string): Promise<void> {
  if (!datasourceId) {
    filterDimensionsOptions.value = []
    return
  }
  if (classifyDatasourceTypeFor(datasourceId) !== 'aloudata') {
    filterDimensionsOptions.value = []
    return
  }
  filterDimensionsLoading.value = true
  try {
    const result = await semanticModelApi.pageAloudataDimensions(String(datasourceId), {
      pageNumber: 1,
      pageSize: 200,
      keyword,
    })
    const list = ((result as unknown as { records?: Array<{ dimName: string; dimDisplayName: string }> })?.records ?? [])
    // 按 dimName 去重，避免目录接口返回重复维度。
    const seen = new Set<string>()
    filterDimensionsOptions.value = list.filter((d) => {
      if (!d.dimName || seen.has(d.dimName)) return false
      seen.add(d.dimName)
      return true
    })
  } catch (e) {
    console.error('[PropertyPanel] load filter dimensions error:', e)
  } finally {
    filterDimensionsLoading.value = false
  }
}

/** 筛选字段维度远程搜索（服务端关键字，防抖 300ms，避免 >200 维度被前端截断）*/
function searchFilterDimensions(query: string): void {
  if (filterDimensionsSearchTimer) {
    clearTimeout(filterDimensionsSearchTimer)
  }
  if (!localFilterDatasourceId.value) {
    return
  }
  filterDimensionsSearchTimer = setTimeout(() => {
    loadFilterDimensions(localFilterDatasourceId.value, query.trim() || undefined)
  }, 300)
}

/** 筛选配置变更时 emit（将 config 写入组件） */
function emitFilterConfigChange(): void {
  const config: FilterComponentConfig = {
    field: localFilterConfig.field,
    optionSource: localFilterConfig.optionSource,
    // 两种模式都需要数据源来加载筛选字段维度列表
    datasourceId: localFilterDatasourceId.value || undefined,
    staticOptions: localFilterConfig.optionSource === 'static'
      ? JSON.parse(JSON.stringify(localFilterConfig.staticOptions))
      : undefined,
    selectionMode: localFilterSelectionMode.value,
    allowSelectAll: localFilterAllowSelectAll.value,
    allowNoFilter: localFilterAllowNoFilter.value,
    defaultValue: localFilterDefaultValue.value,
    scope: localFilterScope.value,
    targetComponentIds: localFilterScope.value === 'scoped' ? [...localTargetComponentIds.value] : undefined,
  }
  const updated: InsightComponent = {
    ...JSON.parse(JSON.stringify(localComponent)),
    config,
  }
  delete (updated as any).position
  emit('change', updated)
}

/** 添加静态选项 */
function addStaticOption(): void {
  if (!localFilterConfig.staticOptions) {
    localFilterConfig.staticOptions = []
  }
  localFilterConfig.staticOptions.push({ label: '', value: '' })
}

/** 移除静态选项 */
function removeStaticOption(idx: number): void {
  if (localFilterConfig.staticOptions) {
    localFilterConfig.staticOptions.splice(idx, 1)
    emitFilterConfigChange()
  }
}

/** 时间筛选配置变更时 emit（将 config 写入组件） */
function emitTimeFilterConfigChange(): void {
  const config: TimeFilterComponentConfig = {
    field: localTimeFilterConfig.field || 'metric_time',
    availablePresets: localTimeFilterPresets.value.length > 0
      ? [...localTimeFilterPresets.value]
      : undefined,
    scope: localFilterScope.value,
    targetComponentIds: localFilterScope.value === 'scoped' ? [...localTargetComponentIds.value] : undefined,
  }
  const updated: InsightComponent = {
    ...JSON.parse(JSON.stringify(localComponent)),
    config,
  }
  delete (updated as any).position
  emit('change', updated)
}

/** AI 分析组件配置变更时 emit */
function emitAiAnalysisConfigChange(): void {
  const config: AIAnalysisComponentConfig = {
    promptTemplate: localAiAnalysisPrompt.value || undefined,
    autoGenerate: localAiAnalysisAutoGenerate.value || undefined,
  }
  const updated: InsightComponent = {
    ...JSON.parse(JSON.stringify(localComponent)),
    config,
  }
  delete (updated as any).position
  emit('change', updated)
}

/** 组合卡片：添加页签（交由编辑器执行：首次添加时会把容器内已有子卡片平移进该页签） */
function addCombinationTab(): void {
  emit('combination-add-tab')
}

/** 组合卡片：删除页签（交由编辑器执行：二次确认 + 删最后一个页签时组件平移回容器） */
function deleteCombinationTab(id: string): void {
  emit('combination-remove-tab', id)
}

/** 多 Tab 模式开关切换 */
function handleTabModeToggle(): void {
  if (tabModeEnabled.value) {
    // 开启 Tab 模式：如果已有单数据源配置，迁移为第一个 Tab
    if (localTabs.value.length === 0) {
      const tabId = 'tab_' + Date.now()
      localTabs.value = [{
        id: tabId,
        title: localComponent.title || 'Tab 1',
        dataSource: JSON.parse(JSON.stringify(localDataSource)),
      }]
      activeTabIndex.value = 0
    }
  } else {
    // 关闭 Tab 模式：如果只有一个 Tab，迁移回单数据源
    if (localTabs.value.length === 1) {
      const tab = localTabs.value[0]
      Object.assign(localDataSource, JSON.parse(JSON.stringify(tab.dataSource)))
    }
    localTabs.value = []
  }
  emitTabChange()
}

/** 添加 Tab */
function addTab(): void {
  const tabId = 'tab_' + Date.now()
  localTabs.value.push({
    id: tabId,
    title: `Tab ${localTabs.value.length + 1}`,
    dataSource: {
      datasourceId: '',
      metrics: [],
      dimensions: [],
      filters: [],
      limit: 100,
    },
  })
  activeTabIndex.value = localTabs.value.length - 1
  // 切换到新 Tab 时加载对应指标选项
  metricsOptions.value = []
  dimensionsOptions.value = []
  emitTabChange()
}

/** 删除 Tab */
function removeTab(idx: number): void {
  localTabs.value.splice(idx, 1)
  if (activeTabIndex.value >= localTabs.value.length) {
    activeTabIndex.value = Math.max(0, localTabs.value.length - 1)
  }
  emitTabChange()
}

/** 切换 Tab 时重新加载指标/维度选项 */
watch(activeTabIndex, () => {
  metricsOptions.value = []
  dimensionsOptions.value = []
  if (activeTab.value?.dataSource?.datasourceId) {
    loadMetrics(activeTab.value.dataSource.datasourceId)
    if (activeTab.value.dataSource.metrics.length) {
      loadDimensions(activeTab.value.dataSource.datasourceId, activeTab.value.dataSource.metrics)
    }
  }
})

/** Tab 数据源变更时重新加载指标/维度 */
function handleTabDatasourceChange(): void {
  if (activeTab.value) {
    const category = classifyDatasourceTypeFor(activeTab.value.dataSource.datasourceId)
    activeTab.value.dataSource.sourceType = category === 'unknown' ? undefined : category.toUpperCase()
    activeTab.value.dataSource.metrics = []
    activeTab.value.dataSource.dimensions = []
    dimensionsOptions.value = []
    if (category === 'aloudata') loadMetrics(activeTab.value.dataSource.datasourceId)
  }
  emitTabChange()
}

/** Tab 配置变更时 emit */
function emitTabChange(): void {
  const updated: InsightComponent = {
    ...JSON.parse(JSON.stringify(localComponent)),
    tabs: tabModeEnabled.value && localTabs.value.length > 0
      ? JSON.parse(JSON.stringify(localTabs.value))
      : undefined,
    // Tab 模式下清除主 dataSource（后端以 tabs 为准）
    dataSource: tabModeEnabled.value ? undefined : (localDataSource.datasourceId ? JSON.parse(JSON.stringify(localDataSource)) : undefined),
    boundFilterIds: localBoundFilterIds.value.length > 0 ? [...localBoundFilterIds.value] : undefined,
    enableTimeFilter: localEnableTimeFilter.value || undefined,
    multiKpi: localComponent.type === 'kpi' ? (localMultiKpi.value || undefined) : undefined,
  }
  delete (updated as any).position
  emit('change', updated)
}

/** 添加静态选项 */
datasourceStore.fetchDatasources().catch(() => {
  // 静默失败，列表可能在其他页面已加载
})
</script>

<style scoped>
.property-panel {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--db-card);
  border-left: 1px solid var(--db-border);
  overflow: hidden;
}

.panel-header {
  padding: 10px 14px;
  font-size: 14px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--db-text-secondary);
  border-bottom: 1px solid var(--db-border);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.property-collapse-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  color: var(--db-text-muted);
  transition: color var(--transition-fast), background var(--transition-fast);
}

.property-collapse-btn:hover {
  color: var(--db-accent);
  background: color-mix(in srgb, var(--db-accent) 8%, transparent);
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.panel-body::-webkit-scrollbar {
  width: 5px;
}

.panel-body::-webkit-scrollbar-thumb {
  background: var(--db-border-strong, #d0d0d0);
  border-radius: 3px;
}

.panel-body::-webkit-scrollbar-thumb:hover {
  background: var(--db-text-quaternary, #bbb);
}

.panel-body::-webkit-scrollbar-track {
  background: transparent;
}

.panel-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--db-text-muted);
}

.empty-icon {
  font-size: 32px;
  opacity: 0.6;
}

.empty-text {
  font-size: 12px;
}

.form-group {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
}

.form-group-column {
  flex-direction: column;
  align-items: stretch;
  gap: 6px;
}

/* SQL 是多行编辑场景，始终独占一行，避免被属性面板的标签列压缩。 */
.form-group.jdbc-query-config {
  flex-direction: column;
  align-items: stretch;
  gap: 6px;
}

.form-group.jdbc-query-config > .form-label {
  width: auto;
  min-width: 0;
}

.form-group.jdbc-query-config :deep(.el-textarea),
.form-group.jdbc-query-config :deep(.el-textarea__inner) {
  width: 100%;
}

/* 行内模式：标签固定宽度，控件占满剩余空间 */
.form-group:not(.form-group-column) > .form-label {
  flex-shrink: 0;
  width: 64px;
  min-width: 64px;
}

.form-group:not(.form-group-column) > :not(.form-label) {
  min-width: 0;
}

/* 开关类表单项：标签与开关同行（左标签右开关），提示文字换行到下方整行 */
.form-group:has(> .el-switch) {
  flex-direction: row;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 4px 8px;
}

.form-group:has(> .el-switch) > .form-label {
  flex: 1 1 auto;
  width: auto;
  min-width: 0;
}

.form-group:has(> .el-switch) > .el-switch {
  margin-left: auto;
  flex-shrink: 0;
}

.form-group:has(> .el-switch) > .form-hint {
  flex-basis: 100%;
  margin-top: 0;
}

.form-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--db-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  line-height: 1;
}

/* el-input / el-select / el-switch 在属性面板中的统一微调 */
.panel-body :deep(.el-input__wrapper) {
  border-radius: 6px;
  box-shadow: 0 0 0 1px var(--db-border) inset;
  transition: box-shadow var(--transition-fast, 0.15s);
}

.panel-body :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--db-border-strong, #c0c4cc) inset;
}

.panel-body :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--db-accent) inset;
}

.panel-body :deep(.el-select) {
  width: 100%;
}

.panel-body :deep(.el-input-number) {
  width: 100%;
}

.panel-body :deep(.el-input-number .el-input__wrapper) {
  padding-left: 8px;
  padding-right: 8px;
}

.panel-body :deep(.el-switch) {
  height: 20px;
}

.preview-group {
  margin-top: 2px;
}

.preview-group :deep(.el-button) {
  width: 100%;
  border-radius: 6px;
}

.preview-result {
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--db-hover);
  border: 1px solid var(--db-border);
  font-size: 12px;
  line-height: 1.4;
}

.preview-error {
  color: var(--el-color-danger);
}

.preview-ok {
  color: var(--el-color-success);
}

.form-hint {
  font-size: 11px;
  color: var(--db-text-quaternary, #999);
  line-height: 1.4;
  margin-top: 1px;
}

.tab-list-editor {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.tab-item-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 8px;
  border-radius: 6px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: background var(--transition-fast, 0.15s), border-color var(--transition-fast, 0.15s);
}

.tab-item-row:hover {
  background: var(--db-hover);
}

.tab-item-row.active {
  border-color: var(--db-accent);
  background: color-mix(in srgb, var(--db-accent) 6%, transparent);
}

.tab-item-row :deep(.el-button) {
  padding: 2px 4px;
  font-size: 11px;
  color: var(--db-text-muted);
  min-width: 20px;
  height: 20px;
}

.tab-item-row :deep(.el-button:hover) {
  color: var(--el-color-danger);
}

.combination-tab-editor {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.combination-tab-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.combination-tab-row :deep(.el-input) {
  flex: 1;
}
.combination-tab-set {
  border: 1px solid var(--db-border);
  background: transparent;
  color: var(--db-text-muted);
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 6px;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
}
.combination-tab-set.on {
  border-color: var(--db-accent);
  color: var(--db-accent);
  background: color-mix(in srgb, var(--db-accent) 8%, transparent);
}
.combination-tab-del {
  border: none;
  background: transparent;
  color: var(--db-text-muted);
  cursor: pointer;
  font-size: 13px;
  padding: 2px 6px;
  border-radius: 4px;
  line-height: 1;
  flex-shrink: 0;
}
.combination-tab-del:hover {
  background: var(--db-danger-bg);
  color: var(--db-danger);
}
.combination-tab-add {
  align-self: flex-start;
  border: 1px dashed var(--db-border);
  background: transparent;
  color: var(--db-accent);
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 6px;
  cursor: pointer;
}
.combination-tab-add:hover {
  border-color: var(--db-accent);
  background: color-mix(in srgb, var(--db-accent) 8%, transparent);
}
/* 背景色：色块 + 可输入颜色参数，双向联动 */
.combination-color-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.combination-color-row :deep(.el-input) {
  flex: 1;
}

.tab-datasource-section {
  border: 1px solid var(--db-border);
  border-radius: 8px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: color-mix(in srgb, var(--db-bg) 50%, transparent);
}

.tab-datasource-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--db-accent);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: 2px;
}

.static-option-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.static-options-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
}

.static-option-row :deep(.el-button) {
  padding: 2px 4px;
  font-size: 11px;
  color: var(--db-text-muted);
  min-width: 20px;
  height: 20px;
}

.static-option-row :deep(.el-button:hover) {
  color: var(--el-color-danger);
}

/* radio-group 在属性面板中更紧凑 */
.panel-body :deep(.el-radio-group) {
  flex-wrap: wrap;
  gap: 0;
}

.panel-body :deep(.el-radio-button__inner) {
  padding: 5px 10px;
  font-size: 12px;
  border-radius: 0;
}

.panel-body :deep(.el-radio-button:first-child .el-radio-button__inner) {
  border-radius: 6px 0 0 6px;
}

.panel-body :deep(.el-radio-button:last-child .el-radio-button__inner) {
  border-radius: 0 6px 6px 0;
}

/* checkbox-group 在属性面板中更紧凑 */
.panel-body :deep(.el-checkbox-group) {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
}

.panel-body :deep(.el-checkbox) {
  margin-right: 0;
  height: 24px;
}

/* textarea 圆角统一 */
.panel-body :deep(.el-textarea__inner) {
  border-radius: 6px;
  font-size: 12px;
}

.binding-mode-hint { margin-bottom: 12px; padding: 8px 10px; border-radius: 6px; color: var(--theme-text-muted); background: var(--theme-surface-hover); font-size: 12px; line-height: 1.5; }
</style>
