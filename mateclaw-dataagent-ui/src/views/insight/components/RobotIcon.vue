<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 48 48"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
  >
    <!-- 头部 -->
    <rect x="8" y="12" width="32" height="28" rx="8" fill="#6366F1" />
    <!-- 左眼 -->
    <rect x="14" y="20" width="8" height="8" rx="4" fill="white" fill-opacity="0.9" />
    <!-- 右眼 -->
    <rect x="26" y="20" width="8" height="8" rx="4" fill="white" fill-opacity="0.9" />
    <!-- 嘴巴 -->
    <rect x="20" y="32" width="8" height="3" rx="1.5" fill="white" fill-opacity="0.6" />
    <!-- 天线杆 -->
    <rect x="18" y="6" width="12" height="4" rx="2" fill="#818CF8" />
    <!-- 天线球 -->
    <circle cx="24" cy="4" r="2" fill="#818CF8" />
  </svg>
</template>

<script setup lang="ts">
defineProps<{
  size?: number | string
}>()

defineOptions({
  name: 'RobotIcon',
})
</script>
