<template>
  <div class="metric-platform-panel">
    <!-- 指标平台连接 -->
    <section class="mp-section">
      <div class="section-header">
        <div class="section-header-row">
          <h2 class="section-title">
            <span class="title-icon">📡</span>
            {{ t('metricPlatform.sectionTitle') }}
          </h2>
          <div class="section-actions">
            <template v-if="!isEditing">
              <button
                class="section-action-btn edit"
                :title="t('datasourcePage.actionEdit')"
                @click="handleEdit"
              >
                <span class="btn-icon">✏️</span>
                <span class="btn-text">{{ t('datasourcePage.actionEdit') }}</span>
              </button>
            </template>
            <template v-else>
              <button
                class="section-action-btn cancel"
                :disabled="saving"
                @click="handleCancel"
              >
                {{ t('common.cancel') }}
              </button>
              <button
                class="section-action-btn primary"
                :disabled="saving"
                @click="handleSave"
              >
                {{ saving ? t('common.loading') : t('common.save') }}
              </button>
            </template>
          </div>
        </div>
        <p class="section-desc">{{ t('metricPlatform.sectionDesc') }}</p>
      </div>

      <!-- 指标平台连接表单 -->
      <div class="form-fields">
        <!-- 显示名称 -->
        <div class="field-row">
          <label class="field-label required">{{ t('metricPlatform.fieldDisplayName') }}</label>
          <input
            v-model="form.displayName"
            class="field-input"
            :disabled="!isEditing"
            :placeholder="t('metricPlatform.placeholderDisplayName')"
          />
        </div>

        <!-- 服务地址 -->
        <div class="field-row">
          <label class="field-label required">{{ t('metricPlatform.fieldServiceAddress') }}</label>
          <input
            v-model="form.serviceAddress"
            class="field-input"
            :disabled="!isEditing"
            :placeholder="t('metricPlatform.placeholderServiceAddress')"
          />
        </div>

        <!-- 产品层端口 -->
        <div class="field-row">
          <label class="field-label required">{{ t('metricPlatform.fieldProductPort') }}</label>
          <input
            v-model="form.productPort"
            class="field-input short"
            :disabled="!isEditing"
            :placeholder="t('metricPlatform.placeholderProductPort')"
          />
        </div>

        <!-- 语义层端口 -->
        <div class="field-row">
          <label class="field-label required">{{ t('metricPlatform.fieldSemanticPort') }}</label>
          <input
            v-model="form.semanticPort"
            class="field-input short"
            :disabled="!isEditing"
            :placeholder="t('metricPlatform.placeholderSemanticPort')"
          />
        </div>

        <!-- 租户ID -->
        <div class="field-row">
          <label class="field-label required">{{ t('metricPlatform.fieldTenantId') }}</label>
          <input
            v-model="form.tenantId"
            class="field-input"
            :disabled="!isEditing"
            :placeholder="t('metricPlatform.placeholderTenantId')"
          />
          <el-tooltip content="租户ID，用于指标查询内容所在的租户" placement="top">
            <span class="field-tip">!</span>
          </el-tooltip>
        </div>

        <!-- 认证方式 -->
        <div class="field-row">
          <label class="field-label required">{{ t('metricPlatform.fieldAuthMethod') }}</label>
          <select v-model="form.authMethod" class="field-select" :disabled="!isEditing">
            <option value="UID">UID</option>
            <option value="TOKEN">TOKEN</option>
            <option value="ACCOUNT">ACCOUNT</option>
            <option value="APIKEY">APIKEY</option>
          </select>
          <el-tooltip content="认证方式。支持 UID、TOKEN、ACCOUNT、APIKEY" placement="top">
            <span class="field-tip">!</span>
          </el-tooltip>
        </div>

        <!-- 认证值 -->
        <div class="field-row">
          <label class="field-label required">{{ t('metricPlatform.fieldAuthValue') }}</label>
          <div class="password-field">
            <input
              v-model="form.authValue"
              class="field-input"
              :type="showPassword ? 'text' : 'password'"
              :disabled="!isEditing"
              :placeholder="t('metricPlatform.placeholderAuthValue')"
            />
            <span class="eye-btn" @click="showPassword = !showPassword">
              <svg v-if="showPassword" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
              <svg v-else xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
            </span>
          </div>
          <el-tooltip content="与认证方式对应的认证值" placement="top">
            <span class="field-tip">!</span>
          </el-tooltip>
        </div>
      </div>
    </section>

    <!-- 指标元数据 -->
    <section class="mp-section">
      <div class="section-header">
        <h2 class="section-title">
          <span class="title-icon">📊</span>
          {{ t('metricPlatform.metadataTitle') }}
          <span class="title-locale">kb.metrics</span>
        </h2>
        <p class="section-desc">{{ t('metricPlatform.metadataDesc') }}</p>
      </div>

      <div class="metadata-list">
        <div
          v-for="(item, idx) in metadataItems"
          :key="idx"
          class="metadata-item"
        >
          <div class="metadata-left">
            <span class="folder-icon">📁</span>
            <span class="metadata-name">{{ item.name }}</span>
          </div>
          <div class="metadata-right">
            <span class="metadata-badge">{{ item.count }} {{ t('metricPlatform.metadataCountSuffix') }}</span>
            <span class="metadata-sync">
              <span class="check-icon">✓</span>
              {{ t('metricPlatform.metadataTagPrefix') }} {{ item.syncedAt }}
            </span>
          </div>
        </div>
      </div>
    </section>

    <!-- 指标管理 -->
    <section class="mp-section">
      <div class="section-header">
        <h2 class="section-title">
          <span class="title-icon">⚙️</span>
          {{ t('metricPlatform.manageTitle') }}
        </h2>
        <p class="section-desc">{{ t('metricPlatform.manageDesc') }}</p>
      </div>

      <div class="indicator-grid">
        <div
          v-for="(ind, idx) in indicators"
          :key="idx"
          class="indicator-card"
        >
          <div class="indicator-head">
            <span class="indicator-name">{{ t(ind.nameKey) }}</span>
            <el-switch v-model="ind.enabled" />
          </div>
          <div class="indicator-meta">
            <span class="tql-tag">{{ t(ind.tagKey) }}</span>
            <span class="meta-text">{{ t(ind.descKey) }}</span>
          </div>
          <div class="indicator-range">{{ t(ind.rangeKey) }}</div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import * as datasourceApi from '@/api/datasource'
