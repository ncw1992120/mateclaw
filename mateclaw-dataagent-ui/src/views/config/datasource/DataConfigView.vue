<template>
  <div class="data-config-page">
    <div class="data-subnav">
      <span class="data-subnav-title">数据源</span>
    </div>
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
.data-subnav { display: flex; align-items: center; gap: 16px; padding: 14px 24px; border-bottom: 1px solid var(--theme-border); background: var(--theme-surface); }
.data-subnav-title { font-weight: 600; color: var(--theme-text); }
</style>
