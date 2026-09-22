<template>
  <el-icon
    v-if="visible"
    class="dashboard-component-icon"
    :size="iconSize"
    :style="iconStyle"
    aria-hidden="true"
  >
    <component :is="iconComponent" />
  </el-icon>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  Aim,
  Calendar,
  Collection,
  DataAnalysis,
  Filter,
  Grid,
  Histogram,
  MagicStick,
  PieChart,
  TrendCharts,
} from '@element-plus/icons-vue'
import type { ChartType, InsightComponentType, ResolvedDashboardTheme } from '@/types'
import { componentIconStyle, resolveDashboardIcon } from '@/utils/dashboard-theme'

const props = withDefaults(defineProps<{
  type: InsightComponentType | 'tab'
  chartType?: ChartType
  title?: string
  dashboardTheme?: ResolvedDashboardTheme
  size?: number
}>(), { size: 14 })

const iconRegistry = { Aim, Calendar, Collection, DataAnalysis, Filter, Grid, Histogram, MagicStick, PieChart, TrendCharts }
const visible = computed(() => props.dashboardTheme?.iconMode !== 'hide')
const iconComponent = computed(() => iconRegistry[resolveDashboardIcon(props.type, props.chartType, props.title) as keyof typeof iconRegistry] ?? Collection)
const iconStyle = computed(() => componentIconStyle(props.dashboardTheme, props.type, props.title))
const iconSize = computed(() => Number.parseInt(iconStyle.value['--dashboard-icon-size'] ?? `${props.size}`, 10))
</script>

<style scoped>
.dashboard-component-icon {
  flex: 0 0 auto;
  vertical-align: -2px;
  color: var(--dashboard-icon-color, currentColor);
  font-weight: var(--dashboard-icon-weight, 800);
  transform: scale(1.08);
}
.dashboard-component-icon :deep(svg) { stroke-width: 2.35; }
</style>
