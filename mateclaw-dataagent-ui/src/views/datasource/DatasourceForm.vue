<template>
  <div class="datasource-form-page">
    <!-- 页面头部 -->
    <header class="form-header">
      <h1 class="form-title">{{ isEditMode ? t('dsForm.editTitle') : t('dsForm.title') }}</h1>
      <!-- 步骤条 -->
      <div class="step-bar">
        <div class="step-item done">
          <span class="step-num">✓</span>
          <span class="step-label">{{ t('dsForm.stepSelectSource') }}</span>
        </div>
        <div class="step-line"></div>
        <div class="step-item active">
          <span class="step-num">2</span>
          <span class="step-label">{{ t('dsForm.stepConfigConn') }}</span>
        </div>
        <div class="step-line"></div>
        <div class="step-item">
          <span class="step-num">3</span>
          <span class="step-label">{{ t('dsForm.stepFinish') }}</span>
        </div>
      </div>
      <span class="close-btn" @click="handleClose">✕</span>
    </header>

    <!-- 主体区域 -->
    <div class="form-body">
      <!-- 左侧数据库类型树 -->
      <aside class="db-tree-panel">
        <div class="tree-search">
          <span class="search-icon">🔍</span>
        </div>
        <div class="tree-content">
          <div v-for="group in dbTypeTree" :key="group.label" class="tree-group">
            <div class="tree-group-label" @click="toggleGroup(group.key)">
              <span class="expand-icon">{{ expandedGroups[group.key] ? '▼' : '▶' }}</span>
              {{ t(group.label) }}
            </div>
            <div v-if="expandedGroups[group.key]" class="tree-items">
              <div
                v-for="item in group.items"
                :key="item.id"
                class="tree-item"
                :class="{ active: item.id === selectedDbId }"
                @click="selectDbType(item)"
              >
                <span class="item-icon">{{ item.icon }}</span>
                <span class="item-name">{{ item.name }}</span>
              </div>
            </div>
          </div>
        </div>
      </aside>

      <!-- 中间表单区 -->
      <main class="form-main">
        <div v-if="formLoading" class="form-loading">
          <span>{{ t('datasourcePage.loading') }}</span>
        </div>
        <template v-else>
        <div class="form-card">
          <h2 class="card-title">
            {{ t('dsForm.selfHostedDB') }} - {{ datasourceInfo.name }}
            <span class="help-link" @click="toggleHelpPanel">
              {{ showHelpPanel ? t('dsForm.hideDoc') : t('dsForm.showDoc') }}
              <span class="link-arrow">›</span>
            </span>
          </h2>

          <p class="version-hint">💡 {{ t('dsForm.versionHint', { versions: '5.5、5.6、5.7、8.0' }) }}</p>

          <div class="form-fields">
            <!-- 显示名称 -->
            <div class="field-row">
              <label class="field-label required">{{ t('dsForm.fieldDisplayName') }}</label>
              <input v-model="form.displayName" class="field-input" :placeholder="t('dsForm.placeholderDisplayName')" />
            </div>

            <!-- 数据库地址 -->
            <div class="field-row">
              <label class="field-label required">{{ t('dsForm.fieldHost') }}</label>
              <input v-model="form.host" class="field-input" :placeholder="t('dsForm.placeholderHost')" />
            </div>

            <!-- 端口 -->
            <div class="field-row">
              <label class="field-label required">{{ t('dsForm.fieldPort') }}</label>
              <input v-model="form.port" class="field-input short" />
            </div>

            <!-- 数据库 -->
            <div class="field-row">
              <label class="field-label required">{{ t('dsForm.fieldDatabase') }}</label>
              <input v-model="form.database" class="field-input" :placeholder="t('dsForm.placeholderDatabase')" />
            </div>

            <!-- 用户名 -->
            <div class="field-row">
              <label class="field-label required">{{ t('dsForm.fieldUsername') }}</label>
              <input v-model="form.username" class="field-input" :placeholder="t('dsForm.placeholderUsername')" />
            </div>

            <!-- 密码 -->
            <div class="field-row">
              <label class="field-label required">{{ t('dsForm.fieldPassword') }}</label>
              <div class="password-field">
                <input v-model="form.password" class="field-input" :type="showPassword ? 'text' : 'password'" :placeholder="t('dsForm.placeholderPassword')" />
                <span class="eye-btn" @click="showPassword = !showPassword">
                  <svg v-if="showPassword" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                  <svg v-else xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
                </span>
              </div>
            </div>

            <!-- 服务器/版本 -->
            <div class="field-row">
              <label class="field-label">{{ t('dsForm.fieldVersion') }}</label>
              <select v-model="form.version" class="field-select">
                <option v-for="v in versionOptions" :key="v.value" :value="v.value">{{ v.label }}</option>
              </select>
            </div>

            <!-- VPC/线路配置 -->
            <div class="field-section">
              <label class="section-label">{{ t('dsForm.vpcConfig') }}</label>
              <p class="section-desc">{{ t('dsForm.vpcDesc') }}</p>
            </div>

            <!-- SSL -->
            <div class="field-toggle">
              <label class="field-label">{{ t('dsForm.ssl') }}</label>
              <label class="switch">
                <input v-model="form.sslEnabled" type="checkbox" />
                <span class="slider"></span>
              </label>
            </div>

            <!-- SSH -->
            <div class="field-toggle">
              <label class="field-label">{{ t('dsForm.ssh') }}</label>
              <label class="switch">
                <input v-model="form.sshEnabled" type="checkbox" />
                <span class="slider"></span>
              </label>
              <p v-if="form.sshEnabled" class="toggle-desc">{{ t('dsForm.sshDesc') }}</p>
            </div>

            <!-- 跨VPC/SQL -->
            <div class="field-toggle">
              <label class="field-label">{{ t('dsForm.crossVpcSql') }}</label>
              <label class="switch">
                <input v-model="form.crossVpcEnabled" type="checkbox" />
                <span class="slider"></span>
              </label>
              <p v-if="form.crossVpcEnabled" class="toggle-desc">{{ t('dsForm.crossVpcDesc') }}</p>
            </div>

            <!-- 开启上传文件入口 -->
            <div class="field-checkbox">
              <label class="checkbox-wrap">
                <input v-model="form.uploadEnabled" type="checkbox" checked />
                <span class="checkmark"></span>
                {{ t('dsForm.uploadFileEntry') }}
              </label>
            </div>

            <!-- 白名单列表 -->
            <div class="whitelist-section">
              <label class="field-label">{{ t('dsForm.whitelistLabel') }}</label>
              <div class="whitelist-box">
                <pre class="whitelist-text">{{ whitelistIps }}</pre>
                <button class="copy-btn" @click="copyWhitelist">{{ t('dsForm.copyWhitelist') }}</button>
              </div>
            </div>
          </div>
        </div>

        <!-- 底部按钮 -->
        <div class="form-actions">
          <button class="btn-cancel" @click="handleCancel">{{ t('dsForm.cancel') }}</button>
          <button class="btn-test" :disabled="testing || submitting" @click="handleTestConnection">
            {{ testing ? t('dsForm.testing') : t('dsForm.testConnection') }}
          </button>
          <button class="btn-submit" :disabled="testing || submitting" @click="handleSubmit">
            {{ submitting ? t('dsForm.testing') : t('dsForm.submit') }}
          </button>
        </div>
        </template>
      </main>

      <!-- 右侧帮助面板 -->
      <aside v-if="showHelpPanel" class="help-panel">
        <p class="help-text">{{ t('dsForm.helpText') }}</p>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import * as datasourceApi from '@/api/datasource'
