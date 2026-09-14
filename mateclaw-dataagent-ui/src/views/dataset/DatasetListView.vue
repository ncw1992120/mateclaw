<template>
  <section class="dataset-list-page">
    <header class="page-header">
      <div>
        <h1>数据集</h1>
        <p>管理可复用的数据集，并进入配置或预览。</p>
      </div>
      <div class="header-actions">
        <button class="secondary-btn" @click="router.push('/?nav=config')">返回配置</button>
        <button data-testid="dataset-create" class="primary-btn" @click="router.push('/datasets/new')">新建数据集</button>
      </div>
    </header>
    <div v-if="loading" class="state">正在加载数据集…</div>
    <div v-else-if="datasets.length === 0" class="state">暂无数据集，先创建一个数据集。</div>
    <div v-else class="dataset-grid">
      <article v-for="dataset in datasets" :key="dataset.id" class="dataset-card">
        <div class="card-title-row">
          <h2>{{ dataset.name }}</h2>
          <span class="status">{{ dataset.status || 'READY' }}</span>
        </div>
        <p class="meta">{{ dataset.datasourceName || '未命名数据源' }} · {{ dataset.rowCount ?? 0 }} 行 · {{ dataset.columnCount ?? 0 }} 个字段</p>
        <div class="card-actions">
          <button :data-testid="`dataset-edit-${dataset.id}`" class="secondary-btn" @click="router.push(`/datasets/${dataset.id}/edit`)">编辑 / 预览</button>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as datasetApi from '@/api/dataset'
import type { Dataset } from '@/types'

const router = useRouter()
const datasets = ref<Dataset[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    datasets.value = await datasetApi.list() as unknown as Dataset[]
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.dataset-list-page { min-height: 100%; padding: 32px; background: var(--theme-bg); color: var(--theme-text); box-sizing: border-box; }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 24px; margin-bottom: 24px; }
.header-actions { display: flex; gap: 8px; align-items: center; }
h1 { margin: 0 0 8px; font-size: 24px; }
.page-header p, .meta { margin: 0; color: var(--theme-text-muted); }
.dataset-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
.dataset-card { padding: 20px; border: 1px solid var(--theme-border); border-radius: 12px; background: var(--theme-surface); }
.card-title-row { display: flex; justify-content: space-between; gap: 12px; align-items: center; }
h2 { margin: 0; font-size: 17px; }
.status { color: #237804; font-size: 12px; }
.card-actions { margin-top: 20px; }
button { border: 0; border-radius: 8px; padding: 9px 14px; cursor: pointer; font-size: 14px; }
.primary-btn { color: #fff; background: var(--main-orange); }
.secondary-btn { color: var(--theme-text); background: var(--theme-surface-hover); border: 1px solid var(--theme-border); }
.state { padding: 48px; text-align: center; color: var(--theme-text-muted); }
</style>
