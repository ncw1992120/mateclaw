<script setup lang="ts">
import { ref, computed } from 'vue'

const props = withDefaults(defineProps<{
  label: string
  side?: 'left' | 'right'
  /** Default vertical position as percentage (0-100). */
  defaultTopPercent?: number
}>(), {
  side: 'left',
  defaultTopPercent: 50,
})

const emit = defineEmits<{
  expand: []
}>()

/** Current top offset in px; null means use the default percentage. */
const dragTopPx = ref<number | null>(null)

const isDragging = ref(false)
let pointerStartY = 0
let elementStartTop = 0
let totalDy = 0

const topStyle = computed(() => {
  if (dragTopPx.value !== null) return `${dragTopPx.value}px`
  return `${props.defaultTopPercent}%`
})

function onPointerDown(e: PointerEvent) {
  e.preventDefault()
  const btn = e.currentTarget as HTMLElement
  btn.setPointerCapture(e.pointerId)
  isDragging.value = true
  pointerStartY = e.clientY
  totalDy = 0

  // Compute current top in px from the rendered position
  const rect = btn.getBoundingClientRect()
  const parent = btn.offsetParent as HTMLElement | null
  if (parent) {
    const parentRect = parent.getBoundingClientRect()
    elementStartTop = rect.top - parentRect.top
  } else {
    elementStartTop = rect.top
  }
}

function onPointerMove(e: PointerEvent) {
  if (!isDragging.value) return
  const dy = e.clientY - pointerStartY
  totalDy = Math.abs(dy)

  const btn = e.currentTarget as HTMLElement
  const parent = btn.offsetParent as HTMLElement
  if (!parent) return

  const parentH = parent.clientHeight
  const btnH = btn.offsetHeight
  const minTop = 8
  const maxTop = parentH - btnH - 8

  let newTop = elementStartTop + dy
  newTop = Math.max(minTop, Math.min(maxTop, newTop))
  dragTopPx.value = newTop
}

function onPointerUp(e: PointerEvent) {
  if (!isDragging.value) return
  isDragging.value = false
  const btn = e.currentTarget as HTMLElement
  btn.releasePointerCapture(e.pointerId)

  // If barely moved → treat as click → expand
  if (totalDy < 5) {
    emit('expand')
  }
}
</script>

<template>
  <button
    type="button"
    class="panel-float-btn"
    :class="[side, { dragging: isDragging }]"
    :style="{ top: topStyle }"
    :title="`展开${label}`"
    @pointerdown="onPointerDown"
    @pointermove="onPointerMove"
    @pointerup="onPointerUp"
  >
    <span class="float-btn-icon">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
        <polyline v-if="side === 'left'" points="9 18 15 12 9 6"/>
        <polyline v-else points="15 18 9 12 15 6"/>
      </svg>
    </span>
    <span class="float-btn-label">{{ label }}</span>
    <span class="float-btn-drag-hint" aria-hidden="true">
      <svg width="8" height="12" viewBox="0 0 8 12" fill="currentColor" opacity=".35">
        <circle cx="2" cy="2" r="1.2"/><circle cx="6" cy="2" r="1.2"/>
        <circle cx="2" cy="6" r="1.2"/><circle cx="6" cy="6" r="1.2"/>
        <circle cx="2" cy="10" r="1.2"/><circle cx="6" cy="10" r="1.2"/>
      </svg>
    </span>
  </button>
</template>

<style scoped>
.panel-float-btn {
  position: absolute;
  z-index: 50;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 10px 6px;
  width: 34px;
  border-radius: 17px;
  background: var(--db-card);
  border: 1px solid var(--db-border);
  box-shadow: 0 2px 8px rgba(0, 0, 0, .08), 0 1px 2px rgba(0, 0, 0, .04);
  cursor: grab;
  color: var(--db-text-muted);
  transition: color .15s, border-color .15s, box-shadow .15s, transform .15s;
  user-select: none;
  touch-action: none;
  transform: translateY(-50%);
}

.panel-float-btn.left {
  left: 10px;
}

.panel-float-btn.right {
  right: 10px;
}

.panel-float-btn:hover {
  color: var(--db-accent);
  border-color: var(--db-accent);
  box-shadow: 0 4px 14px rgba(0, 0, 0, .1), 0 1px 3px rgba(0, 0, 0, .06);
  transform: translateY(-50%) scale(1.05);
}

.panel-float-btn.dragging {
  cursor: grabbing;
  box-shadow: 0 6px 20px rgba(0, 0, 0, .14);
  transform: translateY(-50%) scale(1.08);
  transition: none;
}

.float-btn-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.float-btn-label {
  writing-mode: vertical-rl;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  line-height: 1;
  white-space: nowrap;
}

.float-btn-drag-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 2px;
}

@media (max-width: 499px) {
  .panel-float-btn {
    display: none;
  }
}
</style>
