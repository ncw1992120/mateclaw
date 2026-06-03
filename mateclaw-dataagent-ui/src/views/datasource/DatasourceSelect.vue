<template>
  <div class="datasource-select-page">
    <!-- 数据源配置表单页（编辑模式或选择MySQL后） -->
    <DatasourceForm v-if="showForm" :source-id="formSourceId" :edit-id="props.editId" @back="showForm = false" @cancel="handleFormCancel" @submit="handleFormSubmit" />

    <!-- 空白占位页（其他数据源） -->
    <div v-else-if="showBlank" class="blank-page">
      <div class="blank-content">
        <span class="blank-back-btn" @click="handleBlankBack">◀ {{ t('dsSelect.backToList') }}</span>
        <h2 class="blank-title">{{ blankSourceName }}</h2>
        <p class="blank-desc">{{ t('dsSelect.blankDesc') }}</p>
      </div>
    </div>

    <!-- 数据源选择列表页 -->
    <template v-else>
      <!-- 页面头部 -->
      <header class="page-header">
        <div class="header-left">
          <span class="back-btn" @click="handleBack">◀</span>
          <h1 class="page-title">{{ t('dsSelect.title') }}</h1>
        </div>
        <div class="header-right">
          <button class="icon-btn" :title="t('dsSelect.help')">❓</button>
          <button class="icon-btn" :title="t('dsSelect.doc')">📄</button>
          <button class="icon-btn" :title="t('dsSelect.feedback')">💬</button>
          <button class="icon-btn" :title="t('dsSelect.history')">🕐</button>
        </div>
      </header>

    <!-- 主内容区 -->
    <main class="page-main">
      <!-- 搜索区域 -->
      <div class="search-area">
        <div class="search-box" :class="{ focused: isSearchFocused }">
          <span class="search-icon">🔍</span>
          <input
            v-model="searchText"
            :placeholder="t('dsSelect.searchPlaceholder')"
            class="search-input"
            @focus="isSearchFocused = true"
            @blur="isSearchFocused = false"
            @input="onSearchInput"
          />
          <span v-if="searchText" class="search-clear" @click="clearSearch">✕</span>
        </div>
        <p v-if="searchText" class="search-hint">
          {{ t('dsSelect.searchResultHint', { keyword: searchText, count: totalFilteredCount }) }}
        </p>
      </div>

      <!-- 分类标签 -->
      <div class="category-tabs">
        <button
          v-for="cat in categories"
          :key="cat.key"
          class="category-tab"
          :class="{ active: activeCategory === cat.key }"
          @click="activeCategory = cat.key"
        >
          {{ t(cat.label) }}
        </button>
      </div>

      <!-- 搜索无结果 -->
      <div v-if="totalFilteredCount === 0" class="empty-search">
        <div class="empty-search-icon">🔍</div>
        <p class="empty-search-text">{{ t('dsSelect.searchEmpty') }}</p>
        <p class="empty-search-hint">{{ t('dsSelect.searchEmptyHint', { keyword: searchText }) }}</p>
      </div>

      <!-- 数据源卡片列表 -->
      <template v-else>
        <section v-for="group in filteredGroups" :key="group.title" class="source-group">
          <h3 class="group-title">
            <span class="group-icon">{{ group.icon }}</span>
            {{ t(group.title) }}
            <span class="group-count">{{ group.items.length }}</span>
          </h3>
          <div class="source-grid">
            <div
              v-for="item in group.items"
              :key="item.id"
              class="source-card"
              @click="handleSelect(item)"
            >
              <div class="card-top">
                <span class="card-icon" :style="{ color: item.iconColor }">{{ item.icon }}</span>
                <span v-if="item.badge" class="card-badge" :class="item.badgeType">{{ item.badge }}</span>
              </div>
              <p class="card-name" v-html="highlightName(item.name)"></p>
            </div>
          </div>
        </section>
      </template>
    </main>

    <!-- 右下角浮动按钮 -->
    <button class="float-help-btn" :title="t('dsSelect.helpCenter')">❓</button>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import DatasourceForm from './DatasourceForm.vue'

