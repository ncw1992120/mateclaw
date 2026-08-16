<template>
  <div class="dashboard-panel" style="width: 100%; max-width: none; box-sizing: border-box;">
    <div class="right-header" style="width: 100%;">
      <span class="right-title">{{ t('dashboard.previewTitle') }}</span>
      <button class="right-collapse-btn" @click="emit('collapse')" title="折叠右栏">▶</button>
    </div>

    <div class="tabs" style="width: 100%;">
      <button
        class="tab"
        :class="activeTab === 'dashboard' ? 'active' : 'inactive'"
        @click="activeTab = 'dashboard'"
      >
        {{ t('dashboard.tabDashboard') }}
      </button>
      <button
        class="tab"
        :class="activeTab === 'rawdata' ? 'active' : 'inactive'"
        @click="activeTab = 'rawdata'"
      >
        {{ t('dashboard.tabRawData') }}
      </button>
    </div>

    <!-- Dashboard View -->
    <div v-show="activeTab === 'dashboard'" class="view-dashboard" style="width: 100%; flex: 1; overflow: hidden; display: flex; flex-direction: column;">
      <div class="dashboard-content" style="width: 100%; flex: 1; overflow-y: auto; overflow-x: hidden;">
      <div class="section-label" style="width: 100%;">
        <span class="text">{{ t('dashboard.kpiOverview') }}</span>
      </div>

      <div class="kpi-row" style="width: 100%; display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 20px;">
        <div class="kpi highlight">
          <div class="kpi-val">¥1,245,000</div>
          <div class="kpi-name">{{ t('dashboard.kpiTurnover') }}</div>
          <div class="kpi-chg up">↑ 12.5% {{ t('dashboard.mom') }}</div>
        </div>
        <div class="kpi normal">
          <div class="kpi-val">85,432</div>
          <div class="kpi-name">{{ t('dashboard.kpiActiveCustomers') }}</div>
          <div class="kpi-chg down">↓ 2.1% {{ t('dashboard.mom') }}</div>
        </div>
        <div class="kpi normal">
          <div class="kpi-val">12.5%</div>
          <div class="kpi-name">{{ t('dashboard.kpiConversion') }}</div>
          <div class="kpi-chg up">↑ 0.8% {{ t('dashboard.mom') }}</div>
        </div>
      </div>

      <div class="section-label" style="width: 100%;">
        <span class="text">{{ t('dashboard.chartAnalysis') }}</span>
        <div class="chart-type-btns">
          <button
            v-for="ct in chartTypes"
            :key="ct.key"
            class="ct-btn"
            :class="{ active: activeChartType === ct.key }"
            @click="switchChart(ct.key)"
          >
            {{ ct.label }}
          </button>
        </div>
      </div>
      <div ref="chartRef" class="right-chart" style="width: 100% !important; height: 200px; min-height: 180px; margin-bottom: 12px;"></div>

      <div class="trend-bar" style="width: 100%;">📈 {{ t('dashboard.trendInsight') }}</div>

      <div class="anomaly-card" style="width: 100%;">
        <div class="anomaly-title">⚠ {{ t('dashboard.anomalyDetection') }}</div>
        <div class="anomaly-desc">{{ t('dashboard.anomalyDesc') }}</div>
      </div>

      <div class="section-label" style="width: 100%;">
        <span class="text">{{ t('dashboard.rawData') }}</span>
      </div>
      <table class="data-table" style="width: 100% !important; max-width: none !important; table-layout: fixed; border-collapse: collapse;">
        <thead>
          <tr>
            <th>{{ t('dashboard.colDate') }}</th>
            <th>{{ t('dashboard.colRegion') }}</th>
            <th>{{ t('dashboard.colMetricVal') }}</th>
            <th>{{ t('dashboard.colMom') }}</th>
            <th>{{ t('dashboard.colStatus') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>2026-05-14</td>
            <td>{{ t('dashboard.regionEast') }}</td>
            <td>¥120,000</td>
            <td style="color:var(--main-orange)">↑ 8.2%</td>
            <td><span class="status-ok">{{ t('dashboard.statusNormal') }}</span></td>
          </tr>
          <tr>
            <td>2026-05-13</td>
            <td>{{ t('dashboard.regionSouth') }}</td>
            <td>¥80,000</td>
            <td style="color:var(--mid-grey)">↓ 3.1%</td>
            <td><span class="status-ok">{{ t('dashboard.statusNormal') }}</span></td>
          </tr>
          <tr>
            <td>2026-05-12</td>
            <td>{{ t('dashboard.regionNorth') }}</td>
            <td>¥95,000</td>
            <td style="color:var(--main-orange)">↑ 15.3%</td>
            <td><span class="status-warn">{{ t('dashboard.statusAnomaly') }}</span></td>
          </tr>
        </tbody>
      </table>
      </div>
    </div>

    <!-- Raw Data View -->
    <div v-show="activeTab === 'rawdata'" class="view-rawdata" style="width: 100%; flex: 1; overflow-y: auto; overflow-x: hidden;">
      <table class="data-table" style="width:100%">
        <thead>
          <tr>
            <th>{{ t('dashboard.colDate') }}</th>
            <th>{{ t('dashboard.colRegion') }}</th>
            <th>{{ t('dashboard.kpiTurnover') }}</th>
            <th>{{ t('dashboard.kpiActiveCustomers') }}</th>
            <th>{{ t('dashboard.kpiConversion') }}</th>
            <th>{{ t('dashboard.colMom') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>05-14</td>
            <td>{{ t('dashboard.regionEast') }}</td>
            <td>¥120,000</td>
            <td>32,100</td>
            <td>13.2%</td>
            <td style="color:var(--main-orange)">↑ 8.2%</td>
          </tr>
          <tr>
            <td>05-14</td>
            <td>{{ t('dashboard.regionSouth') }}</td>
            <td>¥80,000</td>
            <td>21,500</td>
            <td>11.8%</td>
            <td style="color:var(--mid-grey)">↓ 3.1%</td>
          </tr>
          <tr>
            <td>05-13</td>
            <td>{{ t('dashboard.regionEast') }}</td>
            <td>¥111,000</td>
            <td>30,800</td>
            <td>12.9%</td>
            <td style="color:var(--main-orange)">↑ 5.1%</td>
          </tr>
          <tr>
            <td>05-13</td>
            <td>{{ t('dashboard.regionSouth') }}</td>
            <td>¥82,500</td>
            <td>22,100</td>
            <td>12.1%</td>
            <td style="color:var(--mid-grey)">↓ 1.2%</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import * as echarts from 'echarts'

const { t } = useI18n()
const emit = defineEmits<{
  collapse: []
}>()

/** 当前活跃标签 */
const activeTab = ref<'dashboard' | 'rawdata'>('dashboard')

/** 图表类型 */
const chartTypes = [
  { key: 'line', label: '折线' },
  { key: 'bar', label: '柱状' },
  { key: 'heatmap', label: '热力' },
]
const activeChartType = ref('line')

/** 图表容器引用 */
const chartRef = ref<HTMLElement | null>(null)
/** ECharts 实例 */
let chartInstance: echarts.ECharts | null = null

/** 示例数据 */
const days = ['05-08', '05-09', '05-10', '05-11', '05-12', '05-13', '05-14']
const barData = [85, 92, 145, 100, 110, 115, 120]
const lineData = [88, 90, 95, 92, 98, 100, 105]

/** 初始化 ECharts（仅在容器可见且有尺寸时执行） */
function initChart(): void {
  if (!chartRef.value || chartRef.value.clientWidth === 0) return
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
  chartInstance = echarts.init(chartRef.value)
  setChartOption(activeChartType.value)
}

/** 延迟初始化 ECharts（等待容器可见） */
function deferredInit(): void {
  nextTick(() => {
    if (chartRef.value && chartRef.value.clientWidth > 0) {
      initChart()
    } else {
      setTimeout(deferredInit, 200)
    }
  })
}

/** 切换图表 */
function switchChart(type: string): void {
  activeChartType.value = type
  setChartOption(type)
}

/** 设置图表配置 */
function setChartOption(type: string): void {
  if (!chartInstance) return

  if (type === 'line') {
    chartInstance.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 40, right: 16, top: 10, bottom: 20 },
      xAxis: { type: 'category', data: days, axisLabel: { fontSize: 9, color: '#aaa' } },
      yAxis: { type: 'value', axisLabel: { fontSize: 9, color: '#aaa' }, splitLine: { lineStyle: { color: '#eee' } } },
      series: [{
        type: 'line', smooth: true, data: lineData,
        lineStyle: { color: '#4176E6', width: 2 },
        itemStyle: { color: '#4176E6' },
        areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(65,118,230,0.2)' }, { offset: 1, color: 'rgba(65,118,230,0)' }] } }
      }]
    }, true)
  } else if (type === 'bar') {
    chartInstance.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 40, right: 16, top: 10, bottom: 20 },
      xAxis: { type: 'category', data: days, axisLabel: { fontSize: 9, color: '#aaa' } },
      yAxis: { type: 'value', axisLabel: { fontSize: 9, color: '#aaa' }, splitLine: { lineStyle: { color: '#eee' } } },
      series: [
        { type: 'bar', data: barData, barWidth: 20, itemStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: '#4176E6' }, { offset: 1, color: '#B7C8FE' }] }, borderRadius: [4, 4, 0, 0] } },
        { type: 'line', smooth: true, data: lineData, lineStyle: { color: '#7F7F7F', width: 2, type: 'dashed' }, itemStyle: { color: '#7F7F7F' }, symbol: 'none' }
      ]
    }, true)
  } else {
    chartInstance.setOption({
      tooltip: { trigger: 'item' },
      grid: { left: 40, right: 16, top: 10, bottom: 20 },
      xAxis: { type: 'category', data: days, axisLabel: { fontSize: 9, color: '#aaa' } },
      yAxis: { type: 'value', axisLabel: { fontSize: 9, color: '#aaa' }, splitLine: { lineStyle: { color: '#eee' } } },
      series: [{
        type: 'bar', barWidth: 20,
        data: barData.map(v => ({ value: v, itemStyle: { color: v > 120 ? '#4868B2' : v > 100 ? '#4176E6' : '#B7C8FE' } })),
        itemStyle: { borderRadius: [4, 4, 0, 0] }
      }]
    }, true)
  }
}

