import { onBeforeUnmount, ref, type Ref } from 'vue'

/**
 * 创建一个带有防抖能力的函数包装器。
 * - 在 delay 毫秒内的多次调用只会执行最后一次
 * - 适用于"防重、防抖、避免重复提交"等场景
 *   例如资源密集型的同步按钮、搜索框输入联想等。
 *
 * @param fn 需要被防抖的原始函数（支持同步与异步）
 * @param delay 防抖延迟（毫秒），默认 600ms
 * @returns 包装后的函数以及控制句柄
 */
export interface DebouncedFnHandle<TArgs extends unknown[], TReturn> {
  /** 调用包装后的函数（受防抖控制） */
  invoke: (...args: TArgs) => void
  /** 立即执行被防抖函数（跳过等待），并重置定时器 */
  flush: () => void
  /** 取消尚未触发的执行 */
  cancel: () => void
  /** 是否处于防抖等待中（响应式 ref） */
  readonly pending: Ref<boolean>
}

export function useDebouncedFn<TArgs extends unknown[], TReturn>(
  fn: (...args: TArgs) => TReturn | Promise<TReturn>,
  delay: number = 600,
): DebouncedFnHandle<TArgs, TReturn> {
  let timer: ReturnType<typeof setTimeout> | null = null
  let lastArgs: TArgs | null = null
  let isPending = false

  const pendingRef = ref(false)

  function clearTimer(): void {
    if (timer !== null) {
      clearTimeout(timer)
      timer = null
    }
  }

  function runNow(): void {
    if (lastArgs === null) {
      return
    }
    const args = lastArgs
    lastArgs = null
    clearTimer()
    isPending = false
    pendingRef.value = false
    fn(...args)
  }

  function invoke(...args: TArgs): void {
    lastArgs = args
    isPending = true
    pendingRef.value = true
    clearTimer()
    timer = setTimeout(runNow, delay)
  }

  function flush(): void {
    if (isPending) {
      runNow()
    }
  }

  function cancel(): void {
    clearTimer()
    lastArgs = null
    isPending = false
    pendingRef.value = false
  }

  onBeforeUnmount(cancel)

  return {
    invoke,
    flush,
    cancel,
    get pending() {
      return pendingRef
    },
  }
}