interface DatasourceItem {
  id: number
  name: string
  icon: string
  iconColor: string
  badge: string | null
  badgeType: string
  category: string
}

interface DatasourceGroup {
  title: string
  icon: string
  category: string
  items: DatasourceItem[]
}

const props = withDefaults(defineProps<{
  editId?: string
}>(), {
  editId: '',
})

const emit = defineEmits<{
  (e: 'back'): void
  (e: 'select', source: DatasourceItem): void
}>()

const { t } = useI18n()

/** 是否编辑模式 */
const isEditMode = computed(() => !!props.editId)

/** 编辑模式下直接进入表单 */
onMounted(() => {
  if (isEditMode.value) {
    formSourceId.value = 3
    showForm.value = true
  }
})

/** 搜索文本 */
const searchText = ref('')
/** 搜索框是否聚焦 */
const isSearchFocused = ref(false)
/** 当前选中的分类 */
const activeCategory = ref('all')
/** 是否显示配置表单 */
const showForm = ref(false)
/** 是否显示空白占位页 */
const showBlank = ref(false)
/** 表单对应的数据源ID */
const formSourceId = ref(3)
/** 空白占位对应的数据源名称 */
const blankSourceName = ref('')

/** 分类配置 */
const categories = [
  { key: 'all', label: 'dsSelect.catAll' },
  { key: 'cloud', label: 'dsSelect.catCloud' },
  { key: 'relational', label: 'dsSelect.catRelational' },
  { key: 'nosql', label: 'dsSelect.catNoSQL' },
  { key: 'warehouse', label: 'dsSelect.catWarehouse' },
  { key: 'middleware', label: 'dsSelect.catMiddleware' },
  { key: 'bigdata', label: 'dsSelect.catBigData' },
  { key: 'log', label: 'dsSelect.catLog' },
  { key: 'other', label: 'dsSelect.catOther' },
]

