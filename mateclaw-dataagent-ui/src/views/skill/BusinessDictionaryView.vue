<template>
  <div class="business-dictionary-page">
    <!-- 顶部标题栏 -->
    <div class="page-topbar">
      <h1 class="topbar-title">{{ t('businessDictionary.title') }}</h1>
    </div>

    <!-- 空状态 -->
    <div v-if="!loading && tenantList.length === 0 && !selectedTenantCode" class="empty-section">
      <div class="empty-icon-wrapper">
        <span class="empty-folder-icon">📖</span>
      </div>
      <p class="empty-desc">{{ t('businessDictionary.emptyDesc') }}</p>
      <button class="btn-create-empty" @click="handleAddTenant">
        ＋ {{ t('businessDictionary.addTenant') }}
      </button>
    </div>

    <!-- 主从布局 -->
    <div v-else class="master-detail-layout">
      <!-- 左侧：租户列表 -->
      <aside class="tenant-sidebar">
        <div class="sidebar-header">
          <span class="sidebar-label">{{ t('businessDictionary.tenantList') }}</span>
          <button class="sidebar-add-btn" :title="t('businessDictionary.addTenant')" @click="handleAddTenant">＋</button>
        </div>
        <div class="tenant-list-scroll">
          <div
            v-for="item in tenantList"
            :key="item"
            class="tenant-list-item"
            :class="{ active: selectedTenantCode === item }"
            @click="selectedTenantCode = item"
          >
            <span class="item-icon">🏢</span>
            <span class="item-name">{{ item }}</span>
            <button class="item-remove-btn" :title="t('businessDictionary.removeTenant')" @click.stop="handleRemoveTenant(item)">✕</button>
          </div>
        </div>
      </aside>

      <!-- 右侧：术语面板 -->
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
.business-dictionary-page {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  background: var(--theme-bg);
  overflow: hidden;
}

.page-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: var(--theme-surface);
  border-bottom: 1px solid var(--theme-border);
  flex-shrink: 0;
}

.topbar-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--theme-text);
  margin: 0;
}

.empty-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  padding: 80px 0;
  background: var(--theme-surface);
}

.empty-icon-wrapper {
  position: relative;
  width: 80px;
  height: 80px;
  margin-bottom: 24px;
}

.empty-folder-icon {
  font-size: 72px;
  opacity: 0.6;
}

.empty-desc {
  font-size: 14px;
  color: var(--theme-text-muted);
  margin: 0 0 20px 0;
  text-align: center;
  max-width: 320px;
  line-height: 1.6;
}

.btn-create-empty {
  height: 32px;
  padding: 0 16px;
  border-radius: 4px;
  border: none;
  background: var(--main-orange);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}

.btn-create-empty:hover {
  background: var(--dark-orange);
}

/* ========== 主从布局 ========== */
.master-detail-layout {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

/* ========== 左侧：租户侧边栏 ========== */
.tenant-sidebar {
  width: 220px;
  min-width: 220px;
  background: var(--theme-surface);
  border-right: 1px solid var(--theme-border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--theme-border);
}

.sidebar-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--theme-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.sidebar-add-btn {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  color: var(--main-orange);
  transition: all 0.15s;
}

.sidebar-add-btn:hover {
  background: var(--theme-surface-hover);
}

.tenant-list-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.tenant-list-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  cursor: pointer;
  transition: all 0.15s;
  border-left: 3px solid transparent;
}

.tenant-list-item:hover {
  background: var(--theme-surface-hover);
}

.tenant-list-item.active {
  background: rgba(240, 90, 35, 0.1);
  border-left-color: var(--main-orange);
}

.tenant-list-item .item-icon {
  flex-shrink: 0;
  font-size: 16px;
}

.tenant-list-item .item-name {
  flex: 1;
  font-size: 13px;
  font-weight: 500;
  color: var(--theme-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tenant-list-item .item-remove-btn {
  width: 20px;
  height: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  font-size: 10px;
  color: var(--theme-text-muted);
  opacity: 0;
  transition: all 0.15s;
  padding: 0;
}

.tenant-list-item:hover .item-remove-btn,
.tenant-list-item.active .item-remove-btn {
  opacity: 1;
}

.tenant-list-item .item-remove-btn:hover {
  background: #f53f3f;
  color: #fff;
}

/* ========== 右侧：术语详情 ========== */
.dictionary-detail {
  flex: 1;
  min-width: 0;
  background: var(--theme-surface);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.detail-placeholder {
  display: flex;
  justify-content: center;
  align-items: center;
  flex: 1;
  color: var(--theme-text-muted);
  font-size: 13px;
}
</style>