import { useDatasourceStore } from '@/stores/useDatasourceStore'

interface DbTypeInfo {
  id: number
  name: string
  icon: string
}

const props = withDefaults(defineProps<{
  sourceId?: number
  editId?: string
}>(), {
  sourceId: 3,
  editId: '',
})

const emit = defineEmits<{
  (e: 'back'): void
  (e: 'cancel'): void
  (e: 'submit', data: any): void
}>()

const { t } = useI18n()
const store = useDatasourceStore()

/** 是否编辑模式 */
const isEditMode = computed(() => !!props.editId)

/** 是否显示右侧帮助面板 */
const showHelpPanel = ref(false)
/** 是否显示密码 */
const showPassword = ref(false)
/** 选中的数据库类型ID */
const selectedDbId = ref(props.sourceId)
/** 是否正在提交 */
const submitting = ref(false)
/** 是否正在测试连接 */
const testing = ref(false)
/** 已创建的数据源ID（用于测试连接） */
const createdDsId = ref<string | null>(null)
/** 表单加载中 */
const formLoading = ref(false)

/** 编辑模式下加载已有数据源 */
onMounted(async () => {
  if (isEditMode.value) {
    formLoading.value = true
    try {
      const ds = await datasourceApi.get(props.editId)
      if (ds) {
        form.displayName = ds.name || ''
        form.host = ds.host || ''
        form.port = String(ds.port || 3306)
        form.database = ds.databaseName || ''
        form.username = ds.username || ''
        form.password = ds.password || ''
        createdDsId.value = ds.id
        const sourceTypeMap: Record<string, number> = {
          mysql: 3,
          postgresql: 15,
          sqlserver: 17,
        }
        selectedDbId.value = sourceTypeMap[ds.sourceType?.toLowerCase()] || 3
      }
    } catch {
      ElMessage.error(t('dsForm.loadFail'))
    } finally {
      formLoading.value = false
    }
  }
})

