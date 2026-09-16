<template>
  <div class="card-canvas">
    <div class="canvas-tip">仪表盘画布 · 点击任意卡片即进入其「属性配置」（当前卡片即配置目标）</div>
    <div class="card-grid">
      <div
        v-for="card in state.cards"
        :key="card.id"
        class="dash-card"
        :class="{ active: card.id === state.activeCardId }"
        @click="selectCard(card.id)"
      >
        <div class="card-head">
          <span class="card-type">{{ typeLabel(card.type) }}</span>
          <span v-if="card.type === 'kpi'" class="card-flags">
            <em v-if="card.multiMetric">多指标</em>
            <em v-if="card.multiTab">多TAB</em>
          </span>
        </div>
        <div class="card-name">{{ card.title }}</div>
        <div class="card-preview">
          <!-- KPI/指标卡示意 -->
          <template v-if="card.type === 'kpi'">
            <div class="kpi-num">1,204</div>
            <div class="kpi-sub">策略下发总数</div>
          </template>
          <!-- 表格卡示意 -->
          <template v-else-if="card.type === 'table'">
            <div class="fake-row" v-for="i in 3" :key="i"><span></span><span></span><span></span></div>
          </template>
          <!-- 图表卡示意 -->
          <template v-else>
            <div class="fake-chart">📈 趋势图占位</div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useInsight } from '../useInsight'
const { state, selectCard } = useInsight()

function typeLabel(t: string) {
  return t === 'kpi' ? 'KPI/指标卡' : t === 'table' ? '表格卡' : '图表卡'
}
</script>

<style scoped>
.card-canvas {
  flex: 1;
  overflow: auto;
  padding: 20px;
}
.canvas-tip {
  color: var(--db-text-muted);
  font-size: 12px;
  margin-bottom: 14px;
}
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}
.dash-card {
  background: var(--db-card);
  border: 1px solid var(--db-border);
  border-radius: var(--radius-lg);
  padding: 16px;
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
  min-height: 150px;
}
.dash-card:hover {
  border-color: var(--db-border-strong);
  box-shadow: var(--shadow-card);
  transform: translateY(-1px);
}
.dash-card.active {
  border-color: var(--db-accent);
  box-shadow: 0 0 0 2px var(--db-accent-light);
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.card-type {
  font-size: 12px;
  color: var(--db-accent);
  font-weight: 600;
}
.card-flags em {
  font-style: normal;
  font-size: 11px;
  background: var(--db-accent-light);
  color: var(--db-accent);
  padding: 1px 6px;
  border-radius: var(--radius-sm);
  margin-left: 4px;
}
.card-name {
  font-weight: 600;
  color: var(--db-text);
  margin-bottom: 12px;
}
.card-preview {
  color: var(--db-text-secondary);
}
.kpi-num {
  font-size: 28px;
  font-weight: 700;
  color: var(--db-text);
}
.kpi-sub {
  font-size: 12px;
  color: var(--db-text-muted);
}
.fake-row {
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
}
.fake-row span {
  height: 8px;
  background: var(--db-muted);
  border-radius: 4px;
  flex: 1;
}
.fake-row span:first-child {
  flex: 0 0 40%;
}
.fake-chart {
  text-align: center;
  padding-top: 24px;
  color: var(--db-text-muted);
}
</style>
