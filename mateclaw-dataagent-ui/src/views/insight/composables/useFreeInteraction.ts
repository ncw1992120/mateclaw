import { onScopeDispose } from 'vue'

/**
 * 自由布局拖拽/缩放的公共交互内核（KpiCardWidget 等使用）。
 *
 * 设计要点（来自组合卡片踩坑总结）：
 * - mousemove 只记录最新鼠标坐标，DOM 更新由 requestAnimationFrame 节流；
 * - **落点提交禁止回读 DOM style**：mouseup 时用「最后一次鼠标坐标 + 调用方传入的
 *   纯函数 compute」重算一次再提交。否则取消 pending rAF 后，末帧位移会丢失
 *   （快速甩动时指标「差一点没到位」）；
 * - compute 必须是纯函数（同一坐标输入 → 同一结果），过程渲染与落点提交共用同一份，
 *   保证两者完全一致。
 */

export interface FreeInteractionPoint {
  x: number
  y: number
}

export interface FreeInteractionHandlers<R> {
  /** 纯计算：最后一次鼠标坐标 → 目标结果（过程渲染与落点提交共用） */
  compute: (last: FreeInteractionPoint) => R
  /** 过程渲染：把结果写到 DOM（rAF 节流内调用，不要在这里写响应式数据） */
  apply: (result: R) => void
  /** 落点提交：把最终结果写回响应式数据（mouseup 时调用一次） */
  commit: (result: R) => void
}

export function useFreeInteraction() {
  let raf = 0
  let last: FreeInteractionPoint | null = null
  let handlers: FreeInteractionHandlers<unknown> | null = null

  function onMouseMove(e: MouseEvent): void {
    if (!handlers) return
    last = { x: e.clientX, y: e.clientY }
    if (!raf) {
      raf = requestAnimationFrame(() => {
        raf = 0
        if (handlers && last) handlers.apply(handlers.compute(last))
      })
    }
  }

  function onMouseUp(): void {
    if (raf) {
      cancelAnimationFrame(raf)
      raf = 0
    }
    if (handlers && last) handlers.commit(handlers.compute(last))
    handlers = null
    last = null
    window.removeEventListener('mousemove', onMouseMove)
    window.removeEventListener('mouseup', onMouseUp)
  }

  /** 组件卸载时兜底解绑，避免泄漏全局监听 */
  function teardown(): void {
    if (raf) {
      cancelAnimationFrame(raf)
      raf = 0
    }
    handlers = null
    last = null
    window.removeEventListener('mousemove', onMouseMove)
    window.removeEventListener('mouseup', onMouseUp)
  }

  function begin<R>(h: FreeInteractionHandlers<R>): void {
    teardown()
    handlers = h as FreeInteractionHandlers<unknown>
    last = null
    window.addEventListener('mousemove', onMouseMove)
    window.addEventListener('mouseup', onMouseUp)
  }

  onScopeDispose(teardown)

  return { begin }
}
