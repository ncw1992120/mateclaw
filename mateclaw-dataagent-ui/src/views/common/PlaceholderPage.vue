<template>
  <div class="placeholder-page">
    <div class="placeholder-content">
      <div class="placeholder-icon">🚧</div>
      <h2 class="placeholder-title">{{ title }}</h2>
      <p class="placeholder-desc">{{ t('common.comingSoon') }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const props = withDefaults(defineProps<{
  title?: string
}>(), {
  title: '',
})

const { t } = useI18n()

const displayTitle = computed(() => props.title || t('common.comingSoon'))
</script>

<style scoped>
.placeholder-page {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f7f8fa;
}

.placeholder-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.placeholder-icon {
  font-size: 64px;
  opacity: 0.5;
}

.placeholder-title {
  font-size: 20px;
  font-weight: 600;
  color: #1d2129;
  margin: 0;
}

.placeholder-desc {
  font-size: 14px;
  color: #86909c;
  margin: 0;
}
</style>
