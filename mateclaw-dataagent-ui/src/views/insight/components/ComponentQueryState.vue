<template>
  <div class="component-query-state" :class="`is-${status}`" role="status" :aria-live="status === 'loading' ? 'polite' : 'assertive'">
    <span v-if="status === 'loading'" class="query-state-spinner" aria-hidden="true" />
    <span v-else class="query-state-icon" aria-hidden="true">{{ status === 'empty' ? '—' : '!' }}</span>
    <div class="query-state-title">
      {{ title }}
    </div>
    <div v-if="message" class="query-state-message" :title="message">{{ message }}</div>
    <button v-if="status === 'timeout' || status === 'error'" type="button" class="query-state-retry" @click="$emit('retry')">
      {{ retryLabel }}
    </button>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  status: 'loading' | 'empty' | 'timeout' | 'error'
  title: string
  message?: string
  retryLabel: string
}>(), { message: '' })

defineEmits<{ (event: 'retry'): void }>()
</script>

<style scoped>
.component-query-state {
  width: calc(100% - 16px);
  height: calc(100% - 16px);
  min-height: 88px;
  margin: 8px;
  padding: 16px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: var(--radius-md, 8px);
  color: var(--db-text-secondary, #606266);
  background: var(--db-card, #fff);
  text-align: center;
}

.query-state-spinner,
.query-state-icon {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  flex: none;
}

.query-state-spinner {
  border: 2px solid var(--db-border, #e5e7eb);
  border-top-color: var(--db-accent, #409eff);
  border-radius: 50%;
  animation: component-query-spin 0.8s linear infinite;
}

.query-state-icon {
  border-radius: 50%;
  color: var(--db-text-muted, #909399);
  background: var(--db-muted, #f3f4f6);
  font-weight: 700;
}

.is-timeout,
.is-error {
  background: color-mix(in srgb, var(--db-danger, #f56c6c) 4%, var(--db-card, #fff));
}

.is-timeout .query-state-icon,
.is-error .query-state-icon {
  color: var(--db-danger, #f56c6c);
  background: color-mix(in srgb, var(--db-danger, #f56c6c) 12%, transparent);
}

.query-state-title {
  font-size: 13px;
  font-weight: 600;
}

.query-state-message {
  max-width: 100%;
  overflow: hidden;
  color: var(--db-text-muted, #909399);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.query-state-retry {
  border: 0;
  padding: 4px 10px;
  border-radius: 4px;
  color: var(--db-accent, #409eff);
  background: transparent;
  cursor: pointer;
  font: inherit;
}

.query-state-retry:hover {
  background: color-mix(in srgb, var(--db-accent, #409eff) 10%, transparent);
}

@keyframes component-query-spin {
  to { transform: rotate(360deg); }
}
</style>
