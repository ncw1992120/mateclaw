<template>
  <div class="datasource-form-page">
    <!-- 页面头部 -->
    <header class="form-header">
      <h1 class="form-title">{{ isEditMode ? t('dsForm.editTitle') : t('dsForm.title') }}</h1>
      <!-- 步骤条 -->
      <div class="step-bar">
        <div class="step-item done">
          <span class="step-num">{{ '\u2713' }}</span>
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
      <span class="close-btn" @click="handleClose">{{ '\u2715' }}</span>
    </header>

    <!-- 主体区域 -->
    <div class="form-body">
      <!-- 中间表单区 -->
      <main class="form-main">
        <div v-if="formLoading" class="form-loading">
          <span>{{ t('datasourcePage.loading') }}</span>
        </div>
        <template v-else>
        <div class="form-card">
          <h2 class="card-title">
            {{ t('dsForm.selfHostedDB') }} - {{ datasourceInfo.name }}
          </h2>

          <p v-if="selectedDbId !== 60" class="version-hint">{{ '\uD83D\uDCA1' }} {{ t('dsForm.versionHint', { versions: '5.5、5.6、5.7、8.0' }) }}</p>
          <p v-else class="version-hint">{{ '\uD83D\uDCA1' }} Aloudata CAN 指标平台，支持指标查询、维度分析等功能</p>

          <div class="form-grid">
            <!-- 显示名称 -->
            <div class="form-field form-field-wide">
              <label class="form-label required">{{ t('dsForm.fieldDisplayName') }}</label>
              <input
                v-model="form.displayName"
                class="form-input"
                :placeholder="t('dsForm.placeholderDisplayName')"
              />
            </div>

            <!-- Aloudata 专用配置 -->
            <template v-if="selectedDbId === 60">
              <!-- 产品层服务地址 -->
              <div class="form-field">
                <label class="form-label required">{{ t('metricPlatform.fieldProductAddress') }}</label>
                <input
                  v-model="form.productHost"
                  class="form-input"
                  :placeholder="t('metricPlatform.placeholderProductAddress')"
                />
              </div>

              <!-- 产品层端口 -->
              <div class="form-field">
                <label class="form-label required">{{ t('metricPlatform.fieldProductPort') }}</label>
                <input
                  v-model="form.aloudataPort"
                  class="form-input"
                  placeholder="8083"
                />
              </div>

              <!-- 语义层服务地址 -->
              <div class="form-field">
                <label class="form-label required">{{ t('metricPlatform.fieldSemanticAddress') }}</label>
                <input
                  v-model="form.semanticHost"
                  class="form-input"
                  :placeholder="t('metricPlatform.placeholderSemanticAddress')"
                />
              </div>

              <!-- 语义层端口 -->
              <div class="form-field">
                <label class="form-label required">{{ t('metricPlatform.fieldSemanticPort') }}</label>
                <input
                  v-model="form.semanticPort"
                  class="form-input"
                  placeholder="8085"
                />
              </div>

              <!-- 租户 ID -->
              <div class="form-field">
                <label class="form-label required">
                  <span>租户 ID</span>
                  <el-tooltip content="租户 ID，用于指标查询内容所在的租户" placement="top">
                    <span class="form-tip">?</span>
                  </el-tooltip>
                </label>
                <input
                  v-model="form.tenantId"
                  class="form-input"
                  placeholder="请输入租户 ID"
                />
              </div>

              <!-- 认证方式 -->
              <div class="form-field">
                <label class="form-label required">
                  <span>认证方式</span>
                  <el-tooltip content="认证方式。支持 UID、TOKEN、ACCOUNT、APIKEY" placement="top">
                    <span class="form-tip">?</span>
                  </el-tooltip>
                </label>
                <select v-model="form.authType" class="form-select">
                  <option value="UID">UID</option>
                  <option value="TOKEN">TOKEN</option>
                  <option value="ACCOUNT">ACCOUNT</option>
                  <option value="APIKEY">APIKEY</option>
                </select>
              </div>

              <!-- 认证值 -->
              <div class="form-field form-field-wide">
                <label class="form-label required">
                  <span>认证值</span>
                  <el-tooltip content="与认证方式对应的认证值" placement="top">
                    <span class="form-tip">?</span>
                  </el-tooltip>
                </label>
                <div class="password-field">
                  <input
                    v-model="form.authValue"
                    class="form-input"
                    :type="showPassword ? 'text' : 'password'"
                    :placeholder="form.authType === 'UID' ? '请输入用户 ID' : form.authType === 'TOKEN' ? '请输入 TOKEN' : form.authType === 'APIKEY' ? '请输入 APIKEY' : '请输入账号'"
                  />
                  <button
                    type="button"
                    class="eye-btn"
                    :title="showPassword ? '隐藏' : '显示'"
                    @click="showPassword = !showPassword"
                  >
                    <svg v-if="showPassword" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                    <svg v-else xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
                  </button>
                </div>
              </div>
            </template>

            <!-- 数据库类型通用配置 (非 Aloudata) -->
            <template v-else>
              <!-- 数据库地址 -->
              <div class="form-field">
                <label class="form-label required">{{ t('dsForm.fieldHost') }}</label>
                <input
                  v-model="form.host"
                  class="form-input"
                  :placeholder="t('dsForm.placeholderHost')"
                />
              </div>

              <!-- 端口 -->
              <div class="form-field">
                <label class="form-label required">{{ t('dsForm.fieldPort') }}</label>
                <input
                  v-model="form.port"
                  class="form-input"
                  style="max-width: 120px;"
                />
              </div>

              <!-- 数据库 -->
              <div class="form-field">
                <label class="form-label required">{{ t('dsForm.fieldDatabase') }}</label>
                <input
                  v-model="form.database"
                  class="form-input"
                  :placeholder="t('dsForm.placeholderDatabase')"
                />
              </div>

              <!-- 用户名 -->
              <div class="form-field">
                <label class="form-label required">{{ t('dsForm.fieldUsername') }}</label>
                <input
                  v-model="form.username"
                  class="form-input"
                  :placeholder="t('dsForm.placeholderUsername')"
                />
              </div>

              <!-- 密码 -->
              <div class="form-field form-field-wide">
                <label class="form-label required">{{ t('dsForm.fieldPassword') }}</label>
                <div class="password-field">
                  <input
                    v-model="form.password"
                    class="form-input"
                    :type="showPassword ? 'text' : 'password'"
                    :placeholder="t('dsForm.placeholderPassword')"
                  />
                  <button
                    type="button"
                    class="eye-btn"
                    :title="showPassword ? '隐藏' : '显示'"
                    @click="showPassword = !showPassword"
                  >
                    <svg v-if="showPassword" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                    <svg v-else xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
                  </button>
                </div>
              </div>

              <!-- 服务器版本 -->
              <div class="form-field">
                <label class="form-label">{{ t('dsForm.fieldVersion') }}</label>
                <select v-model="form.version" class="form-select">
                  <option v-for="v in versionOptions" :key="v.value" :value="v.value">{{ v.label }}</option>
                </select>
              </div>

              <!-- VPC/线路配置 -->
              <div class="form-field form-field-wide">
                <label class="form-label" style="font-weight: 600; margin-bottom: 4px;">{{ t('dsForm.vpcConfig') }}</label>
                <p class="field-desc" style="margin: 0; font-size: 12px; color: #86909c;">{{ t('dsForm.vpcDesc') }}</p>
              </div>

              <!-- SSL -->
              <div class="form-field form-field-wide">
                <label class="checkbox-label">
                  <label class="switch">
                    <input v-model="form.sslEnabled" type="checkbox" />
                    <span class="slider"></span>
                  </label>
                  <span class="switch-text">{{ t('dsForm.ssl') }}</span>
                </label>
              </div>

              <!-- SSH -->
              <div class="form-field form-field-wide">
                <label class="checkbox-label">
                  <label class="switch">
                    <input v-model="form.sshEnabled" type="checkbox" />
                    <span class="slider"></span>
                  </label>
                  <span class="switch-text">{{ t('dsForm.ssh') }}</span>
                </label>
                <p v-if="form.sshEnabled" class="field-desc" style="margin: 4px 0 0 40px; font-size: 12px; color: #86909c;">{{ t('dsForm.sshDesc') }}</p>
              </div>

              <!-- 跨 VPC/SQL -->
              <div class="form-field form-field-wide">
                <label class="checkbox-label">
                  <label class="switch">
                    <input v-model="form.crossVpcEnabled" type="checkbox" />
                    <span class="slider"></span>
                  </label>
                  <span class="switch-text">{{ t('dsForm.crossVpcSql') }}</span>
                </label>
                <p v-if="form.crossVpcEnabled" class="field-desc" style="margin: 4px 0 0 40px; font-size: 12px; color: #86909c;">{{ t('dsForm.crossVpcDesc') }}</p>
              </div>

              <!-- 开启上传文件入口 -->
              <div class="form-field form-field-wide">
                <label class="checkbox-label">
                  <input v-model="form.uploadEnabled" type="checkbox" checked />
                  <span class="checkmark-new"></span>
                  <span class="switch-text">{{ t('dsForm.uploadFileEntry') }}</span>
                </label>
              </div>

              <!-- 白名单列表 -->
              <div class="form-field form-field-wide">
                <label class="form-label">{{ t('dsForm.whitelistLabel') }}</label>
                <div class="whitelist-box-new">
                  <pre class="whitelist-text-new">{{ whitelistIps }}</pre>
                  <button class="copy-btn-new" @click="copyWhitelist">{{ t('dsForm.copyWhitelist') }}</button>
                </div>
              </div>
            </template>

            <!-- 共享元数据开关（所有类型均显示，Aloudata 强制勾选并禁用） -->
            <div class="form-field form-field-wide">
              <label class="checkbox-label">
                <label class="switch">
                  <input v-model="form.metaShared" type="checkbox" />
                  <span class="slider"></span>
                </label>
                <span class="switch-text">共享元数据（同工作区所有用户可查看）</span>
              </label>
              <p class="field-desc" style="margin: 4px 0 0 40px; font-size: 12px; color: #86909c;">
                开启后，同工作区其他用户可查看该数据源的元数据（不包含连接配置）
              </p>
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
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import * as datasourceApi from '@/api/datasource'
import { useDatasourceStore } from '@/stores/useDatasourceStore'

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
        // 产品层与语义层地址统一从 connection_params 读取，
        // 未配置时回退到独立字段 productHost / semanticHost，最后回退到通用 host 字段（兼容历史数据）
        let cpAnyHost = ''
        let cpSemHost = ''
        if (ds.connectionParams) {
          try {
            const cp = JSON.parse(ds.connectionParams)
            if (cp.anymetricsHost) {
              cpAnyHost = cp.anymetricsHost
            }
            if (cp.semanticHost) {
              cpSemHost = cp.semanticHost
            }
          } catch {
            // 忽略解析错误
          }
        }
        form.productHost = cpAnyHost || ds.productHost || ds.host || ''
        form.semanticHost = cpSemHost || ds.semanticHost || ds.host || ''
        form.port = String(ds.port || 3306)
        form.aloudataPort = String((ds.sourceType === 'aloudata' && ds.connectionParams)
          ? (JSON.parse(ds.connectionParams).anymetricsPort || 8083)
          : (ds.port || 8083))
        form.database = ds.databaseName || ''
        form.username = ds.username || ''
        form.password = ds.password || ''
        createdDsId.value = ds.id
        const sourceTypeMap: Record<string, number> = {
          mysql: 3,
          postgresql: 15,
          sqlserver: 17,
          aloudata: 60,
        }
        selectedDbId.value = sourceTypeMap[ds.sourceType?.toLowerCase()] || 3
        // 加载 Aloudata 额外配置
        if (ds.connectionParams) {
          try {
            const params = JSON.parse(ds.connectionParams)
            if (params.semanticPort) {
              form.semanticPort = String(params.semanticPort)
            }
            if (params.authType) {
              form.authType = params.authType
            }
          } catch {
            // 忽略解析错误
          }
        }
        // Aloudata 类型加载租户ID和认证值
        if (ds.sourceType === 'aloudata') {
          form.tenantId = ds.username || ''
          form.authValue = ds.password || ''
        }
        // 加载元数据共享状态（按后端存储值）
        form.metaShared = ds.metaShared ?? false
      }
    } catch {
      ElMessage.error(t('dsForm.loadFail'))
    } finally {
      formLoading.value = false
    }
  }
})