/** 窗口缩放事件处理 */
function handleResize(): void {
  if (chartInstance && chartRef.value && chartRef.value.clientWidth > 0) {
    chartInstance.resize()
  }
}

let resizeObserver: ResizeObserver | null = null

watch(activeTab, () => {
  if (activeTab.value === 'dashboard') {
    nextTick(() => initChart())
  }
})

onMounted(() => {
  deferredInit()
  window.addEventListener('resize', handleResize)
  if (chartRef.value) {
    resizeObserver = new ResizeObserver(() => {
      if (!chartInstance && chartRef.value && chartRef.value.clientWidth > 0) {
        initChart()
      } else if (chartInstance) {
        chartInstance.resize()
      }
    })
    resizeObserver.observe(chartRef.value)
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  resizeObserver?.disconnect()
  chartInstance?.dispose()
})
</script>

<style scoped>
.dashboard-panel {
  height: 100%;
  width: 100%;
  max-width: none !important;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-sizing: border-box;
}

.dashboard-panel > * {
  width: 100% !important;
  max-width: none !important;
  box-sizing: border-box;
}

.view-dashboard {
  flex: 1;
  width: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.dashboard-content {
  flex: 1;
  width: 100%;
  overflow-y: auto;
  overflow-x: hidden;
}

.view-rawdata {
  flex: 1;
  width: 100%;
  overflow-y: auto;
  overflow-x: hidden;
}

.right-header {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}

.right-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--dark-text);
}