/** 展开的分组 */
const expandedGroups = reactive({
  localFiles: false,
  datasets: true,
  aliyunDb: true,
  selfHosted: true,
  bigData: false,
  other: false,
})

/** 当前选中的数据源信息 */
const datasourceInfo = computed(() => {
  const allSources: Record<number, { name: string; icon: string }> = {
    3: { name: 'MySQL', icon: '🐬' },
    15: { name: 'PostgreSQL', icon: '🐘' },
    17: { name: 'SQL Server', icon: '🔵' },
  }
  return allSources[props.sourceId] || { name: 'MySQL', icon: '🐬' }
})

/** 表单数据 */
const form = reactive({
  displayName: '',
  host: '',
  port: '3306',
  database: '',
  username: '',
  password: '',
  version: '8.0',
  sslEnabled: false,
  sshEnabled: false,
  crossVpcEnabled: false,
  uploadEnabled: true,
})

/** 版本选项 */
const versionOptions = [
  { value: '8.0', label: '8.0' },
  { value: '5.7', label: '5.7' },
  { value: '5.6', label: '5.6' },
  { value: '5.5', label: '5.5' },
]

/** 白名单IP */
const whitelistIps = `47.101.100.24/0,191.0.0.0/47,10.137.30/0,192.92.0/0,234.204.106.15.160/0,24.106.15.160/0,23.106/15`

/** 数据库类型树 */
const dbTypeTree = [
  {
    key: 'localFiles',
    label: 'dsForm.treeLocalFiles',
    items: [] as DbTypeInfo[],
  },
  {
    key: 'datasets',
    label: 'dsForm.treeDatasets',
    items: [] as DbTypeInfo[],
  },
  {
    key: 'aliyunDb',
    label: 'dsForm.treeAliyunDb',
    items: [
      { id: 1, name: 'RDS MySQL DuckDB', icon: '🔷' },
      { id: 7, name: 'PolarDB MySQL 版', icon: '🐬' },
      { id: 8, name: 'PolarDB PostgreSQL 版', icon: '🐘' },
      { id: 9, name: 'PolarDB-X 分布式版', icon: '🔷' },
      { id: 13, name: '云原生数仓 AnalyticDB', icon: '☁️' },
    ],
  },
  {
    key: 'selfHosted',
    label: 'dsForm.treeSelfHosted',
    items: [
      { id: 3, name: 'MySQL', icon: '🐬' },
      { id: 15, name: 'PostgreSQL', icon: '🐘' },
      { id: 17, name: 'SQL Server', icon: '🔵' },
      { id: 16, name: 'Oracle', icon: '🔴' },
      { id: 25, name: 'ClickHouse', icon: '🔔' },
      { id: 12, name: 'StarRocks', icon: '⭐' },
      { id: 26, name: 'Doris', icon: '🦕' },
      { id: 29, name: 'Presto', icon: '⚡' },
      { id: 47, name: 'Apache Doris', icon: '📦' },
      { id: 50, name: 'Impala', icon: '🦩' },
      { id: 22, name: 'Oracle', icon: '🔴' },
      { id: 46, name: 'Hive', icon: '🐝' },
      { id: 51, name: 'Spark SQL', icon: '🔥' },
      { id: 31, name: 'MongoDB', icon: '🍃' },
      { id: 14, name: 'MariaDB', icon: '🦁' },
      { id: 33, name: 'Elasticsearch', icon: '🔍' },
      { id: 32, name: 'SAP HANA', icon: '🌿' },
      { id: 28, name: 'SAP IQ (Sybase IQ)', icon: '📊' },
      { id: 34, name: 'HBase', icon: '🏠' },
      { id: 35, name: 'Hbase', icon: '🏠' },
      { id: 58, name: 'SelectDB', icon: '⚙️' },
    ],
  },
]