/** 当前选中的数据源信息 */
const datasourceInfo = computed(() => {
  const allSources: Record<number, { name: string; icon: string }> = {
    3: { name: 'MySQL', icon: '\uD83D\uDC17' },
    15: { name: 'PostgreSQL', icon: '\uD83D\uDC18' },
    17: { name: 'SQL Server', icon: '\uD83D\uDD35' },
    60: { name: 'Aloudata', icon: '\u274E' },
  }
  return allSources[selectedDbId.value] || { name: 'MySQL', icon: '\uD83D\uDC17' }
})

/** 表单数据 */
const form = reactive({
  displayName: '',
  host: '',
  // Aloudata 产品层与语义层独立地址（独立进程服务）
  productHost: '',
  semanticHost: '',
  port: '3306',
  database: '',
  username: '',
  password: '',
  version: '8.0',
  sslEnabled: false,
  sshEnabled: false,
  crossVpcEnabled: false,
  uploadEnabled: true,
  // 元数据是否共享（同工作区所有用户可查看）
  metaShared: false,
  // Aloudata 专用字段
  semanticPort: '8085',
  aloudataPort: '8083',
  tenantId: '',
  authType: 'UID',
  authValue: '',
})

/**
 * 监听数据源类型切换，仅在新建模式下同步 metaShared 默认值。
 * 编辑模式下不覆盖用户已保存的 metaShared 值。
 */
