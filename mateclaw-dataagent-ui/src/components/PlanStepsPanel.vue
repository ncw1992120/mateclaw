<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import type { PlanMeta } from '@/types'

const props = defineProps<{
  plan: PlanMeta
  isGenerating: boolean
}>()

const collapsed = ref(true)

/** 已完成步骤数 */
const completedCount = computed(() =>
  props.plan.stepResults?.filter(r => r?.status === 'completed').length || 0
)

/** 失败步骤数 */
const failedCount = computed(() =>
  props.plan.stepResults?.filter(r => r?.status === 'failed').length || 0
)

/** 所有步骤是否完成 */
const allDone = computed(() =>
  completedCount.value === props.plan.steps.length && !props.isGenerating
)

/** 计划是否有失败步骤 */
const planHasFailed = computed(() =>
  failedCount.value > 0 && !props.isGenerating
)

/** 计划终态标签 */
const planStatusLabel = computed(() => {
  if (props.plan.planStatus === 'completed') return 'completed'
  if (props.plan.planStatus === 'failed') return 'failed'
  if (allDone.value) return 'completed'
  if (planHasFailed.value) return 'failed'
  return ''
})

type StepStatus = 'pending' | 'running' | 'completed' | 'failed'

/** 每个步骤的状态映射 */
const stepStatuses = computed<StepStatus[]>(() =>
  props.plan.steps.map((_, i) => {
    const result = props.plan.stepResults?.[i]
    if (result?.status === 'completed') return 'completed'
    if (result?.status === 'failed') return 'failed'
    if (i === props.plan.currentStep && props.isGenerating) return 'running'
    return 'pending'
  })
)

const expandedSteps = reactive(new Set<number>())

function toggleStep(index: number) {
  const result = props.plan.stepResults?.[index]
  if (!result?.result) return
  if (expandedSteps.has(index)) {
    expandedSteps.delete(index)
  } else {
    expandedSteps.add(index)
  }
}

/** 截断步骤结果 */
function truncateResult(text: string, max: number): string {
  if (!text || text.length <= max) return text
  return text.slice(0, max) + '...'
}
</script>

