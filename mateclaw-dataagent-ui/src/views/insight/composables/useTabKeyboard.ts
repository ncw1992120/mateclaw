import { nextTick } from 'vue'

/**
 * 页签键盘导航（WAI-ARIA tabs 模式）公共逻辑。
 *
 * 从 KpiCardWidget 的页签实现中抽出，供 KPI 卡与组合卡片页签复用：
 * - 左/右/上/下方向键循环切换；Home/End 跳转首尾；Enter/Space 激活；
 * - 切换后把焦点移动到新激活的页签（roving tabindex）。
 *
 * 用法：组件模板中每个 tab 元素声明 role="tab"、aria-selected、tabindex，
 * 并绑定 @keydown="onTabKeydown($event, tab.id)"。
 */
export function useTabKeyboard(
  /** 页签列表（getter，保证拿到最新数组） */
  tabs: () => { id: string }[],
  /** 激活页签选择函数 */
  select: (id: string) => void,
  /** 按 id 查找页签 DOM 元素（用于移动焦点；请用容器内局部查找，别用全局 document） */
  getTabEl: (id: string) => HTMLElement | null,
) {
  function onTabKeydown(event: KeyboardEvent, tabId: string): void {
    const list = tabs()
    const currentIndex = list.findIndex((tab) => tab.id === tabId)
    if (currentIndex < 0 || list.length < 2) return
    let nextIndex = currentIndex
    if (event.key === 'ArrowRight' || event.key === 'ArrowDown') nextIndex = (currentIndex + 1) % list.length
    else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') nextIndex = (currentIndex - 1 + list.length) % list.length
    else if (event.key === 'Home') nextIndex = 0
    else if (event.key === 'End') nextIndex = list.length - 1
    else if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      select(tabId)
      return
    } else return
    event.preventDefault()
    const nextTab = list[nextIndex]
    if (!nextTab) return
    select(nextTab.id)
    nextTick(() => getTabEl(nextTab.id)?.focus())
  }

  return { onTabKeydown }
}
