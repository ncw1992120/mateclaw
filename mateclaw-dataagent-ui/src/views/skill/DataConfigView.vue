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
  background: #f5f6f8;
  height: 100%;
  display: flex;
  flex-direction: column;
}
</style>