/** 所有数据源分组 */
const allSourceGroups: DatasourceGroup[] = [
  {
    title: 'dsSelect.groupRecommended',
    icon: '⭐',
    category: 'all',
    items: [
      { id: 1, name: 'RDS MySQL DuckDB', icon: '🔷', iconColor: '#165dff', badge: null, badgeType: '', category: 'cloud' },
      { id: 2, name: 'RDS MySQL 版', icon: '🐬', iconColor: '#165dff', badge: null, badgeType: '', category: 'cloud' },
      { id: 3, name: 'MySQL', icon: '🐬', iconColor: '#165dff', badge: 'NEW', badgeType: 'primary', category: 'relational' },
      { id: 4, name: 'MaxCompute', icon: '⚡', iconColor: '#165dff', badge: null, badgeType: '', category: 'warehouse' },
      { id: 5, name: 'AnalyticDB MySQL 版', icon: '🍊', iconColor: '#ff7d00', badge: null, badgeType: '', category: 'warehouse' },
      { id: 6, name: 'AnalyticDB PostgreSQL 版', icon: '🐘', iconColor: '#165dff', badge: null, badgeType: '', category: 'warehouse' },
      { id: 7, name: 'PolarDB MySQL 版', icon: '🐬', iconColor: '#165dff', badge: null, badgeType: '', category: 'cloud' },
      { id: 8, name: 'PolarDB PostgreSQL 版', icon: '🐘', iconColor: '#165dff', badge: null, badgeType: '', category: 'cloud' },
      { id: 9, name: 'PolarDB-X 分布式版', icon: '🔷', iconColor: '#165dff', badge: null, badgeType: '', category: 'cloud' },
    ],
  },
  {
    title: 'dsSelect.groupCloudNative',
    icon: '☁️',
    category: 'cloud',
    items: [
      { id: 10, name: 'HybridDB for MySQL (原HybridDB)', icon: '🗄️', iconColor: '#165dff', badge: null, badgeType: '', category: 'cloud' },
      { id: 11, name: 'OceanBase', icon: '🌊', iconColor: '#165dff', badge: null, badgeType: '', category: 'cloud' },
      { id: 12, name: 'StarRocks', icon: '⭐', iconColor: '#ff7d00', badge: 'HOT', badgeType: 'warning', category: 'cloud' },
      { id: 13, name: '云原生数仓 AnalyticDB', icon: '☁️', iconColor: '#165dff', badge: null, badgeType: '', category: 'warehouse' },
      { id: 14, name: 'MariaDB', icon: '🦁', iconColor: '#7b61ff', badge: null, badgeType: '', category: 'relational' },
      { id: 15, name: 'PostgreSQL', icon: '🐘', iconColor: '#165dff', badge: null, badgeType: '', category: 'relational' },
      { id: 16, name: 'Oracle', icon: '🔴', iconColor: '#f53f3f', badge: null, badgeType: '', category: 'relational' },
      { id: 17, name: 'SQL Server', icon: '🔵', iconColor: '#165dff', badge: null, badgeType: '', category: 'relational' },
      { id: 18, name: 'Db2', icon: '💠', iconColor: '#0066cc', badge: null, badgeType: '', category: 'relational' },
      { id: 19, name: 'DM (达梦)', icon: '🔶', iconColor: '#ff7d00', badge: null, badgeType: '', category: 'relational' },
      { id: 20, name: 'GaussDB', icon: '⚙️', iconColor: '#165dff', badge: null, badgeType: '', category: 'cloud' },
      { id: 21, name: '人大金仓 Kingbase', icon: '👑', iconColor: '#c7000b', badge: null, badgeType: '', category: 'relational' },
      { id: 22, name: 'SAP HANA', icon: '🌿', iconColor: '#008fd3', badge: null, badgeType: '', category: 'relational' },
      { id: 23, name: 'TiDB', icon: '🐚', iconColor: '#00b377', badge: null, badgeType: '', category: 'relational' },
      { id: 24, name: 'PolarDB-O', icon: '🐘', iconColor: '#165dff', badge: null, badgeType: '', category: 'cloud' },
      { id: 25, name: 'ClickHouse', icon: '🔔', iconColor: '#ffcc00', badge: null, badgeType: '', category: 'warehouse' },
      { id: 26, name: 'Doris', icon: '🦕', iconColor: '#165dff', badge: null, badgeType: '', category: 'warehouse' },
      { id: 27, name: 'Impala', icon: '🦩', iconColor: '#ff7d00', badge: null, badgeType: '', category: 'bigdata' },
      { id: 28, name: 'Kudu', icon: '🎯', iconColor: '#165dff', badge: null, badgeType: '', category: 'bigdata' },
      { id: 29, name: 'Druid', icon: '🧙', iconColor: '#165dff', badge: null, badgeType: '', category: 'bigdata' },
      { id: 30, name: 'SAP IQ (Sybase IQ)', icon: '📊', iconColor: '#ff7d00', badge: null, badgeType: '', category: 'relational' },
    ],
  },
  {
    title: 'dsSelect.groupNoSQL',
    icon: '🗄️',
    category: 'nosql',
    items: [
      { id: 31, name: 'MongoDB', icon: '🍃', iconColor: '#4db33d', badge: null, badgeType: '', category: 'nosql' },
      { id: 32, name: 'Redis', icon: '🔴', iconColor: '#dc382d', badge: null, badgeType: '', category: 'nosql' },
      { id: 33, name: 'Elasticsearch', icon: '🔍', iconColor: '#fed10a', badge: null, badgeType: '', category: 'nosql' },
      { id: 34, name: 'HBase', icon: '🏠', iconColor: '#165dff', badge: null, badgeType: '', category: 'nosql' },
      { id: 35, name: 'Cassandra', icon: '👁️', iconColor: '#1287b1', badge: null, badgeType: '', category: 'nosql' },
      { id: 36, name: 'Lindorm', icon: '🌲', iconColor: '#165dff', badge: null, badgeType: '', category: 'nosql' },
      { id: 37, name: 'TableStore', icon: '📋', iconColor: '#165dff', badge: null, badgeType: '', category: 'nosql' },
      { id: 38, name: 'InfluxDB', icon: '⚡', iconColor: '#00b5ad', badge: null, badgeType: '', category: 'nosql' },
      { id: 39, name: 'OpenTSDB', icon: '📈', iconColor: '#165dff', badge: null, badgeType: '', category: 'nosql' },
      { id: 40, name: 'Prometheus', icon: '🔥', iconColor: '#e6522e', badge: null, badgeType: '', category: 'nosql' },
      { id: 41, name: 'Graph Database', icon: '🕸️', iconColor: '#165dff', badge: null, badgeType: '', category: 'nosql' },
      { id: 42, name: 'Neo4j', icon: '💚', iconColor: '#018bff', badge: null, badgeType: '', category: 'nosql' },
      { id: 43, name: 'JanusGraph', icon: '📊', iconColor: '#165dff', badge: null, badgeType: '', category: 'nosql' },
      { id: 44, name: 'HugeGraph', icon: '🔷', iconColor: '#165dff', badge: null, badgeType: '', category: 'nosql' },
      { id: 45, name: 'NebulaGraph', icon: '🌌', iconColor: '#ea3636', badge: null, badgeType: '', category: 'nosql' },
    ],
  },
  {
    title: 'dsSelect.groupBigData',
    icon: '📦',
    category: 'bigdata',
    items: [
      { id: 46, name: 'Apache Hive', icon: '🐝', iconColor: '#fdee21', badge: null, badgeType: '', category: 'bigdata' },
      { id: 47, name: 'Spark SQL', icon: '🔥', iconColor: '#e25a1c', badge: null, badgeType: '', category: 'bigdata' },
      { id: 48, name: 'Presto / Trino', icon: '⚡', iconColor: '#165dff', badge: null, badgeType: '', category: 'bigdata' },
      { id: 49, name: 'Kylin', icon: '🔮', iconColor: '#165dff', badge: null, badgeType: '', category: 'bigdata' },
      { id: 50, name: 'Impala', icon: '🦩', iconColor: '#ff7d00', badge: null, badgeType: '', category: 'bigdata' },
      { id: 51, name: 'Flink SQL', icon: '🌊', iconColor: '#e6522e', badge: null, badgeType: '', category: 'bigdata' },
      { id: 52, name: 'Data Lake Analytics', icon: '🏞️', iconColor: '#165dff', badge: null, badgeType: '', category: 'bigdata' },
    ],
  },
  {
    title: 'dsSelect.groupFile',
    icon: '📄',
    category: 'other',
    items: [
      { id: 53, name: 'Excel(.xlsx)', icon: '📗', iconColor: '#217346', badge: null, badgeType: '', category: 'other' },
      { id: 54, name: 'CSV', icon: '📄', iconColor: '#165dff', badge: null, badgeType: '', category: 'other' },
    ],
  },
  {
    title: 'dsSelect.groupLocal',
    icon: '💻',
    category: 'other',
    items: [
      { id: 55, name: 'FTP/SFTP', icon: '📡', iconColor: '#165dff', badge: null, badgeType: '', category: 'other' },
    ],
  },
  {
    title: 'dsSelect.groupAPI',
    icon: '🔗',
    category: 'middleware',
    items: [
      { id: 56, name: 'API 数据源', icon: '🌐', iconColor: '#165dff', badge: null, badgeType: '', category: 'middleware' },
    ],
  },
  {
    title: 'dsSelect.groupRealtime',
    icon: '⚡',
    category: 'bigdata',
    items: [
      { id: 57, name: '实时计算 Flink', icon: '🌊', iconColor: '#e6522e', badge: null, badgeType: '', category: 'bigdata' },
    ],
  },
  {
    title: 'dsSelect.groupIM',
    icon: '💬',
    category: 'other',
    items: [
      { id: 58, name: '钉钉 / 企业微信', icon: '💬', iconColor: '#165dff', badge: null, badgeType: '', category: 'other' },
    ],
  },
  {
    title: 'dsSelect.groupCustom',
    icon: '⚙️',
    category: 'other',
    items: [
      { id: 59, name: '自定义数据源', icon: '🛠️', iconColor: '#86909c', badge: null, badgeType: '', category: 'other' },
    ],
  },
]

