<script setup lang="ts">
defineProps<{
  description: string
  actionLabel?: string
}>()

const emit = defineEmits<{
  action: []
}>()
</script>

<template>
  <div class="insight-editor-empty-state">
    <div class="insight-editor-empty-state__icon" aria-hidden="true">—</div>
    <p class="insight-editor-empty-state__description">{{ description }}</p>
    <button
      v-if="actionLabel"
      type="button"
      class="insight-editor-empty-state__action"
      @click="emit('action')"
    >
      {{ actionLabel }}
    </button>
  </div>
</template>