import { useDatasourceStore } from '@/stores/useDatasourceStore'
import type { Datasource } from '@/types'

const { t } = useI18n()
const store = useDatasourceStore()

const props = defineProps<{
  datasourceId?: string
  /** 父级用于强制刷新面板的版本号，变更时重新拉取详情 */
  refreshKey?: number
}>()

/** 是否显示密码 */
const showPassword = ref(false)

/** 是否处于编辑模式 */
const isEditing = ref(false)

/** 是否正在保存 */
const saving = ref(false)

/** 连接信息表单初始值，加载后由详情接口回填 */
const form = reactive({
  displayName: '',
  serviceAddress: '',
  productPort: '',
  semanticPort: '',
  tenantId: '',
  authMethod: 'UID',
  authValue: '',
})

/** 编辑前的表单快照（取消时恢复） */
const formBackup = reactive({ ...form })

/** connectionParams 解析后的结构（含 anymetricsPort / semanticPort / authType） */
interface ConnectionParams {
  anymetricsPort?: number
  semanticPort?: number
  authType?: string
}

/** 解析后端返回的 connectionParams 字符串 */
function parseConnectionParams(raw: string | undefined | null): ConnectionParams {
  if (!raw) {
    return {}
  }
  try {
    const obj = JSON.parse(raw) as ConnectionParams
    return obj || {}
  } catch {
    return {}
  }
}

