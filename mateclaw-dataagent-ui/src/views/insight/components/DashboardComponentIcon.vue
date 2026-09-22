<template>
  <el-icon
    v-if="visible"
    class="dashboard-component-icon"
    :size="size"
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
import { resolveDashboardIcon } from '@/utils/dashboard-theme'

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
</script>

<style scoped>
.dashboard-component-icon { flex: 0 0 auto; vertical-align: -2px; }
</style>
