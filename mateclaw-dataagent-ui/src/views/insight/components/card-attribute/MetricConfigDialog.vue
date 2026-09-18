<template>
  <el-dialog v-model="ui.metricConfig.visible" title="指标配置" width="920px" :close-on-click-modal="false">
    <div class="mc-body">
      <template v-if="metrics.length">
        <div class="mc-head">
          <span>显示</span>
          <span>展示列名</span>
          <span>指标值</span>
          <span>单位</span>
          <span>辅助说明</span>
          <span>样式</span>
          <span>同步</span>
        </div>
        <div v-for="m in metrics" :key="m.fieldKey" class="mc-row">
          <el-switch v-model="m.visible" size="small" />
          <el-input v-model="m.displayName" size="small" placeholder="展示列名" />
          <el-input :model-value="m.fieldKey" size="small" readonly class="mc-readonly" />
          <el-input v-model="m.unit" size="small" placeholder="如：万元" />
          <el-input v-model="m.helperText" size="small" placeholder="辅助说明" />
          <button class="mc-style-btn" :title="`配置「${m.displayName || m.fieldKey}」字段样式`" @click="openMetricStyle(m.fieldKey)">:</button>
          <el-button size="small" text type="primary" @click="syncKpiMetricStyles(m.fieldKey)">同步</el-button>
        </div>
        <p class="hint">
          指标由最终结果集字段自动投影生成（{{ metrics.length }} 个），不支持手工新增 / 删除；
          「指标值」为结果集字段（只读，运行时取值）。「同步」把该指标的整套字段样式复制到所有指标。
        </p>
      </template>

      <!-- 空态：字段只能来自结果集，不提供手工新增入口 -->
      <div v-else class="mc-empty">
        <p>暂无指标：请先在「数据集配置」中完成数据源配置，指标将由最终结果集字段自动投影生成。</p>
        <el-button size="small" :loading="rebuilding" @click="rebuild">重新投影</el-button>
      </div>
    </div>

    <template #footer>
      <el-button @click="ui.metricConfig.visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useInsight } from './useInsight'

const { state, openMetricStyle, syncKpiMetricStyles, rebuildKpiMetrics } = useInsight()
const ui = state.ui

const metrics = computed(() => state.kpiMetrics)
const rebuilding = ref(false)

async function rebuild() {
  rebuilding.value = true
  try {
    rebuildKpiMetrics()
  } finally {
    rebuilding.value = false
  }
}
</script>

<style scoped>
.mc-body {
  min-height: 80px;
}
.mc-head,
.mc-row {
  display: grid;
  grid-template-columns: 48px 1.2fr 1.2fr 0.7fr 1.2fr 44px 64px;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
.mc-head {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.mc-row .el-switch {
  justify-content: center;
}
.mc-readonly :deep(.el-input__wrapper) {
  background: var(--el-fill-color-light);
  box-shadow: none;
}
.mc-readonly :deep(.el-input__inner) {
  color: var(--el-text-color-regular);
  cursor: default;
}
.mc-style-btn {
  width: 24px;
  height: 24px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-size: 14px;
  font-weight: 700;
  line-height: 1;
  cursor: pointer;
  transition: all 0.15s;
}
.mc-style-btn:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 10px 0 0;
  line-height: 1.6;
}
.mc-empty {
  text-align: center;
  padding: 16px 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.mc-empty p {
  margin: 0 0 10px;
}
</style>