.right-collapse-btn {
  margin-left: auto;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: 1px solid var(--light-grey);
  background: #fff;
  font-size: 14px;
  color: var(--muted);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-family: inherit;
}

.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.tab {
  padding: 5px 14px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  border: none;
  font-family: inherit;
}

.tab.active {
  background: var(--main-orange);
  color: #fff;
}

.tab.inactive {
  background: var(--lighter-grey);
  color: var(--mid-grey);
  border: 1px solid var(--light-grey);
}

.section-label {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.section-label .text {
  font-size: 12px;
  font-weight: 700;
  color: var(--blue-grey);
}

.section-label .badge {
  padding: 2px 8px;
  border-radius: 8px;
  background: var(--very-light-orange);
  border: 1px solid var(--light-orange);
  font-size: 9px;
  color: var(--dark-orange);
}

.kpi-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  width: 100%;
  gap: 12px;
  margin-bottom: 20px;
}

.kpi {
  border-radius: 10px;
  padding: 12px;
  text-align: center;
  min-width: 0;
}

.kpi.highlight {
  background: var(--light-orange);
  border: 1px solid var(--main-orange);
}

.kpi.normal {
  background: var(--lighter-grey);
  border: 1px solid var(--light-grey);
}

.kpi-val {
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 2px;
}