/** 获取搜索关键词（小写） */
const keyword = computed(() => searchText.value.toLowerCase().trim())

/** 是否处于搜索模式 */
const isSearching = computed(() => keyword.value.length > 0)

/** 根据分类和搜索关键词过滤的分组 */
const filteredGroups = computed(() => {
  return allSourceGroups
    .map((group) => {
      const filteredItems = group.items.filter((item) => {
        const matchCategory = activeCategory.value === 'all' || item.category === activeCategory.value
        const matchKeyword = !isSearching.value || item.name.toLowerCase().includes(keyword.value)
        return matchCategory && matchKeyword
      })
      return { ...group, items: filteredItems }
    })
    .filter((group) => group.items.length > 0)
})

/** 过滤后的总数量 */
const totalFilteredCount = computed(() => {
  return filteredGroups.value.reduce((sum, group) => sum + group.items.length, 0)
})

/** 搜索输入事件 */
function onSearchInput(): void {
  if (isSearching.value && activeCategory.value !== 'all') {
    activeCategory.value = 'all'
  }
}

/** 清除搜索 */
function clearSearch(): void {
  searchText.value = ''
}

/** 高亮搜索匹配文本 */
function highlightName(name: string): string {
  if (!isSearching.value) {
    return escapeHtml(name)
  }
  const lowerName = name.toLowerCase()
  const lowerKeyword = keyword.value
  const index = lowerName.indexOf(lowerKeyword)
  if (index === -1) {
    return escapeHtml(name)
  }
  const before = name.substring(0, index)
  const match = name.substring(index, index + lowerKeyword.length)
  const after = name.substring(index + lowerKeyword.length)
  return escapeHtml(before) + '<mark class="search-highlight">' + escapeHtml(match) + '</mark>' + escapeHtml(after)
}

