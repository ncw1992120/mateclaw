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
  /* 透明底：配置壳纸面卡片已提供工作区层级，子视图不再自带灰底 */
  background: transparent;
  height: 100%;
  display: flex;
  flex-direction: column;
}
</style>
