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
      <span class="plan-panel__status" :class="{ 'is-done': planStatusLabel === 'completed', 'is-failed': planStatusLabel === 'failed', 'is-running': isGenerating && planStatusLabel !== 'completed' && planStatusLabel !== 'failed' }">
        <svg v-if="planStatusLabel === 'failed'" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10" fill="currentColor" stroke="none"/><path d="M9 9l6 6M15 9l-6 6" stroke="#fff" stroke-width="2" stroke-linecap="round" fill="none"/></svg>
        <svg v-else-if="planStatusLabel === 'completed'" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10" fill="currentColor" stroke="none"/><path d="M8.5 12.3l2.4 2.4 4.6-5" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/></svg>
        <svg v-else class="spin-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><path d="M21 12a9 9 0 1 1-6.219-8.56"/></svg>
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
              <svg v-if="stepStatuses[i] === 'running'" class="spin-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><path d="M21 12a9 9 0 1 1-6.219-8.56"/></svg>
              <svg v-else-if="stepStatuses[i] === 'completed'" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10" fill="currentColor" stroke="none"/><path d="M8.5 12.3l2.4 2.4 4.6-5" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/></svg>
              <svg v-else-if="stepStatuses[i] === 'failed'" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10" fill="currentColor" stroke="none"/><path d="M9 9l6 6M15 9l-6 6" stroke="#fff" stroke-width="2" stroke-linecap="round" fill="none"/></svg>
              <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="8"/></svg>
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
  align-items: stretch;
  width: 100%;
  max-width: 100%;
  /* Codeon 风格：无外框、无背景，内容平铺 */
  background: transparent;
  border: none;
  border-radius: 0;
  margin-bottom: 8px;
  overflow: visible;
  transition: none;
}
.plan-panel.is-failed {
  border: none;
}

.plan-panel__toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 6px 10px;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  user-select: none;
  font-size: 13px;
  line-height: 1.4;
  color: var(--theme-text);
  text-align: left;
  transition: all 0.15s;
  white-space: nowrap;
}
.plan-panel__toggle:hover {
  background: var(--theme-surface-hover);
}

.plan-panel__status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.plan-panel__status svg {
  width: 16px;
  height: 16px;
  display: block;
}
.plan-panel__status.is-running { color: var(--main-orange); }
.plan-panel__status.is-done    { color: var(--el-color-success, #67c23a); }
.plan-panel__status.is-failed  { color: var(--el-color-warning, #e6a23c); }

.spin-icon {
  display: inline-block;
  animation: spin 1s linear infinite;
  font-size: 14px;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.plan-panel__label {
  font-weight: 500;
}

.plan-panel__count {
  font-size: 12px;
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
  margin-left: auto;
  display: inline-flex;
  width: 9px;
  height: 9px;
  color: var(--muted);
  opacity: 0;
  transition: transform 0.2s, opacity 0.15s ease;
}
.plan-panel__toggle:hover .plan-panel__arrow {
  opacity: 1;
}
.plan-panel__arrow.is-open {
  transform: rotate(90deg);
}

.plan-panel__body {
  width: 100%;
  max-width: 100%;
  padding: 2px 0 4px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.plan-step {
  transition: background 0.15s;
  cursor: pointer;
  border-radius: 6px;
}
.plan-step:hover {
  background: var(--theme-surface-hover);
}

.plan-step__header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 10px;
  font-size: 13px;
  user-select: none;
}

.plan-step__status {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}
.plan-step__status svg {
  width: 16px;
  height: 16px;
  display: block;
}
.is-completed .plan-step__status { color: var(--el-color-success, #67c23a); }
.is-running .plan-step__status { color: var(--main-orange); }
.is-failed .plan-step__status { color: var(--el-color-warning, #e6a23c); }
.is-pending .plan-step__status { color: var(--muted); }


.plan-step__index {
  font-size: 12px;
  color: var(--muted);
  font-weight: 500;
  flex-shrink: 0;
}

.plan-step__text {
  flex: 1;
  min-width: 0;
  color: var(--theme-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.is-running .plan-step__text {
  color: var(--theme-text);
  font-weight: 500;
}
.is-completed .plan-step__text {
  color: var(--theme-text-secondary);
}
.is-failed .plan-step__text {
  color: var(--el-color-warning, #e6a23c);
  font-weight: 500;
}

.plan-step__arrow {
  flex-shrink: 0;
  color: var(--muted);
  opacity: 0;
  transition: transform 0.2s, opacity 0.15s ease;
  display: inline-flex;
  width: 9px;
  height: 9px;
}
.plan-step:hover .plan-step__arrow {
  opacity: 1;
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
