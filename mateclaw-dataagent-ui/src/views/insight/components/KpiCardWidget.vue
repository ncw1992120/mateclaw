<template>
  <div class="kpi-card-widget">
    <div class="kpi-value">{{ kpiData?.value ?? '--' }}</div>
    <div class="kpi-name">{{ kpiData?.name ?? component.title }}</div>
    <div v-if="kpiData?.chg" class="kpi-chg" :class="kpiData.up ? 'up' : 'down'">
      <span>{{ kpiData.up ? '↑' : '↓' }} {{ kpiData.chg }}</span>
    </div>
    <div v-else class="kpi-placeholder">{{ t('insight.kpiNoData') }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { InsightComponent, InsightComponentData } from '@/types'

defineOptions({
  name: 'KpiCardWidget',
})

const { t } = useI18n()

const props = defineProps<{
  /** 组件配置 */
  component: InsightComponent
  /** 组件渲染数据 */
  componentData?: InsightComponentData
}>()

const kpiData = computed(() => props.componentData?.kpi)
</script>

<style scoped>
.kpi-card-widget {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 16px;
  box-sizing: border-box;
  background: var(--theme-surface);
  border-radius: 8px;
  border: 1px solid var(--theme-border);
}

.kpi-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--theme-text);
  line-height: 1.2;
  margin-bottom: 6px;
}

.kpi-name {
  font-size: 13px;
  color: var(--theme-text-secondary);
  margin-bottom: 4px;
}

.kpi-chg {
  font-size: 12px;
  font-weight: 500;
}

.kpi-chg.up {
  color: #52c41a;
}

.kpi-chg.down {
  color: #f5222d;
}

.kpi-placeholder {
  font-size: 12px;
  color: var(--theme-text-muted);
}
</style>
