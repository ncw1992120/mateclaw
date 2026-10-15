/**
 * 洞察仪表盘加载性能标记。
 *
 * 用于在开发/验证环境下为「列表可交互 → 列表数据就绪」「进入编辑器 → Schema 解析完成」
 * 「预览外壳 → 首个组件出数 → 全部组件完成」等阶段打点，便于同一看板优化前后对照。
 *
 * 约束：
 * - 仅在开发环境生效；生产构建下所有导出均为无副作用空操作。
 * - 只记录阶段名称与耗时，不采集看板内容、筛选值或凭据。
 * - 不改变任何运行时请求，标记写入失败一律静默忽略。
 */

/** 性能阶段标记名称（按页面分组，便于在 Performance 面板/脚本中按名读取） */
export type DashboardPerformanceMark =
  /** 洞察列表：组件已挂载、控件可操作 */
  | 'insight-list-interactive'
  /** 洞察列表：摘要分页请求已发起 */
  | 'insight-list-request-start'
  /** 洞察列表：第一页摘要卡片已渲染 */
  | 'insight-list-data-ready'
  /** 列表发起新建：创建接口已返回 */
  | 'insight-create-resolved'
  /** 列表发起新建：已跳转到编辑器路由 */
  | 'insight-create-navigated'
  /** 编辑器：外壳已渲染 */
  | 'insight-editor-interactive'
  /** 编辑器：详情请求已发起 */
  | 'insight-editor-request-start'
  /** 编辑器：Schema 解析完成，页面/组件树可操作 */
  | 'insight-editor-schema-ready'
  /** 预览：开始加载详情 */
  | 'insight-preview-start'
  /** 预览：画布骨架已出现 */
  | 'insight-preview-canvas-ready'
  /** 预览：首个组件数据可渲染 */
  | 'insight-preview-first-component'
  /** 预览：当前页所有可查询组件均已结束（成功/空/失败/超时） */
  | 'insight-preview-all-components'

/** 是否写入性能标记；默认跟随开发环境，生产恒为 false */
let enabled = Boolean(import.meta.env.DEV)

/**
 * 覆盖性能标记开关（仅供测试与本地验证使用）。
 * 生产构建下不会有人调用，默认保持关闭。
 */
export function setDashboardPerformanceEnabled(next: boolean): void {
  enabled = Boolean(next)
}

/** 当前是否写入性能标记 */
export function isDashboardPerformanceEnabled(): boolean {
  return enabled
}

/** 写入一个阶段点；重复写入同名标记不会抛错。 */
export function markDashboardPerformance(name: DashboardPerformanceMark): void {
  if (!enabled) {
    return
  }
  try {
    globalThis.performance?.mark?.(name)
  } catch {
    // 宿主环境不支持或不接受该标记时静默跳过
  }
}

/**
 * 测量两个阶段点之间的耗时（毫秒）。
 * 起点或终点缺失、环境不支持测量，或处于关闭状态时返回 undefined。
 */
export function measureDashboardPerformance(
  name: string,
  start: DashboardPerformanceMark,
  end: DashboardPerformanceMark,
): number | undefined {
  if (!enabled) {
    return undefined
  }
  try {
    const api = globalThis.performance
    if (typeof api?.measure !== 'function') {
      return undefined
    }
    const entry = api.measure(name, start, end) as { duration?: unknown } | undefined
    const duration = entry?.duration
    return typeof duration === 'number' ? duration : undefined
  } catch {
    return undefined
  }
}
