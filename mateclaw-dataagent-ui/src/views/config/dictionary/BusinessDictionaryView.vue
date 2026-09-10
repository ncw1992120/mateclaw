<template>
  <div class="dictionary-page">
    <!-- 身份页头：标题 + 租户统计 + 描述（左） + 主操作（右），与技能/智能体/数据页同一版式语言 -->
    <div class="dict-content-header">
      <div class="dict-content-title">
        <div class="dict-content-name-row">
          <h3 class="dict-content-name">{{ t('businessDictionary.title') }}</h3>
          <!-- 租户数量标识：常驻标题旁，加载完成后才显示避免首帧 0 闪烁 -->
          <span v-if="statsReady" class="dict-title-stats">
            <span class="summary-item">
              <span class="summary-dot" aria-hidden="true"></span>
              {{ t('businessDictionary.tenantCount') }}
              <b class="summary-num">{{ tenantList.length }}</b>
            </span>
          </span>
        </div>
        <p class="dict-content-desc">{{ t('businessDictionary.desc') }}</p>
      </div>
      <div class="header-actions">
        <button type="button" class="btn-create-pill" @click="handleAddTenant">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true">
            <line x1="12" y1="5" x2="12" y2="19" />
            <line x1="5" y1="12" x2="19" y2="12" />
          </svg>
          {{ t('businessDictionary.addTenant') }}
        </button>
      </div>
    </div>

    <!-- 全局空态：线型 SVG chip + 文案 + 胶囊按钮，与技能/智能体/数据页同形态 -->
    <div v-if="!loading && tenantList.length === 0" class="global-empty surface-card">
      <div class="global-empty-icon">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z" />
          <path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z" />
        </svg>
      </div>
      <h3>{{ t('businessDictionary.emptyDesc') }}</h3>
      <button type="button" class="btn-create-pill small" @click="handleAddTenant">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" aria-hidden="true">
          <line x1="12" y1="5" x2="12" y2="19" />
          <line x1="5" y1="12" x2="19" y2="12" />
        </svg>
        {{ t('businessDictionary.addTenant') }}
      </button>
    </div>

    <!-- 主从布局：左侧租户列表 / 右侧术语面板 -->
    <div v-else class="master-detail-layout">
      <aside class="dict-sidebar">
        <div class="tenant-list-scroll">
          <div
            v-for="item in tenantList"
            :key="item"
            class="tenant-list-item"
            :class="{ active: selectedTenantCode === item }"
            @click="selectedTenantCode = item"
          >
            <span class="item-icon">
              <!-- <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
                <polyline points="9 22 9 12 15 12 15 22"/>
              </svg> -->
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M4 4h11a3 3 0 0 1 3 3v13H7a3 3 0 0 1-3-3z"></path>
                <path d="M8 8h6"></path>
              </svg> 
            </span>
            <span class="item-name" :title="item">{{ item }}</span>
            <button class="item-remove-btn" :title="t('businessDictionary.removeTenant')" @click.stop="handleRemoveTenant(item)">
              <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="3 6 5 6 21 6"/>
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                <line x1="10" y1="11" x2="10" y2="17"/>
                <line x1="14" y1="11" x2="14" y2="17"/>
              </svg>
            </button>
          </div>
        </div>
      </aside>

      <main class="dictionary-detail">
        <BusinessTermPanel
          v-if="selectedTenantCode"
          :tenant-code="selectedTenantCode"
        />
        <div v-else class="detail-placeholder">
          <p>{{ t('businessDictionary.selectTenant') }}</p>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as businessTermApi from '@/api/business-term'
import BusinessTermPanel from '@/views/datasource/BusinessTermPanel.vue'

const { t } = useI18n()

const loading = ref(false)
const tenantList = ref<string[]>([])
const selectedTenantCode = ref('')
/** 首次加载完成后才显示标题旁租户统计，避免首帧 0 闪烁 */
const statsReady = ref(false)

onMounted(() => {
  loadTenantList()
})

/** 加载租户列表（从后端接口获取已有术语的租户编码） */
async function loadTenantList(): Promise<void> {
  loading.value = true
  try {
    const codes = await businessTermApi.listTenantCodes()
    tenantList.value = (codes || []) as unknown as string[]
    if (tenantList.value.length > 0 && !selectedTenantCode.value) {
      selectedTenantCode.value = tenantList.value[0]
    }
  } catch {
    tenantList.value = []
  } finally {
    loading.value = false
    statsReady.value = true
  }
}

/** 添加租户 */
async function handleAddTenant(): Promise<void> {
  try {
    const { value } = await ElMessageBox.prompt(
      t('businessDictionary.addTenantPrompt'),
      t('businessDictionary.addTenant'),
      {
        inputPlaceholder: t('businessDictionary.tenantCodePlaceholder'),
        inputValidator: (val: string) => {
          const trimmed = val?.trim() || ''
          if (!trimmed) {
            return t('businessDictionary.tenantCodeRequired')
          }
          if (trimmed.length > 64) {
            return t('businessDictionary.tenantCodeTooLong')
          }
          if (tenantList.value.includes(trimmed)) {
            return t('businessDictionary.tenantCodeExists')
          }
          return true
        },
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
      },
    )
    const code = (value as string).trim()
    tenantList.value.push(code)
    selectedTenantCode.value = code
  } catch {
    // user cancelled
  }
}

