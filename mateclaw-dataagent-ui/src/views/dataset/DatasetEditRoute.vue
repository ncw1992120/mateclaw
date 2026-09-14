<template>
  <DatasetEdit
    :dataset-id="datasetId"
    :mode="mode"
    @back="goList"
    @saved="goList"
  />
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import DatasetEdit from './DatasetEdit.vue'

defineProps<{ datasetId?: string; mode?: 'config' | 'preview' }>()
const router = useRouter()
function goList(): void { router.push('/datasets') }
</script>
