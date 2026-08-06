<script setup lang="ts">
import { ref, computed } from 'vue'
import type { DelegationNode } from '@/types'

// 递归渲染委派调用树中的一个子 agent 节点（depth >= 2）。
// depth-1 的子 agent 复用 ChatView 中的 tool_call segment 渲染，本组件在每个 depth-1
// segment 的 childTimeline.children 中被挂载，并对其自身 children 递归。
// 移植自 mateclaw-ui/src/components/chat/DelegationNodeView.vue，适配 dataagent-ui 样式变量。
const props = defineProps<{ node: DelegationNode }>()

const expanded = ref(props.node.status === 'running')

const isRunning = computed(() => props.node.status === 'running')
const isError = computed(() => props.node.status === 'error')
const isSuccess = computed(() => props.node.status === 'completed')
const isStalled = computed(() => isRunning.value && !!props.node.stale)
// 异步委派（delegateAsync）：父 agent 不阻塞，结果稍后通过 task_output 取回
const isAsync = computed(() => !!props.node.async)

const plan = computed(() => props.node.plan)
const tools = computed(() => props.node.tools || [])
const children = computed(() => props.node.children || [])
const resultPreview = computed(() => {
  const r = props.node.result || ''
  return r.length <= 600 ? r : r.slice(0, 600) + '\n... [截断]'
})

const hasBody = computed(() =>
  !!plan.value || tools.value.length > 0 || children.value.length > 0 || !!props.node.result
)

const progress = computed(() => {
  const p = plan.value
  if (p?.steps?.length) {
    const done = p.stepResults?.filter(r => r?.status === 'completed').length || 0
    return `${done}/${p.steps.length}`
  }
  const n = tools.value.length
  return n ? `${n} ${n === 1 ? 'tool' : 'tools'}` : ''
})

function stepStatus(i: number): 'pending' | 'running' | 'completed' {
  const p = plan.value
  if (!p) return 'pending'
  if (p.stepResults?.[i]?.status === 'completed') return 'completed'
  if (i === p.currentStep) return 'running'
  return 'pending'
}

function toggle() {
  if (hasBody.value) expanded.value = !expanded.value
}
</script>