/** 切换分组展开状态 */
function toggleGroup(key: string): void {
  expandedGroups[key] = !expandedGroups[key]
}

/** 选择数据库类型 */
function selectDbType(item: DbTypeInfo): void {
  selectedDbId.value = item.id
}

/** 切换帮助面板 */
function toggleHelpPanel(): void {
  showHelpPanel.value = !showHelpPanel.value
}

/** 复制白名单 */
function copyWhitelist(): void {
  navigator.clipboard.writeText(whitelistIps)
}

/** 关闭 */
function handleClose(): void {
  emit('cancel')
}

/** 取消 */
function handleCancel(): void {
  emit('back')
}

/** 构建请求参数（兼容创建和更新） */
function buildCreateRequest() {
  const sourceTypeMap: Record<number, string> = {
    3: 'mysql',
    15: 'postgresql',
    17: 'sqlserver',
  }
  return {
    name: form.displayName,
    sourceType: sourceTypeMap[selectedDbId.value] || 'mysql',
    host: form.host,
    port: Number(form.port) || 3306,
    databaseName: form.database,
    username: form.username,
    password: form.password,
    enabled: true,
  }
}

/** 测试连接 */
async function handleTestConnection(): Promise<void> {
  if (testing.value || submitting.value) {
    return
  }
  testing.value = true
  try {
    if (!createdDsId.value) {
      const request = buildCreateRequest()
      const ds = await datasourceApi.create(request)
      createdDsId.value = ds.id
    }
    const result = await datasourceApi.testConnection(createdDsId.value)
    if (result) {
      ElMessage.success(t('datasourcePage.testSuccess'))
    } else {
      ElMessage.error(t('datasourcePage.testFail'))
    }
    store.fetchDatasources()
  } catch {
    ElMessage.error(t('datasourcePage.testFail'))
  } finally {
    testing.value = false
  }
}

/** 提交保存数据源 */
async function handleSubmit(): Promise<void> {
  if (submitting.value || testing.value) {
    return
  }
  submitting.value = true
  try {
    const request = buildCreateRequest()
    if (createdDsId.value) {
      await datasourceApi.update(createdDsId.value, request)
    } else {
      const ds = await datasourceApi.create(request)
      createdDsId.value = ds.id
    }
    ElMessage.success(isEditMode.value ? t('dsForm.updateSuccess') : t('dsForm.submitSuccess'))
    emit('submit', { ...form })
  } catch {
    ElMessage.error(isEditMode.value ? t('dsForm.updateFail') : t('dsForm.submitFail'))
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.datasource-form-page {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  background: #f7f8fa;
}

/* 页面头部 */
.form-header {
  display: flex;
  align-items: center;
  padding: 12px 24px;
  background: #fff;
  border-bottom: 1px solid #e5e6eb;
  flex-shrink: 0;
  gap: 32px;
  position: relative;
}

.form-title {
  font-size: 16px;
  font-weight: 600;
  color: #1d2129;
  margin: 0;
  white-space: nowrap;
}

/* 步骤条 */
.step-bar {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
}

.step-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #86909c;
}

.step-item.done {
  color: #00b42a;
}

.step-item.active {
  color: #165dff;
  font-weight: 500;
}

.step-num {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  background: #f2f3f5;
  color: #86909c;
}

.step-item.done .step-num {
  background: #e8ffea;
  color: #00b42a;
}

.step-item.active .step-num {
  background: #165dff;
  color: #fff;
}

.step-line {
  width: 40px;
  height: 1px;
  background: #e5e6eb;
}

.step-label {
  white-space: nowrap;
}

.close-btn {
  position: absolute;
  right: 16px;
  top: 50%;
  transform: translateY(-50%);
  width: 28px;
  height: 28px;
  border-radius: 4px;
  border: none;
  background: transparent;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c9cdd4;
  transition: all 0.2s;
}

.close-btn:hover {
  background: #f2f3f5;
  color: #4e5969;
}

/* 主体区域 */
.form-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* 左侧数据库类型树 */
.db-tree-panel {
  width: 200px;
  background: #fff;
  border-right: 1px solid #e5e6eb;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.tree-search {
  padding: 10px 12px;
  border-bottom: 1px solid #f2f3f5;
}

.search-icon {
  font-size: 14px;
  color: #c9cdd4;
}

.tree-content {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}

.tree-group {
  margin: 0;
}

.tree-group-label {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 500;
  color: #4e5969;
  cursor: pointer;
  user-select: none;
}

.tree-group-label:hover {
  background: #f2f3f5;
}

.expand-icon {
  font-size: 8px;
  color: #86909c;
  transition: transform 0.2s;
}

.tree-items {
  padding-left: 8px;
}

.tree-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px 5px 16px;
  font-size: 12.5px;
  color: #4e5969;
  cursor: pointer;
  border-radius: 0;
  transition: all 0.15s;
}