/** 根据详情接口回填表单 */
function fillFormFromDatasource(ds: Datasource): void {
  const cp = parseConnectionParams(ds.connectionParams)
  form.displayName = ds.name || ''
  form.serviceAddress = ds.host || ''
  form.productPort = cp.anymetricsPort != null ? String(cp.anymetricsPort) : ''
  form.semanticPort = cp.semanticPort != null ? String(cp.semanticPort) : ''
  form.tenantId = ds.username || ''
  form.authMethod = cp.authType || 'UID'
  form.authValue = ds.password || ''
  Object.assign(formBackup, form)
}

/** 加载数据源详情并回填表单 */
async function loadDatasource(id: string): Promise<void> {
  try {
    const ds = await datasourceApi.get(id)
    fillFormFromDatasource(ds)
  } catch {
    // 错误由全局 axios 拦截器统一提示
  }
}

/** 切换选中数据源或父级强制刷新时重新加载表单数据 */
watch(
  () => [props.datasourceId, props.refreshKey],
  ([id]) => {
    isEditing.value = false
    if (id) {
      loadDatasource(id)
    }
  },
  { immediate: true },
)

/** 进入编辑模式 */
function handleEdit(): void {
  Object.assign(formBackup, form)
  isEditing.value = true
}

/** 取消编辑 */
function handleCancel(): void {
  Object.assign(form, formBackup)
  isEditing.value = false
}

/** 保存编辑 */
async function handleSave(): Promise<void> {
  if (saving.value) {
    return
  }
  saving.value = true
  try {
    if (props.datasourceId) {
      const params = {
        anymetricsPort: Number(form.productPort) || 8080,
        semanticPort: Number(form.semanticPort) || 8080,
        authType: form.authMethod,
      }
      const updated = await datasourceApi.update(props.datasourceId, {
        name: form.displayName,
        host: form.serviceAddress,
        username: form.tenantId,
        password: form.authValue,
        connectionParams: JSON.stringify(params),
      } as never)
      // 用接口返回的最新数据回填表单，保证与服务端一致
      fillFormFromDatasource(updated)
      // 刷新列表与工具栏的展示名称
      await store.fetchDatasources()
    }
    isEditing.value = false
    ElMessage.success(t('common.success'))
  } catch {
    // error handled by interceptor
  } finally {
    saving.value = false
  }
}

/** 指标元数据列表（演示数据） */
const metadataItems = reactive([
  { name: 'AUM 指标', count: 3, syncedAt: '2025-05-21' },
  { name: '客户指标', count: 3, syncedAt: '2025-05-21' },
  { name: '行为指标', count: 3, syncedAt: '2025-05-21' },
])

/** 指标管理列表（演示数据） */
const indicators = reactive([
  {
    nameKey: 'metricPlatform.indicatorAumName',
    tagKey: 'metricPlatform.indicatorAumTag',
    descKey: 'metricPlatform.indicatorAumDesc',
    rangeKey: 'metricPlatform.indicatorAumRange',
    enabled: true,
  },
  {
    nameKey: 'metricPlatform.indicatorCrossName',
    tagKey: 'metricPlatform.indicatorCrossTag',
    descKey: 'metricPlatform.indicatorCrossDesc',
    rangeKey: 'metricPlatform.indicatorCrossRange',
    enabled: true,
  },
  {
    nameKey: 'metricPlatform.indicatorFundName',
    tagKey: 'metricPlatform.indicatorFundTag',
    descKey: 'metricPlatform.indicatorFundDesc',
    rangeKey: 'metricPlatform.indicatorFundRange',
    enabled: true,
  },
  {
    nameKey: 'metricPlatform.indicatorActiveName',
    tagKey: 'metricPlatform.indicatorActiveTag',
    descKey: 'metricPlatform.indicatorActiveDesc',
    rangeKey: 'metricPlatform.indicatorActiveRange',
    enabled: true,
  },
])
</script>

<style scoped>
.metric-platform-panel {
  display: flex;
  flex-direction: column;
  gap: 28px;
  padding: 24px 32px 32px;
  background: transparent;
  height: 100%;
  box-sizing: border-box;
}