<template>
  <div class="plan-panel" :class="{
    'is-done': planStatusLabel === 'completed',
    'is-failed': planStatusLabel === 'failed',
  }">
    <!-- 标题栏 -->
    <div class="plan-panel__toggle" @click="collapsed = !collapsed">
      <span class="plan-panel__icon">
        <span v-if="isGenerating && planStatusLabel !== 'completed' && planStatusLabel !== 'failed'" class="spin-icon">⟳</span>
        <span v-else-if="planStatusLabel === 'failed'" class="icon-failed">✕</span>
      </span>
      <span class="plan-panel__label">{{ $t('chat.executionPlan') }}</span>
      <span class="plan-panel__count">
        <template v-if="planStatusLabel === 'failed'">
          {{ completedCount }}/{{ plan.steps.length }} {{ $t('chat.planDone') }}, {{ failedCount }} {{ $t('chat.planFailed') }}
        </template>
        <template v-else>
          {{ completedCount }}/{{ plan.steps.length }} {{ $t('chat.planDone') }}
        </template>
      </span>
      <span v-if="planStatusLabel === 'failed'" class="plan-panel__badge" :class="planStatusLabel">
        {{ $t('chat.planStatusFailed') }}
      </span>
      <span class="plan-panel__arrow" :class="{ 'is-open': !collapsed }">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 6 15 12 9 18"/></svg>
      </span>
    </div>

    <!-- 步骤列表 -->
    <Transition name="plan-slide">
      <div v-if="!collapsed" class="plan-panel__body">
        <div
          v-for="(step, i) in plan.steps"
          :key="i"
          class="plan-step"
          :class="{
            'is-pending': stepStatuses[i] === 'pending',
            'is-running': stepStatuses[i] === 'running',
            'is-completed': stepStatuses[i] === 'completed',
            'is-failed': stepStatuses[i] === 'failed',
          }"
          @click="toggleStep(i)"
        >
          <div class="plan-step__header">
            <span class="plan-step__status">
              <span v-if="stepStatuses[i] === 'running'" class="spin-icon">⟳</span>
              <span v-else-if="stepStatuses[i] === 'completed'" class="icon-done">✓</span>
              <span v-else-if="stepStatuses[i] === 'failed'" class="icon-failed">✕</span>
              <span v-else class="plan-step__dot"></span>
            </span>
            <span class="plan-step__index">{{ i + 1 }}.</span>
            <span class="plan-step__text" :title="step">{{ step }}</span>
            <span
              v-if="plan.stepResults?.[i]?.result"
              class="plan-step__arrow"
              :class="{ 'is-open': expandedSteps.has(i) }"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"/></svg>
            </span>
          </div>

          <!-- 步骤结果预览 -->
          <Transition name="plan-slide">
            <div v-if="expandedSteps.has(i) && plan.stepResults?.[i]?.result" class="plan-step__result">
              <pre>{{ truncateResult(plan.stepResults[i].result, 500) }}</pre>
            </div>
          </Transition>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.plan-panel {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  width: fit-content;
  max-width: 100%;
  background: var(--theme-surface-hover);
  border: 1px solid var(--theme-border);
  border-radius: 12px;
  margin-bottom: 10px;
  overflow: hidden;
  transition: border-color 0.3s;
}
.plan-panel.is-failed {
  border-color: var(--el-color-warning-light-5, #f0c78a);
}

.plan-panel__toggle {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 10px;
  background: transparent;
  cursor: pointer;
  user-select: none;
  font-size: 12px;
  line-height: 1.4;
  color: var(--theme-text-muted);
  text-align: left;
  transition: all 0.15s;
  white-space: nowrap;
}
.plan-panel__toggle:hover {
  color: var(--theme-text-secondary);
  background: var(--theme-border);
}

.plan-panel__icon {
  display: flex;
  align-items: center;
  color: var(--muted);
}
.plan-panel.is-done .plan-panel__icon {
  color: var(--el-color-success, #67c23a);
}
.plan-panel.is-failed .plan-panel__icon {
  color: var(--el-color-warning, #e6a23c);
}

.spin-icon {
  display: inline-block;
  animation: spin 1s linear infinite;
  font-size: 12px;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.icon-done {
  color: var(--el-color-success, #67c23a);
  font-size: 12px;
  font-weight: 700;
}
.icon-failed {
  color: var(--el-color-warning, #e6a23c);
  font-size: 12px;
  font-weight: 700;
}

.plan-panel__label {
  font-weight: 500;
}

.plan-panel__count {
  font-size: 11px;
  color: var(--muted);
  font-weight: 400;
}

.plan-panel__badge {
  font-size: 10px;
  padding: 0 5px;
  border-radius: 3px;
  font-weight: 500;
  color: #fff;
  line-height: 1.5;
}
.plan-panel__badge.completed {
  background: var(--el-color-success-light-5, #a4da89);
}
.plan-panel__badge.failed {
  background: var(--el-color-warning-light-5, #f0c78a);
}

.plan-panel__arrow {
  margin-left: 1px;
  display: inline-flex;
  width: 9px;
  height: 9px;
  color: var(--muted);
  transition: transform 0.2s;
}
.plan-panel__arrow.is-open {
  transform: rotate(90deg);
}

.plan-panel__body {
  width: 100%;
  max-width: 100%;
  padding: 0 12px 12px;
}

.plan-step {
  transition: background 0.15s;
  cursor: pointer;
  border-top: 1px solid var(--theme-border);
}
.plan-step:first-child {
  border-top: none;
}
.plan-step:hover {
  background: var(--theme-border);
}

.plan-step__header {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 2px;
  font-size: 12px;
  user-select: none;
}

.plan-step__status {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  flex-shrink: 0;
}
.is-completed .plan-step__status { color: var(--el-color-success, #67c23a); }
.is-running .plan-step__status { color: var(--muted); }
.is-failed .plan-step__status { color: var(--el-color-warning, #e6a23c); }

.plan-step__dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  border: 1.5px solid var(--muted);
  background: transparent;
}

.plan-step__index {
  font-size: 11px;
  color: var(--muted);
  font-weight: 500;
  flex-shrink: 0;
}

.plan-step__text {
  flex: 1;
  min-width: 0;
  color: var(--theme-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.is-running .plan-step__text {
  color: var(--theme-text-secondary);
  font-weight: 500;
}
.is-completed .plan-step__text {
  color: var(--muted);
}
.is-failed .plan-step__text {
  color: var(--el-color-warning, #e6a23c);
  font-weight: 500;
}

.plan-step__arrow {
  flex-shrink: 0;
  color: var(--muted);
  transition: transform 0.2s;
  display: inline-flex;
  width: 9px;
  height: 9px;
}
.plan-step__arrow.is-open {
  transform: rotate(90deg);
}

.plan-step__result {
  padding: 0 0 2px 22px;
}
.plan-step__result pre {
  margin: 0;
  padding: 4px 6px;
  background: var(--theme-bg);
  border-radius: 4px;
  border: 1px solid var(--theme-border);
  font-family: 'SF Mono', 'Menlo', 'Consolas', monospace;
  font-size: 11px;
  line-height: 1.4;
  color: var(--theme-text-muted);
  max-height: 160px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
.is-failed .plan-step__result pre {
  border-color: var(--el-color-warning-light-7, #f5dab1);
  background: var(--el-color-warning-light-9, #fdf6ec);
}

/* Transitions */
.plan-slide-enter-active, .plan-slide-leave-active {
  transition: all 0.2s ease;
}
.plan-slide-enter-from, .plan-slide-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