watch(selectedDbId, (newId) => {
  if (!isEditMode.value) {
    form.metaShared = false
  }
})

/** 版本选项 */
const versionOptions = [
  { value: '8.0', label: '8.0' },
  { value: '5.7', label: '5.7' },
  { value: '5.6', label: '5.6' },
  { value: '5.5', label: '5.5' },
]

/** 白名单 IP */
const whitelistIps = `47.101.100.24/0,191.0.0.0/47,10.137.30/0,192.92.0/0,234.204.106.15.160/0,24.106.15.160/0,23.106/15`

/** 验证表单必填项 */
function validateForm(): { valid: boolean; message: string } {
  const isAloudata = selectedDbId.value === 60
  
  // 通用必填项检查
  if (!form.displayName || !form.displayName.trim()) {
    return { valid: false, message: t('dsForm.validation.displayNameRequired') }
  }
  
  if (isAloudata) {
    // Aloudata 类型必填项
    if (!form.productHost || !form.productHost.trim()) {
      return { valid: false, message: '产品层服务地址不能为空' }
    }
    if (!form.aloudataPort || !String(form.aloudataPort).trim()) {
      return { valid: false, message: '产品层端口不能为空' }
    }
    if (!form.semanticHost || !form.semanticHost.trim()) {
      return { valid: false, message: '语义层服务地址不能为空' }
    }
    if (!form.semanticPort || !String(form.semanticPort).trim()) {
      return { valid: false, message: '语义层端口不能为空' }
    }
    if (!form.tenantId || !form.tenantId.trim()) {
      return { valid: false, message: '租户 ID 不能为空' }
    }
    if (!form.authValue || !form.authValue.trim()) {
      return { valid: false, message: '认证值不能为空' }
    }
  } else {
    // 数据库类型必填项
    if (!form.host || !form.host.trim()) {
      return { valid: false, message: t('dsForm.validation.hostRequired') }
    }
    if (!form.port || !String(form.port).trim()) {
      return { valid: false, message: t('dsForm.validation.portRequired') }
    }
    if (!form.database || !form.database.trim()) {
      return { valid: false, message: t('dsForm.validation.databaseRequired') }
    }
    if (!form.username || !form.username.trim()) {
      return { valid: false, message: t('dsForm.validation.usernameRequired') }
    }
    if (!form.password || !form.password.trim()) {
      return { valid: false, message: t('dsForm.validation.passwordRequired') }
    }
  }
  
  return { valid: true, message: '' }
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
    60: 'aloudata',
  }
  const isAloudata = selectedDbId.value === 60
  const request: Record<string, any> = {
    name: form.displayName,
    sourceType: sourceTypeMap[selectedDbId.value] || 'mysql',
    port: isAloudata ? undefined : Number(form.port) || 3306,
    databaseName: form.database,
    username: isAloudata ? form.tenantId : form.username,
    password: isAloudata ? form.authValue : form.password,
    enabled: true,
    // 元数据是否共享（同工作区所有用户可查看）
    metaShared: form.metaShared,
  }
  // Aloudata 类型：产品层与语义层地址统一存到 connection_params，避免使用 host 字段
  if (isAloudata) {
    request.connectionParams = JSON.stringify({
      anymetricsHost: form.productHost,
      semanticHost: form.semanticHost,
      anymetricsPort: Number(form.aloudataPort) || 8083,
      semanticPort: Number(form.semanticPort) || 8085,
      authType: form.authType,
    })
  } else {
    request.host = form.host
  }
  return request
}