/** HTML转义 */
function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/** 返回 */
function handleBack(): void {
  if (showForm.value) {
    showForm.value = false
  } else if (showBlank.value) {
    showBlank.value = false
  } else {
    emit('back')
  }
}

/** 选择数据源 - 仅MySQL Server展示表单，其他展示空白 */
function handleSelect(source: DatasourceItem): void {
  if (source.id === 3) {
    formSourceId.value = source.id
    showForm.value = true
    showBlank.value = false
  } else {
    blankSourceName.value = source.name
    showBlank.value = true
    showForm.value = false
  }
}

/** 表单取消 */
function handleFormCancel(): void {
  showForm.value = false
}

/** 表单提交成功 - 返回列表并通知父组件刷新 */
function handleFormSubmit(): void {
  showForm.value = false
  emit('back')
}

/** 空白页返回 */
function handleBlankBack(): void {
  showBlank.value = false
}
</script>

<style scoped>
.datasource-select-page {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  background: #f7f8fa;
}

/* 页面头部 */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  background: #fff;
  border-bottom: 1px solid #e5e6eb;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-btn {
  width: 28px;
  height: 28px;
  border-radius: 4px;
  border: none;
  background: #f2f3f5;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #4e5969;
  transition: all 0.2s;
}

.back-btn:hover {
  background: #e8f3ff;
  color: #165dff;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #1d2129;
  margin: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.icon-btn {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  border: none;
  background: transparent;
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.icon-btn:hover {
  background: #f2f3f5;
}

/* 主内容区 */
.page-main {
  flex: 1;
  overflow-y: auto;
  padding: 24px 32px;
}

/* 搜索区域 */
.search-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 28px;
}

.search-box {
  width: 480px;
  height: 44px;
  border-radius: 22px;
  border: 1px solid #e5e6eb;
  background: #fff;
  display: flex;
  align-items: center;
  padding: 0 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: all 0.25s;
}

.search-box.focused {
  border-color: #165dff;
  box-shadow: 0 2px 12px rgba(22, 93, 255, 0.12);
}

.search-icon {
  font-size: 16px;
  margin-right: 10px;
  color: #c9cdd4;
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 14px;
  color: #1d2129;
  background: transparent;
  font-family: inherit;
}

.search-input::placeholder {
  color: #c9cdd4;
}

.search-clear {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #c9cdd4;
  color: #fff;
  font-size: 10px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-left: 8px;
  transition: background 0.2s;
  line-height: 1;
}

.search-clear:hover {
  background: #86909c;
}

.search-hint {
  font-size: 12px;
  color: #86909c;
  margin: 8px 0 0 0;
}

/* 分类标签 */
.category-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 32px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e5e6eb;
}

