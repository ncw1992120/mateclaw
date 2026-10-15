import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { useUserStore } from '@/stores/useUserStore'

const STORAGE_KEY_PREFIX = 'mateclaw:insight:recent-colors:v1:'
const MAX_RECENT_COLORS = 12

function normalizeColor(value: unknown): string | null {
  if (typeof value !== 'string') return null
  const color = value.trim()
  if (/^#[\da-f]{3}$/i.test(color)) {
    return `#${color.slice(1).split('').map((digit) => digit + digit).join('').toUpperCase()}`
  }
  if (/^#[\da-f]{6}$/i.test(color)) return color.toUpperCase()
  return null
}

function parseColors(value: string | null): string[] {
  if (!value) return []
  try {
    const parsed: unknown = JSON.parse(value)
    if (!Array.isArray(parsed)) return []
    return parsed
      .map(normalizeColor)
      .filter((color): color is string => color !== null)
      .filter((color, index, colors) => colors.indexOf(color) === index)
      .slice(0, MAX_RECENT_COLORS)
  } catch {
    return []
  }
}

export const useInsightColorHistoryStore = defineStore('insightColorHistory', () => {
  const userStore = useUserStore()
  const recentColors = ref<string[]>([])

  function accountStorageKey(): string | null {
    const userId = userStore.userId
    return userId === null || userId === undefined || userId === ''
      ? null
      : `${STORAGE_KEY_PREFIX}${userId}`
  }

  function reload(): void {
    const key = accountStorageKey()
    if (!key) {
      recentColors.value = []
      return
    }
    try {
      recentColors.value = parseColors(localStorage.getItem(key))
    } catch {
      recentColors.value = []
    }
  }

  function recordColor(value: unknown): void {
    const color = normalizeColor(value)
    if (!color) return
    recentColors.value = [
      color,
      ...recentColors.value.filter((recentColor) => recentColor !== color),
    ].slice(0, MAX_RECENT_COLORS)

    const key = accountStorageKey()
    if (!key) return
    try {
      localStorage.setItem(key, JSON.stringify(recentColors.value))
    } catch {
      // Color editing remains available when browser storage is unavailable or full.
    }
  }

  watch(() => userStore.userId, reload, { immediate: true, flush: 'sync' })

  if (typeof window !== 'undefined') {
    window.addEventListener('storage', (event: StorageEvent) => {
      const activeKey = accountStorageKey()
      if (activeKey && (event.key === activeKey || event.key === null)) reload()
    })
  }

  return { recentColors, recordColor, reload }
})