<template>
  <div class="deleg-node" :class="{ 'is-running': isRunning, 'is-error': isError, 'is-success': isSuccess }">
    <div class="deleg-node__header" @click="toggle">
      <span class="deleg-node__status">
        <span v-if="isAsync" class="deleg-node__async" :title="$t('chat.subagentAsync')">⏱</span>
        <span v-else-if="isRunning" class="spin-icon">⟳</span>
        <span v-else-if="isSuccess" class="icon-done">✓</span>
        <span v-else class="icon-failed">✕</span>
      </span>
      <span class="deleg-node__icon">↳</span>
      <span class="deleg-node__name">{{ node.agentName }}</span>
      <span v-if="progress" class="deleg-node__badge">{{ progress }}</span>
      <span v-if="isStalled" class="deleg-node__stale" :title="$t('chat.subagentStalled')">⚠</span>
      <span
        v-if="hasBody"
        class="deleg-node__arrow"
        :class="{ 'is-open': expanded }"
      ><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"/></svg></span>
    </div>

    <Transition name="deleg-slide">
      <div v-if="expanded && hasBody" class="deleg-node__body">
        <!-- 子 agent 自身的计划步骤 -->
        <div v-if="plan" class="deleg-node__plan">
          <div
            v-for="(step, i) in plan.steps"
            :key="i"
            class="deleg-node__step"
            :class="`is-${stepStatus(i)}`"
          >
            <span v-if="stepStatus(i) === 'running'" class="spin-icon">⟳</span>
            <span v-else-if="stepStatus(i) === 'completed'" class="icon-done">✓</span>
            <span v-else class="deleg-node__dot"></span>
            <span class="deleg-node__step-text">{{ step }}</span>
          </div>
        </div>

        <!-- 子 agent 调用的工具 -->
        <div v-if="tools.length" class="deleg-node__tools">
          <div
            v-for="(t, i) in tools"
            :key="i"
            class="deleg-node__tool"
            :class="`is-${t.status}`"
          >
            <span v-if="t.status === 'running'" class="spin-icon">⟳</span>
            <span v-else-if="t.status === 'completed'" class="icon-done">✓</span>
            <span v-else class="icon-failed">✕</span>
            <span class="deleg-node__tool-name">{{ t.name }}</span>
          </div>
        </div>

        <!-- 递归更深层子 agent -->
        <DelegationNodeView v-for="c in children" :key="c.subagentId" :node="c" />

        <!-- 最终结果预览 -->
        <pre v-if="node.result" class="deleg-node__result">{{ resultPreview }}</pre>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.deleg-node {
  margin: 2px 0 2px 4px;
  padding-left: 8px;
  border-left: 2px solid var(--theme-border);
}
.deleg-node.is-running { border-left-color: var(--el-color-primary, #409eff); }
.deleg-node.is-success { border-left-color: var(--el-color-success, #67c23a); }
.deleg-node.is-error { border-left-color: var(--el-color-warning, #e6a23c); }

.deleg-node__header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  line-height: 1.9;
  color: var(--theme-text-muted);
  cursor: pointer;
  user-select: none;
}
.deleg-node__status { display: flex; align-items: center; flex-shrink: 0; }
.is-success .deleg-node__status { color: var(--el-color-success, #67c23a); }
.is-error .deleg-node__status { color: var(--el-color-warning, #e6a23c); }
.is-running .deleg-node__status { color: var(--el-color-primary, #409eff); }

.deleg-node__icon { color: var(--muted); flex-shrink: 0; font-size: 11px; }
.deleg-node__name {
  font-weight: 500;
  color: var(--theme-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 180px;
}
.deleg-node__badge {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--muted);
  background: var(--theme-border);
  border-radius: 8px;
  padding: 0 6px;
  line-height: 16px;
}
.deleg-node__stale {
  flex-shrink: 0;
  color: var(--el-color-warning, #e6a23c);
}
.deleg-node__async {
  flex-shrink: 0;
  color: var(--muted);
  font-size: 11px;
}
.deleg-node__arrow {
  flex-shrink: 0;
  color: var(--muted);
  transition: transform 0.2s;
  margin-left: auto;
  display: inline-flex;
  width: 9px;
  height: 9px;
}
.deleg-node__arrow.is-open { transform: rotate(90deg); }

.deleg-node__body { padding: 2px 0 2px 4px; }
.deleg-node__plan {
  margin-bottom: 4px;
  padding-left: 4px;
  border-left: 2px solid var(--theme-border);
}
.deleg-node__step,
.deleg-node__tool {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--muted);
}
.deleg-node__step.is-running,
.deleg-node__tool.is-running { color: var(--el-color-primary, #409eff); }
.deleg-node__step.is-completed,
.deleg-node__tool.is-completed { color: var(--theme-text-muted); }
.deleg-node__tool.is-error { color: var(--el-color-warning, #e6a23c); }
.deleg-node__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--muted);
  flex-shrink: 0;
  margin: 0 3px;
}
.deleg-node__tools { padding-left: 6px; }
.deleg-node__result {
  margin: 4px 0 0;
  padding: 6px 8px;
  background: var(--theme-bg);
  border-radius: 4px;
  border: 1px solid var(--theme-border);
  font-family: 'SF Mono', 'Menlo', 'Consolas', monospace;
  font-size: 11px;
  line-height: 1.5;
  color: var(--theme-text-muted);
  max-height: 200px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.spin-icon {
  display: inline-block;
  animation: deleg-spin 1s linear infinite;
  font-size: 11px;
}
@keyframes deleg-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
.icon-done {
  color: var(--el-color-success, #67c23a);
  font-size: 11px;
  font-weight: 700;
}
.icon-failed {
  color: var(--el-color-warning, #e6a23c);
  font-size: 11px;
  font-weight: 700;
}

.deleg-slide-enter-active, .deleg-slide-leave-active { transition: all 0.2s ease; }
.deleg-slide-enter-from, .deleg-slide-leave-to { opacity: 0; transform: translateY(-3px); }
</style>