.category-tab {
  padding: 5px 14px;
  border-radius: 14px;
  border: none;
  background: transparent;
  font-size: 13px;
  color: #4e5969;
  cursor: pointer;
  font-family: inherit;
  white-space: nowrap;
  transition: all 0.2s;
}

.category-tab:hover {
  color: #165dff;
  background: rgba(22, 93, 255, 0.06);
}

.category-tab.active {
  background: #165dff;
  color: #fff;
  font-weight: 500;
}

/* 搜索空状态 */
.empty-search {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
}

.empty-search-icon {
  font-size: 56px;
  opacity: 0.35;
  margin-bottom: 16px;
}

.empty-search-text {
  font-size: 15px;
  font-weight: 500;
  color: #4e5969;
  margin: 0 0 6px 0;
}

.empty-search-hint {
  font-size: 13px;
  color: #c9cdd4;
  margin: 0;
}

/* 分组 */
.source-group {
  margin-bottom: 28px;
}

.group-title {
  font-size: 14px;
  font-weight: 600;
  color: #1d2129;
  margin: 0 0 14px 0;
  display: flex;
  align-items: center;
  gap: 6px;
}

.group-icon {
  font-size: 15px;
}

.group-count {
  font-size: 11px;
  font-weight: 400;
  color: #c9cdd4;
  background: #f2f3f5;
  padding: 1px 6px;
  border-radius: 8px;
  line-height: 1.5;
}

.source-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
}

.source-card {
  background: #fff;
  border-radius: 6px;
  padding: 16px 14px;
  cursor: pointer;
  transition: all 0.25s;
  border: 1px solid #e5e6eb;
  position: relative;
}

.source-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(22, 93, 255, 0.1);
  border-color: #165dff;
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.card-icon {
  font-size: 26px;
}

.card-badge {
  font-size: 9px;
  font-weight: 700;
  padding: 1px 5px;
  border-radius: 3px;
  line-height: 1.5;
  letter-spacing: 0.3px;
}

.card-badge.primary {
  background: #e8f3ff;
  color: #165dff;
}

.card-badge.warning {
  background: #fff7e8;
  color: #ff7d00;
}

.card-name {
  font-size: 12px;
  font-weight: 500;
  color: #1d2129;
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-name :deep(.search-highlight) {
  background: #fff3e0;
  color: #ff7d00;
  padding: 0 1px;
  border-radius: 2px;
  font-weight: 600;
}

/* 浮动帮助按钮 */
.float-help-btn {
  position: fixed;
  right: 28px;
  bottom: 28px;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: none;
  background: #165dff;
  color: #fff;
  font-size: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(22, 93, 255, 0.35);
  transition: all 0.25s;
  z-index: 99;
}

.float-help-btn:hover {
  transform: scale(1.08);
  background: #0e42d2;
}

/* 空白占位页 */
.blank-page {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f7f8fa;
}

.blank-content {
  text-align: center;
}

.blank-back-btn {
  font-size: 13px;
  color: #165dff;
  cursor: pointer;
  margin-bottom: 24px;
  display: inline-block;
  transition: opacity 0.2s;
}

.blank-back-btn:hover {
  opacity: 0.7;
}

.blank-title {
  font-size: 18px;
  font-weight: 600;
  color: #1d2129;
  margin: 0 0 12px 0;
}

.blank-desc {
  font-size: 14px;
  color: #86909c;
  margin: 0;
}
</style>