/** 测试连接 */
async function handleTestConnection(): Promise<void> {
  if (testing.value || submitting.value) {
    return
  }
  
  // 验证必填项
  const validation = validateForm()
  if (!validation.valid) {
    ElMessage.error(validation.message)
    return
  }
  
  testing.value = true
  try {
    let result: boolean
    if (isEditMode.value || createdDsId.value) {
      // 编辑模式或已有记录：使用 ID 测试连接
      const testId = createdDsId.value!
      result = await datasourceApi.testConnection(testId)
    } else {
      // 新建模式：仅做连通性测试，不创建数据源记录
      const request = buildCreateRequest()
      result = await datasourceApi.testConnectionApi(request)
    }
    if (result) {
      ElMessage.success(t('datasourcePage.testSuccess'))
    } else {
      ElMessage.error(t('datasourcePage.testFail'))
    }
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
  
  // 验证必填项
  const validation = validateForm()
  if (!validation.valid) {
    ElMessage.error(validation.message)
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

.version-hint {
  font-size: 12px;
  color: #86909c;
  margin: 0 0 20px 0;
}

/* 表单网格布局 */
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px 24px;
  margin-top: 20px;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.form-field-wide {
  grid-column: 1 / -1;
}

.form-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #4e5969;
  font-weight: 500;
}

.form-label.required::before {
  content: '*';
  color: #f53f3f;
  font-weight: 600;
  margin-right: 2px;
}

.form-tip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #e5e6eb;
  color: #86909c;
  font-size: 10px;
  font-weight: bold;
  cursor: help;
  transition: all 0.15s;
}

.form-tip:hover {
  background: #165dff;
  color: #fff;
}

.form-input,
.form-select {
  height: 36px;
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  padding: 0 12px;
  font-size: 13px;
  color: #1d2129;
  outline: none;
  transition: all 0.15s;
  font-family: inherit;
  background: #fff;
  box-sizing: border-box;
  width: 100%;
}

.form-input:hover:not(:disabled),
.form-select:hover:not(:disabled) {
  border-color: #c9cdd4;
}

.form-input:focus,
.form-select:focus {
  border-color: #165dff;
  box-shadow: 0 0 0 3px rgba(22, 93, 255, 0.08);
}

.form-input:disabled {
  background: #f7f8fa;
  color: #1d2129;
  cursor: not-allowed;
  border-color: #e5e6eb;
}

.form-input::placeholder {
  color: #c9cdd4;
}

.form-select {
  appearance: none;
  background-image: url("data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 12 12'%3E%3Cpath fill='%2386909c' d='M6 8.5L1.5 4h9z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  background-size: 10px;
  padding-right: 32px;
}

.password-field {
  position: relative;
  display: flex;
  align-items: center;
}

.password-field .form-input {
  padding-right: 40px;
}

.password-field .form-input::-ms-reveal,
.password-field .form-input::-webkit-credentials-auto-fill-button {
  display: none;
}

.eye-btn {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c9cdd4;
  border-radius: 4px;
  transition: all 0.15s;
}

.eye-btn:hover {
  color: #165dff;
  background: #f2f3f5;
}

/* 开关切换新样式 */
.checkbox-label {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: #4e5969;
  cursor: pointer;
  user-select: none;
}

.checkbox-label input[type='checkbox'] {
  display: none;
}

.switch-text {
  font-size: 13px;
  color: #4e5969;
}

.field-desc {
  font-size: 12px;
  color: #86909c;
  line-height: 1.5;
}

/* 白名单新样式 */
.whitelist-box-new {
  position: relative;
  background: #fafbfc;
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  padding: 12px 80px 12px 16px;
  min-height: 60px;
}

.whitelist-text-new {
  font-family: 'Courier New', monospace;
  font-size: 11.5px;
  color: #4e5969;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  line-height: 1.6;
}

.copy-btn-new {
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

.copy-btn-new:hover {
  border-color: #165dff;
  color: #165dff;
}

/* 复选框新样式 */
.checkmark-new {
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

.checkbox-label input:checked + .checkmark-new {
  background: #165dff;
  border-color: #165dff;
}

.checkbox-label input:checked + .checkmark-new::after {
  content: '✓';
  color: #fff;
  font-size: 11px;
  font-weight: bold;
  position: absolute;
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
  content: '\u2713';
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
</style>
