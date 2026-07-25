<template>
  <div class="report-scene">
    <div class="report-card">
      <div class="report-cover">
        <div class="cover-badge">AI 生成</div>
        <h3 class="cover-title">月度经营分析</h3>
        <p class="cover-date">2026 年 6 月</p>
      </div>
      <div class="report-body">
        <div class="report-metric">
          <span class="metric-value">¥1,248W</span>
          <span class="metric-label">总销售额</span>
        </div>
        <div class="report-chart">
          <svg viewBox="0 0 200 50" preserveAspectRatio="none">
            <path
              d="M0,40 Q30,35 60,25 T120,20 T200,10"
              fill="none"
              stroke="#f05a23"
              stroke-width="2.5"
              stroke-linecap="round"
              stroke-dasharray="260"
              stroke-dashoffset="260"
            >
              <animate attributeName="stroke-dashoffset" from="260" to="0" dur="1.2s" fill="freeze" />
            </path>
          </svg>
        </div>
        <div class="report-tags">
          <span class="tag">趋势</span>
          <span class="tag">洞察</span>
          <span class="tag">建议</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'ReportScene',
})
</script>

<style scoped>
.report-scene {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.report-card {
  width: 240px;
  height: 160px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.15);
  overflow: hidden;
  animation: float-window 5s ease-in-out infinite;
  display: flex;
  flex-direction: column;
}

@keyframes float-window {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}

.report-cover {
  position: relative;
  padding: 14px 16px;
  background: linear-gradient(135deg, #f05a23 0%, #e75c01 100%);
  color: #fff;
}

.cover-badge {
  position: absolute;
  top: 10px;
  right: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 10px;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.2);
}

.cover-title {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.cover-date {
  margin: 4px 0 0 0;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.85);
}

.report-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 12px 16px;
}

.report-metric {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.metric-value {
  font-size: 18px;
  font-weight: 700;
  color: #1a1d23;
}

.metric-label {
  font-size: 11px;
  color: #888;
}

.report-chart {
  flex: 1;
  min-height: 0;
  margin: 8px 0;
}

.report-chart svg {
  width: 100%;
  height: 100%;
}

.report-tags {
  display: flex;
  gap: 6px;
}

.tag {
  padding: 3px 8px;
  border-radius: 10px;
  font-size: 10px;
  font-weight: 500;
  color: #f05a23;
  background: rgba(240, 90, 35, 0.1);
}
</style>
