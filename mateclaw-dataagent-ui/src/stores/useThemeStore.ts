import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export type ThemeMode = 'light' | 'warm' | 'eye-care' | 'dark' | 'system'

const STORAGE_KEY = 'theme-mode'

/** 用户界面主题状态管理 */
export const useThemeStore = defineStore('theme', () => {
  const saved = localStorage.getItem(STORAGE_KEY) as ThemeMode | null
  const theme = ref<ThemeMode>(isValidTheme(saved) ? saved : 'light')

  const effectiveTheme = computed<Exclude<ThemeMode, 'system'>>(() => {
    if (theme.value !== 'system') {
      return theme.value
    }
    return prefersDark() ? 'dark' : 'light'
  })

  /** 当前是否处于暗色模式 */
  const isDark = computed(() => effectiveTheme.value === 'dark')

  /**
   * 应用主题到文档根元素。
   * 项目自定义主题走 html[data-theme]，但 Element Plus 只认 html.dark 类
   * （配合 element-plus/theme-chalk/dark/css-vars.css 生效），故暗色时同步切换该类，
   * 让 EP 组件（dialog/input/select/message 等）自动进入暗色变量集。
   */
  function applyTheme(): void {
    const html = document.documentElement
    html.setAttribute('data-theme', effectiveTheme.value)
    html.classList.toggle('dark', effectiveTheme.value === 'dark')
  }

  /**
   * 设置主题模式
   */
  function setTheme(mode: ThemeMode): void {
    theme.value = mode
    localStorage.setItem(STORAGE_KEY, mode)
    applyTheme()
  }

  /**
   * 初始化主题：恢复用户选择并监听系统主题变化
   */
  function init(): void {
    applyTheme()
    if (theme.value === 'system') {
      window
        .matchMedia('(prefers-color-scheme: dark)')
        .addEventListener('change', applyTheme)
    }
  }

  return {
    theme,
    effectiveTheme,
    isDark,
    setTheme,
    applyTheme,
    init,
  }
})

function isValidTheme(value: string | null): value is ThemeMode {
  return value !== null && ['light', 'warm', 'eye-care', 'dark', 'system'].includes(value)
}

function prefersDark(): boolean {
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}
