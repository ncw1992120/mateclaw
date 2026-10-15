<script setup lang="ts">
import InlineHelp from './InlineHelp.vue'

defineProps<{
  title: string
  help?: string
}>()
</script>

<template>
  <section class="insight-property-section">
    <div class="insight-property-section__header">
      <h3 class="insight-property-section__title">{{ title }}</h3>
      <InlineHelp
        v-if="help"
        :label="title"
        :content="help"
      />
    </div>
    <div class="insight-property-section__body">
      <slot />
    </div>
  </section>
</template>
