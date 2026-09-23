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
import type { ChartType, ComponentThemeAccentGroup, ComponentTitleIconStyle, InsightComponentType, ResolvedDashboardTheme } from '@/types'
import { componentIconStyle, resolveDashboardIcon } from '@/utils/dashboard-theme'
import { resolveDashboardIcon as resolveRegisteredIcon } from '@/utils/dashboard-icon-registry'

const props = withDefaults(defineProps<{
  type: InsightComponentType | 'tab'
  chartType?: ChartType
  title?: string
  dashboardTheme?: ResolvedDashboardTheme
  titleIconStyle?: ComponentTitleIconStyle
  themeAccentGroup?: ComponentThemeAccentGroup
  size?: number
  variant?: number
}>(), { size: 14 })

const iconRegistry = { Aim, Calendar, Collection, DataAnalysis, Filter, Grid, Histogram, MagicStick, PieChart, TrendCharts }
const visible = computed(() => Boolean(props.titleIconStyle?.iconKey) || props.dashboardTheme?.iconMode !== 'hide')
const iconComponent = computed(() => {
  const selected = resolveRegisteredIcon(props.titleIconStyle?.iconKey)
  if (selected) return selected
  return iconRegistry[resolveDashboardIcon(props.type, props.chartType, props.title) as keyof typeof iconRegistry] ?? Collection
})
const iconStyle = computed(() => {
  const style = componentIconStyle(props.dashboardTheme, props.type, props.title, props.variant, props.themeAccentGroup)
  const customColor = props.titleIconStyle?.colorMode === 'custom' && /^#[0-9a-f]{6}$/i.test(props.titleIconStyle.color ?? '')
    ? props.titleIconStyle.color
    : undefined
  const strokeWidth = [1.5, 2, 2.5, 3].includes(Number(props.titleIconStyle?.strokeWidth))
    ? Number(props.titleIconStyle?.strokeWidth)
    : 2.35
  return {
    ...style,
    ...(customColor ? { '--dashboard-icon-color': customColor } : {}),
    '--dashboard-icon-stroke-width': `${strokeWidth}px`,
  }
})
const iconSize = computed(() => Number.parseInt(iconStyle.value['--dashboard-icon-size'] ?? `${props.size}`, 10))
</script>

<style scoped>
.dashboard-component-icon {
  flex: 0 0 auto;
  vertical-align: -2px;
  color: var(--dashboard-icon-color, currentColor);
  font-weight: var(--dashboard-icon-weight, 800);
  margin-right: 6px;
  transform: scale(1.08);
}
.dashboard-component-icon :deep(svg) {
  stroke: currentColor;
  stroke-width: var(--dashboard-icon-stroke-width, 2.35px);
  vector-effect: non-scaling-stroke;
}
</style>
