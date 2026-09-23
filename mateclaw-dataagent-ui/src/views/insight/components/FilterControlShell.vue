<template>
  <div
    class="filter-control-widget filter-control-inline"
    :class="[`title-bar-${titleBarStyle ?? 'standard'}`, { 'filter-control-label-hidden': !showLabel }]"
  >
    <div v-if="showLabel" class="filter-control-label">
      <slot name="icon" />
      <span>{{ title }}</span>
    </div>
    <div class="filter-control-content">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'FilterControlShell' })

withDefaults(defineProps<{
  title: string
  titleBarStyle?: string
  showLabel?: boolean
}>(), {
  showLabel: true,
})
</script>

<style scoped>
.filter-control-widget {
  --filter-control-gap: 12px;
  container-type: inline-size;
  width: 100%;
  min-width: 0;
  min-height: 64px;
  display: grid;
  grid-template-columns: minmax(104px, 0.42fr) minmax(0, 1fr);
  align-items: center;
  gap: var(--filter-control-gap);
  padding: 10px 12px;
  box-sizing: border-box;
  background: var(--component-surface, var(--db-card));
  border: 1px solid var(--db-border);
  border-radius: var(--radius-md, 8px);
  transition: border-color 180ms ease, box-shadow 180ms ease;
}

.filter-control-widget:hover {
  border-color: color-mix(in srgb, var(--db-accent) 35%, var(--db-border));
}

.filter-control-label {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--db-text-secondary);
  font-size: 12px;
  font-weight: 600;
  line-height: 20px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.filter-control-label :deep(svg) {
  flex: 0 0 auto;
  width: 16px;
  height: 16px;
}

.filter-control-content {
  min-width: 0;
  width: 100%;
}

.filter-control-label-hidden {
  grid-template-columns: minmax(0, 1fr);
}

@container (max-width: 320px) {
  .filter-control-widget {
    grid-template-columns: minmax(0, 1fr);
    gap: 6px;
    padding: 8px 10px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .filter-control-widget {
    transition: none;
  }
}
</style>
