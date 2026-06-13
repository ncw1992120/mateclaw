<template>
  <div class="config-center-page">
    <!-- 顶部标题 -->
    <header class="config-topbar">
      <h1 class="page-title">{{ t('nav.subSkill') }}</h1>
    </header>

    <!-- Tab 横向栏 -->
    <nav class="config-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="tab-item"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        {{ t(tab.labelKey) }}
      </button>
    </nav>

    <!-- 内容区 -->
    <section class="config-content">
      <SkillManage v-if="activeTab === 'skill'" />
      <DataConfigView v-else-if="activeTab === 'data'" />
      <AgentConfigView v-else-if="activeTab === 'agent'" />
      <ModelConfigView v-else-if="activeTab === 'model'" />
      <KnowledgeConfigView v-else-if="activeTab === 'knowledge'" />
    </section>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { usePersistedRef } from '@/composables/usePersistedRef'
import SkillManage from './SkillManage.vue'
import DataConfigView from './DataConfigView.vue'
import AgentConfigView from './AgentConfigView.vue'
import ModelConfigView from './ModelConfigView.vue'
import KnowledgeConfigView from './KnowledgeConfigView.vue'

const { t } = useI18n()

/** Tab 可选取值 */
const TAB_KEYS = ['skill', 'data', 'agent', 'model', 'knowledge'] as const
type TabKey = (typeof TAB_KEYS)[number]

/** 当前激活的 Tab（刷新后保留） */
const activeTab = usePersistedRef<TabKey>(
  'mc-config-center-active-tab',
  'skill',
  (value) => (TAB_KEYS as readonly string[]).includes(value),
)

/** Tab 配置：i18n key */
const tabs = [
  {
    key: 'skill' as const,
    labelKey: 'configCenter.tabSkill',
  },
  {
    key: 'data' as const,
    labelKey: 'configCenter.tabData',
  },
  {
    key: 'agent' as const,
    labelKey: 'configCenter.tabAgent',
  },
  {
    key: 'model' as const,
    labelKey: 'configCenter.tabModel',
  },
  {
    key: 'knowledge' as const,
    labelKey: 'configCenter.tabKnowledge',
  },
]
</script>

<style scoped>
.config-center-page {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  background: #f5f6f8;
  overflow: hidden;
}

/* 顶部标题区 */
.config-topbar {
  padding: 18px 24px 14px;
  background: #fff;
  flex-shrink: 0;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #1d2129;
  margin: 0;
  letter-spacing: 0.5px;
}

/* Tab 横向栏 */
.config-tabs {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 24px;
  background: #fff;
  border-bottom: 1px solid #e8ecf2;
  flex-shrink: 0;
}

.tab-item {
  position: relative;
  padding: 12px 16px;
  font-size: 14px;
  color: #4e5969;
  background: transparent;
  border: none;
  cursor: pointer;
  transition: color 0.2s;
  margin-bottom: -1px;
}

.tab-item::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 0;
  transform: translateX(-50%);
  width: 0;
  height: 3px;
  background: #f05a23;
  border-radius: 2px 2px 0 0;
  transition: width 0.2s;
}

.tab-item:hover {
  color: #f05a23;
}

.tab-item:hover::after {
  width: 60%;
}

.tab-item.active {
  color: #f05a23;
  font-weight: 600;
}

.tab-item.active::after {
  width: 80%;
}

/* 内容区 */
.config-content {
  flex: 1;
  min-height: 0;
  overflow: auto;
  background: #f5f6f8;
}
</style>
