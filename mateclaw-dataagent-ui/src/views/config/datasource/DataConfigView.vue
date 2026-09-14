<template>
  <div class="data-config-page">
    <div class="data-subnav">
      <span class="data-subnav-title">数据源</span>
      <button class="dataset-link" @click="router.push('/datasets')">数据集管理</button>
    </div>
    <DatasourceView />
  </div>
</template>

<script setup lang="ts">
import DatasourceView from '@/views/DatasourceView.vue'
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<style scoped>
.data-config-page {
  padding: 0;
  background: var(--theme-bg);
  height: 100%;
  display: flex;
  flex-direction: column;
}
.data-subnav { display: flex; align-items: center; gap: 16px; padding: 14px 24px; border-bottom: 1px solid var(--theme-border); background: var(--theme-surface); }
.data-subnav-title { font-weight: 600; color: var(--theme-text); }
.dataset-link { border: 1px solid var(--theme-border); border-radius: 8px; padding: 7px 12px; color: var(--theme-text); background: var(--theme-surface-hover); cursor: pointer; }
</style>
