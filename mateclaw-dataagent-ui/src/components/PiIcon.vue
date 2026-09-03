<template>
  <span class="pi-icon" v-html="svgContent" />
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  /** pixelarticons 标识符，如 "pi:clipboard-note" */
  name: string
  /** 图标尺寸（px），默认 16 */
  size?: number
}>()

/** 使用 import.meta.glob 预加载所有 pixelarticons SVG 为原始字符串 */
const svgModules = import.meta.glob(
  '../../node_modules/pixelarticons/svg/*.svg',
  { query: '?raw', import: 'default', eager: true },
) as Record<string, string>

/** 从路径中提取图标名（去掉扩展名） */
const svgMap = new Map<string, string>()
for (const [path, raw] of Object.entries(svgModules)) {
  const fileName = path.split('/').pop()!.replace(/\.svg$/, '')
  svgMap.set(fileName, raw)
}

const svgContent = computed(() => {
  const raw = props.name
  // 解析 "pi:xxx" 格式
  let iconName = raw
  if (raw.startsWith('pi:')) {
    iconName = raw.slice(3)
  }
  const svg = svgMap.get(iconName)
  if (!svg) return ''
  // 替换尺寸属性以适配 size prop
  const sz = props.size ?? 16
  return svg
    .replace(/width="\d+"/, `width="${sz}"`)
    .replace(/height="\d+"/, `height="${sz}"`)
})
</script>

<style scoped>
.pi-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  vertical-align: middle;
  line-height: 1;
}

.pi-icon :deep(svg) {
  display: block;
}
</style>
