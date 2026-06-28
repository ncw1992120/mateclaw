<template>
  <router-view />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useUserStore } from '@/stores/useUserStore'

const userStore = useUserStore()

onMounted(() => {
  // 应用启动时，如果存在 token，从后端恢复用户信息
  if (userStore.token) {
    userStore.fetchCurrentUser()
  }
})
</script>

<style>
#app {
  width: 100%;
  height: 100%;
}
</style>
