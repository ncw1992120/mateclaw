<template>
  <div class="insight-scene">
    <div class="dashboard-card">
      <div class="dashboard-header">
        <span class="dashboard-title">销售洞察</span>
        <span class="dashboard-badge">AI 生成</span>
      </div>
      <div class="metric-row">
        <div class="metric-item">
          <span class="metric-label">销售额</span>
          <span class="metric-value">¥1,248W</span>
        </div>
        <div class="metric-item">
          <span class="metric-label">订单量</span>
          <span class="metric-value">8,642</span>
        </div>
      </div>
      <div class="chart-area">
        <div class="chart-line">
          <svg viewBox="0 0 240 80" preserveAspectRatio="none">
            <defs>
              <linearGradient id="chartGradient" x1="0%" y1="0%" x2="0%" y2="100%">
                <stop offset="0%" stop-color="#4176E6" stop-opacity="0.35" />
                <stop offset="100%" stop-color="#4176E6" stop-opacity="0" />
              </linearGradient>
            </defs>
            <path d="M0,60 Q30,55 60,45 T120,35 T180,42 T240,20 L240,80 L0,80 Z" fill="url(#chartGradient)" />
            <path d="M0,60 Q30,55 60,45 T120,35 T180,42 T240,20" fill="none" stroke="#4176E6" stroke-width="2.5" stroke-linecap="round" />
          </svg>
        </div>
        <div class="chart-bars">
          <div
            v-for="(height, index) in barHeights"
            :key="index"
            class="chart-bar"
            :style="{ height: `${height}%` }"
          />
        </div>
      </div>
      <div class="insight-tip">
        <el-icon size="12"><Warning /></el-icon>
        <span>本周销售额环比增长 12.5%，华东区贡献最高</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Warning } from '@element-plus/icons-vue'

defineOptions({
  name: 'InsightScene',
})

const barHeights = [45, 62, 38, 78, 55, 88, 70]
</script>

<style scoped>
.insight-scene {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.dashboard-card {
  width: 240px;
  height: 160px;
  padding: 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.15);
  animation: float-window 5s ease-in-out infinite;
  overflow: hidden;
}

@keyframes float-window {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}

.dashboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.dashboard-title {
  font-size: 12px;
  font-weight: 700;
  color: #1a1d23;
}

.dashboard-badge {
  padding: 2px 6px;
  border-radius: 8px;
  font-size: 8px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #4176E6 0%, #4868B2 100%);
}

.metric-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.metric-item {
  flex: 1;
  padding: 6px;
  border-radius: 6px;
  background: #f5f6f8;
}

.metric-label {
  display: block;
  font-size: 8px;
  color: #888;
  margin-bottom: 1px;
}

.metric-value {
  display: block;
  font-size: 11px;
  font-weight: 700;
  color: #1a1d23;
}

.chart-area {
  margin-bottom: 6px;
}

.chart-line {
  height: 34px;
  margin-bottom: 4px;
}

.chart-line svg {
  width: 100%;
  height: 100%;
}

.chart-bars {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 3px;
  height: 22px;
}

.chart-bar {
  flex: 1;
  border-radius: 2px 2px 0 0;
  background: linear-gradient(180deg, #4176E6 0%, #ffab7e 100%);
  opacity: 0.85;
  animation: grow-bar 1.2s ease-out forwards;
  transform-origin: bottom;
}

@keyframes grow-bar {
  from { transform: scaleY(0); }
  to { transform: scaleY(1); }
}

.insight-tip {
  display: none;
}
</style>