/* ========== 通用 Section ========== */
.mp-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.section-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.section-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 30px;
  padding: 0 12px;
  border: 1px solid #e5e6eb;
  border-radius: 4px;
  background: #fff;
  color: #4e5969;
  font-size: 12.5px;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
  white-space: nowrap;
}

.section-action-btn:hover:not(:disabled) {
  border-color: #165dff;
  color: #165dff;
}

.section-action-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.section-action-btn.cancel:hover:not(:disabled) {
  border-color: #c9cdd4;
  color: #1d2129;
}

.section-action-btn.primary {
  background: #165dff;
  border-color: #165dff;
  color: #fff;
}

.section-action-btn.primary:hover:not(:disabled) {
  background: #0e42d2;
  border-color: #0e42d2;
  color: #fff;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #1d2129;
  margin: 0;
}

.title-icon {
  font-size: 16px;
}

.title-locale {
  font-size: 12px;
  color: #86909c;
  font-weight: 400;
  margin-left: 4px;
}

.section-desc {
  font-size: 12.5px;
  color: #86909c;
  margin: 0;
  line-height: 1.6;
}

/* ========== 指标平台连接表单 ========== */
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
  box-sizing: border-box;
}

.field-input:focus {
  border-color: #165dff;
  box-shadow: 0 0 0 2px rgba(22, 93, 255, 0.1);
}

.field-input:disabled,
.field-select:disabled {
  background: #f7f8fa;
  color: #1d2129;
  cursor: not-allowed;
  border-color: #e5e6eb;
}

.field-input:disabled::placeholder {
  color: #c9cdd4;
}

.field-input.short {
  max-width: 120px;
}

.field-input::placeholder {
  color: #c9cdd4;
}

.field-tip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #f53f3f;
  color: #fff;
  font-size: 11px;
  font-weight: bold;
  cursor: pointer;
  flex-shrink: 0;
  margin-left: 4px;
}

.field-tip:hover {
  background: #cb2630;
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
  flex-shrink: 0;
  box-sizing: border-box;
}

.field-select:focus {
  border-color: #165dff;
  box-shadow: 0 0 0 2px rgba(22, 93, 255, 0.1);
}

.password-field {
  position: relative;
  flex: 1;
  max-width: 360px;
  display: flex;
  align-items: center;
}

.password-field .field-input {
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

/* ========== 指标元数据列表 ========== */
.metadata-list {
  display: flex;
  flex-direction: column;
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  overflow: hidden;
  background: #fff;
}

.metadata-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid #f0f1f3;
  transition: background 0.15s;
}

.metadata-item:last-child {
  border-bottom: none;
}

.metadata-item:hover {
  background: #fafbfc;
}

.metadata-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.folder-icon {
  font-size: 18px;
  color: #ffb547;
}

.metadata-name {
  font-size: 13.5px;
  color: #1d2129;
  font-weight: 500;
}

.metadata-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.metadata-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  font-size: 11.5px;
  font-weight: 500;
  color: #f05a23;
  background: #fff2e8;
  border-radius: 10px;
}

.metadata-sync {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #86909c;
}

.check-icon {
  color: #00b42a;
  font-weight: 700;
}

/* ========== 指标管理 卡片网格 ========== */
.indicator-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.indicator-card {
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  background: #fff;
  padding: 14px 16px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: border-color 0.15s;
}

.indicator-card:hover {
  border-color: #c9cdd4;
}

.indicator-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.indicator-name {
  font-size: 13.5px;
  font-weight: 600;
  color: #1d2129;
}

.indicator-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.tql-tag {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  font-size: 10.5px;
  font-weight: 600;
  color: #f05a23;
  background: #fff2e8;
  border-radius: 3px;
  letter-spacing: 0.3px;
}

.meta-text {
  font-size: 12px;
  color: #4e5969;
}

.indicator-range {
  font-size: 12px;
  color: #86909c;
  line-height: 1.5;
}

:deep(.el-switch) {
  --el-switch-on-color: #f05a23;
}
</style>