.kpi.highlight .kpi-val {
  color: var(--dark-orange);
}

.kpi.normal .kpi-val {
  color: var(--dark-text);
}

.kpi-name {
  font-size: 11px;
  color: #666;
  margin-bottom: 4px;
}

.kpi-chg {
  font-size: 11px;
  font-weight: 600;
}

.kpi-chg.up {
  color: var(--main-orange);
}

.kpi-chg.down {
  color: var(--mid-grey);
}

.chart-type-btns {
  display: flex;
  gap: 6px;
  margin-left: auto;
}

.ct-btn {
  padding: 3px 10px;
  border-radius: 10px;
  font-size: 10px;
  border: 1px solid var(--light-grey);
  background: var(--lighter-grey);
  color: var(--mid-grey);
  cursor: pointer;
  font-family: inherit;
}

.ct-btn.active {
  background: var(--main-orange);
  color: #fff;
  border-color: var(--main-orange);
}

.right-chart {
  width: 100% !important;
  height: 200px;
  min-height: 180px;
  margin-bottom: 12px;
}

.trend-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--very-light-orange);
  border: 1px solid var(--light-orange);
  border-radius: 8px;
  padding: 8px 12px;
  margin-bottom: 12px;
  font-size: 11px;
  color: var(--blue-grey);
}

.anomaly-card {
  border: 1px solid var(--light-grey);
  border-radius: 8px;
  padding: 10px 12px;
  position: relative;
  margin-bottom: 16px;
  overflow: hidden;
}

.anomaly-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  width: 4px;
  height: 100%;
  background: var(--main-orange);
}

.anomaly-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--main-orange);
  margin-bottom: 4px;
}

.anomaly-desc {
  font-size: 11px;
  color: var(--body-text);
  padding-left: 12px;
}

.data-table {
  width: 100% !important;
  max-width: none !important;
  table-layout: fixed;
  border-collapse: collapse;
  border: 1px solid var(--light-grey);
  border-radius: 6px;
  overflow: hidden;
  font-size: 11px;
}

.data-table th {
  background: var(--light-grey);
  color: var(--blue-grey);
  font-weight: 600;
  padding: 6px 10px;
  text-align: center;
}

.data-table td {
  padding: 5px 10px;
  text-align: center;
  color: var(--body-text);
  border-top: 1px solid var(--light-grey);
}

.status-ok {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 8px;
  background: var(--very-light-orange);
  border: 1px solid var(--light-orange);
  font-size: 10px;
  font-weight: 600;
  color: var(--dark-orange);
}

.status-warn {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 8px;
  background: var(--light-orange);
  border: 1px solid var(--main-orange);
  font-size: 10px;
  font-weight: 600;
  color: var(--main-orange);
}
</style>