/** 移除租户（删除该租户下的所有术语数据） */
async function handleRemoveTenant(code: string): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('businessDictionary.removeTenantConfirm', { code }),
      t('businessDictionary.removeTenant'),
      {
        type: 'warning',
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
      },
    )
    await businessTermApi.removeByTenantCode(code)
    tenantList.value = tenantList.value.filter(c => c !== code)
    if (selectedTenantCode.value === code) {
      selectedTenantCode.value = tenantList.value.length > 0 ? tenantList.value[0] : ''
    }
    ElMessage.success(t('businessDictionary.removeTenantSuccess'))
  } catch {
    // user cancelled
  }
}
</script>

<style scoped>
.dictionary-page {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  background: transparent;
  overflow: hidden;
}

/* 身份页头：标题/描述靠左，主操作靠右，单行对齐 */
.dict-content-header {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
  padding: 2px 2px 14px;
}

.dict-content-title {
  min-width: 0;
}

.dict-content-name-row {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.dict-content-name {
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

.dict-content-desc {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--db-text-muted);
  line-height: 18px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 租户数量标识：紧凑内联，与技能/数据页统计同一语言 */
.dict-title-stats {
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

/* ========== 主从布局 ========== */
.master-detail-layout {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

/* ========== 左侧：租户侧边栏 ========== */
/* 侧栏：家族卡片面（白面 + 细边 + 圆角 + 弱阴影），与详情区以留白分界而非分线 */
.dict-sidebar {
  width: 220px;
  min-width: 220px;
  background: var(--db-card);
  border: 1px solid var(--db-border);
  border-radius: var(--radius-lg, 12px);
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  margin-right: 14px;
  overflow: hidden;
}

.tenant-list-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

/* 列表项：单行紧凑（图标 + 名称 + 删除按钮），项间由滚动区 gap 分隔 */
.tenant-list-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  flex-shrink: 0;
  cursor: pointer;
  border-radius: 8px;
  transition: background var(--transition-fast, 0.15s);
}

.tenant-list-item:hover {
  background: var(--db-hover);
}

/* 激活态：主题色 10% 淡底 + 名称主题色加粗，与配置侧栏同一语言 */
.tenant-list-item.active {
  background: color-mix(in srgb, var(--main-orange) 10%, transparent);
}

.tenant-list-item.active .item-name {
  color: var(--main-orange);
  font-weight: 600;
}

/* 图标：与左侧配置导航 .nav-item-icon 逐态同构——默认 secondary 色 + 0.9 透明，hover 随行提亮，激活主题色（透明度不覆盖，同 nav） */
.item-icon {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  color: var(--db-text-secondary);
  opacity: 0.9;
  transition: color 120ms ease, opacity 120ms ease;
}

.tenant-list-item:hover .item-icon {
  color: var(--db-text);
}

.tenant-list-item.active .item-icon {
  color: var(--main-orange);
}

.item-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--db-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 删除按钮：24px 幽灵图标按钮，行 hover/激活时淡入 */
.item-remove-btn {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  color: var(--db-text-muted);
  opacity: 0;
  transition: opacity 0.15s, background-color 120ms ease, color 120ms ease;
  padding: 0;
  flex-shrink: 0;
}

.tenant-list-item:hover .item-remove-btn,
.tenant-list-item.active .item-remove-btn {
  opacity: 1;
}

.item-remove-btn:hover {
  background: var(--db-danger-bg);
  color: var(--db-danger);
}

/* ========== 右侧：术语详情 ========== */
.dictionary-detail {
  flex: 1;
  min-width: 0;
  background: transparent;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.detail-placeholder {
  display: flex;
  justify-content: center;
  align-items: center;
  flex: 1;
  color: var(--db-text-muted);
  font-size: 13px;
}

/* ===== 响应式适配 ===== */

/* 中等屏幕：标题行允许换行，避免标题被统计+按钮挤成零宽 */
@media (max-width: 1024px) {
  .dict-content-header {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }
  .dict-content-name-row {
    flex-wrap: wrap;
    gap: 6px 14px;
  }
  .header-actions {
    margin-left: 0;
    justify-content: flex-end;
  }
}

/* 小屏幕：主从改纵向堆叠，侧栏横向铺满并限高 */
@media (max-width: 720px) {
  .master-detail-layout {
    flex-direction: column;
  }
  .dict-sidebar {
    width: 100%;
    min-width: 0;
    margin-right: 0;
    margin-bottom: 14px;
    max-height: 200px;
  }
  .dict-content-desc {
    white-space: normal;
    overflow: visible;
  }
}
</style>
