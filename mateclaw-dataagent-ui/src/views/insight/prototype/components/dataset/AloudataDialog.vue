<template>
  <el-dialog v-model="ui.aloudata.visible" :title="aloudataTitle" width="560px" :close-on-click-modal="false">
    <!-- 指标&维度 -->
    <template v-if="ui.aloudata.mode === 'metric-dim'">
      <p class="hint">配置指标、维度和查询筛选条件；停止修改 3 秒后自动生成数据集表单，无额外确认按钮。</p>
      <div class="block">
        <div class="block-title">指标</div>
        <el-checkbox-group v-model="ui.aloudata.metrics" @change="onAuto">
          <el-checkbox v-for="m in MOCK_ALOUDATA_METRICS" :key="m" :label="m" />
        </el-checkbox-group>
      </div>
      <div class="block">
        <div class="block-title">维度</div>
        <el-checkbox-group v-model="ui.aloudata.dims" @change="onAuto">
          <el-checkbox v-for="d in MOCK_ALOUDATA_DIMS" :key="d" :label="d" />
        </el-checkbox-group>
      </div>
    </template>

    <!-- 指标视图 -->
    <template v-else>
      <p class="hint">只能选择一个有权限的指标视图；停止选择 3 秒后自动生成数据集表单。</p>
      <el-select v-model="ui.aloudata.metricView" placeholder="请选择指标视图" @change="onAuto" style="width: 100%">
        <el-option v-for="v in MOCK_ALOUDATA_METRIC_VIEWS" :key="v" :label="v" :value="v" />
      </el-select>
    </template>

    <template #footer>
      <el-button @click="ui.aloudata.visible = false">取消</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  useInsight,
  MOCK_ALOUDATA_METRICS,
  MOCK_ALOUDATA_DIMS,
  MOCK_ALOUDATA_METRIC_VIEWS,
} from '../../useInsight'

const { state, confirmAloudata } = useInsight()
const ui = state.ui
const aloudataTitle = computed(() =>
  ui.aloudata.mode === 'metric-view' ? 'Aloudata · 指标视图' : 'Aloudata · 指标&维度',
)

// [MOCK] 停止选择/修改 3 秒后自动生成数据集表单（文档 3.2）
let timer: any
function onAuto() {
  clearTimeout(timer)
  timer = setTimeout(() => {
    confirmAloudata()
    ElMessage.success('已根据 Aloudata 配置生成数据集')
  }, 3000)
}
</script>

<style scoped>
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 0 0 12px;
}
.block {
  margin-bottom: 14px;
}
.block-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}
</style>
