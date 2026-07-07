<template>
  <div class="data-config-page">
    <DatasourceView />
  </div>
</template>

<script setup lang="ts">
import DatasourceView from '@/views/DatasourceView.vue'
</script>

<style scoped>
.data-config-page {
  padding: 0;
  background: var(--theme-bg);
  height: 100%;
  display: flex;
  flex-direction: column;
}
</style>