.tree-item:hover {
  background: rgba(22, 93, 255, 0.06);
  color: #165dff;
}

.tree-item.active {
  background: #e8f3ff;
  color: #165dff;
  font-weight: 500;
}

.item-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.item-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 中间表单区 */
.form-main {
  flex: 1;
  overflow-y: auto;
  padding: 24px 32px;
  display: flex;
  flex-direction: column;
}

.form-loading {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 80px 0;
  color: #86909c;
  font-size: 14px;
}

.form-card {
  background: #fff;
  border-radius: 8px;
  padding: 24px 28px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #1d2129;
  margin: 0 0 4px 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.help-link {
  font-size: 12px;
  font-weight: 400;
  color: #165dff;
  cursor: pointer;
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 2px;
}

.help-link:hover {
  text-decoration: underline;
}

.link-arrow {
  font-size: 14px;
  transition: transform 0.2s;
}

.help-link:hover .link-arrow {
  transform: translateX(2px);
}

.version-hint {
  font-size: 12px;
  color: #86909c;
  margin: 0 0 20px 0;
}

/* 表单字段 */
.form-fields {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.field-label {
  width: 90px;
  font-size: 13px;
  color: #4e5969;
  flex-shrink: 0;
  text-align: right;
}

.field-label.required::before {
  content: '*';
  color: #f53f3f;
  margin-right: 4px;
}

.field-input {
  flex: 1;
  max-width: 360px;
  height: 34px;
  border: 1px solid #e5e6eb;
  border-radius: 4px;
  padding: 0 12px;
  font-size: 13px;
  color: #1d2129;
  outline: none;
  transition: border-color 0.2s;
  font-family: inherit;
  background: #fff;
}

.field-input:focus {
  border-color: #165dff;
  box-shadow: 0 0 0 2px rgba(22, 93, 255, 0.1);
}

.field-input.short {
  max-width: 120px;
}

.field-input::placeholder {
  color: #c9cdd4;
}

.password-field,
.schema-field {
  position: relative;
  flex: 1;
  max-width: 360px;
  display: flex;
  align-items: center;
}

.password-field .field-input,
.schema-field .field-input {
  flex: 1;
  max-width: none;
  padding-right: 36px;
}

.password-field .field-input::-ms-reveal,
.password-field .field-input::-webkit-credentials-auto-fill-button {
  display: none;
}

.eye-btn {
  position: absolute;
  right: 10px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c9cdd4;
  transition: color 0.2s;
}

.eye-btn:hover {
  color: #4e5969;
}

.eye-btn.small {
  font-size: 12px;
}

.field-select {
  width: 360px;
  height: 34px;
  border: 1px solid #e5e6eb;
  border-radius: 4px;
  padding: 0 12px;
  font-size: 13px;
  color: #1d2129;
  outline: none;
  background: #fff;
  cursor: pointer;
  font-family: inherit;
}

.field-select:focus {
  border-color: #165dff;
  box-shadow: 0 0 0 2px rgba(22, 93, 255, 0.1);
}

/* 分段标题 */
.field-section {
  padding-top: 8px;
  border-top: 1px solid #f2f3f5;
}

.section-label {
  font-size: 13px;
  font-weight: 500;
  color: #1d2129;
  display: block;
  margin-bottom: 4px;
}

.section-desc {
  font-size: 12px;
  color: #86909c;
  margin: 0;
  line-height: 1.5;
}

/* 开关切换 */
.field-toggle {
  display: flex;
  align-items: center;
  gap: 12px;
}

.field-toggle .field-label {
  width: auto;
  text-align: left;
}

.switch {
  position: relative;
  display: inline-block;
  width: 36px;
  height: 20px;
  flex-shrink: 0;
}

.switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.slider {
  position: absolute;
  cursor: pointer;
  inset: 0;
  background: #c9cdd4;
  border-radius: 10px;
  transition: background 0.25s;
}

.slider::before {
  content: '';
  position: absolute;
  height: 16px;
  width: 16px;
  left: 2px;
  bottom: 2px;
  background: #fff;
  border-radius: 50%;
  transition: transform 0.25s;
}

.switch input:checked + .slider {
  background: #165dff;
}

.switch input:checked + .slider::before {
  transform: translateX(16px);
}

.toggle-desc {
  font-size: 11.5px;
  color: #86909c;
  margin: 2px 0 0 48px;
  line-height: 1.4;
}

/* 复选框 */
.field-checkbox {
  padding: 4px 0;
}

.checkbox-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #4e5969;
  cursor: pointer;
  user-select: none;
}

.checkbox-wrap input[type='checkbox'] {
  display: none;
}

.checkmark {
  width: 16px;
  height: 16px;
  border: 1.5px solid #c9cdd4;
  border-radius: 3px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  flex-shrink: 0;
  position: relative;
}

.checkbox-wrap input:checked + .checkmark {
  background: #165dff;
  border-color: #165dff;
}

.checkbox-wrap input:checked + .checkmark::after {
  content: '✓';
  color: #fff;
  font-size: 11px;
  font-weight: bold;
  position: absolute;
}

/* 白名单 */
.whitelist-section {
  margin-top: 8px;
}

.whitelist-box {
  position: relative;
  background: #fafbfc;
  border: 1px solid #e5e6eb;
  border-radius: 4px;
  padding: 12px 80px 12px 16px;
  min-height: 60px;
}

.whitelist-text {
  font-family: 'Courier New', monospace;
  font-size: 11.5px;
  color: #4e5969;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.6;
}

.copy-btn {
  position: absolute;
  bottom: 12px;
  right: 12px;
  padding: 4px 12px;
  border: 1px solid #c9cdd4;
  border-radius: 4px;
  background: #fff;
  font-size: 12px;
  color: #4e5969;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}

.copy-btn:hover {
  border-color: #165dff;
  color: #165dff;
}

/* 底部按钮 */
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #f2f3f5;
}

.btn-cancel {
  height: 34px;
  padding: 0 20px;
  border-radius: 4px;
  border: 1px solid #e5e6eb;
  background: #fff;
  color: #4e5969;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}

.btn-cancel:hover {
  border-color: #c9cdd4;
  color: #1d2129;
}

.btn-test {
  height: 34px;
  padding: 0 20px;
  border-radius: 4px;
  border: none;
  background: #165dff;
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: all 0.2s;
  font-family: inherit;
}

.btn-test:hover {
  background: #0e42d2;
}

.btn-test:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  background: #86909c;
}

.btn-submit {
  height: 34px;
  padding: 0 24px;
  border-radius: 4px;
  border: none;
  background: #165dff;
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}

.btn-submit:hover:not(:disabled) {
  background: #0e42d2;
}

.btn-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  background: #86909c;
}

.btn-icon {
  font-size: 14px;
}

/* 右侧帮助面板 */
.help-panel {
  width: 240px;
  background: #fff;
  border-left: 1px solid #e5e6eb;
  padding: 20px 16px;
  flex-shrink: 0;
  overflow-y: auto;
}

.help-text {
  font-size: 12.5px;
  color: #86909c;
  line-height: 1.7;
  margin: 0;
}
</style>
