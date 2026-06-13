import { ref, watch, type Ref } from 'vue'

/**
 * 创建一个 ref，自动将其值同步到 localStorage。
 * 用于在页面刷新后保持 UI 状态（例如选中的菜单项、激活的 Tab 等）。
 *
 * @param key localStorage 中的键名
 * @param defaultValue 当 localStorage 中无值或值非法时使用的默认值
 * @param validate 用于校验持久化值是否合法的函数；返回 false 时回退到默认值
 */
export function usePersistedRef<T extends string>(
  key: string,
  defaultValue: T,
  validate?: (value: string) => boolean,
): Ref<T> {
  const stored = localStorage.getItem(key)
  const initial = stored !== null && (!validate || validate(stored)) ? (stored as T) : defaultValue
  const state = ref(initial) as Ref<T>

  watch(state, (value) => {
    localStorage.setItem(key, value)
  })

  return state
}
