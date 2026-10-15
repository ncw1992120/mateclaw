import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  markDashboardPerformance,
  measureDashboardPerformance,
  setDashboardPerformanceEnabled,
  type DashboardPerformanceMark,
} from '../dashboardPerformance'

const MARK_START: DashboardPerformanceMark = 'insight-list-request-start'
const MARK_END: DashboardPerformanceMark = 'insight-list-data-ready'

function markNames(): string[] {
  return performance.getEntriesByType('mark').map((entry) => entry.name)
}

describe('dashboardPerformance', () => {
  beforeEach(() => {
    performance.clearMarks()
    performance.clearMeasures()
  })

  afterEach(() => {
    // 还原为开发环境默认行为，避免影响其他用例
    setDashboardPerformanceEnabled(true)
    performance.clearMarks()
    performance.clearMeasures()
  })

  it('在开发环境按阶段写入 performance.mark 并可测量阶段耗时', () => {
    setDashboardPerformanceEnabled(true)

    markDashboardPerformance(MARK_START)
    markDashboardPerformance(MARK_END)
    const duration = measureDashboardPerformance('insight-list', MARK_START, MARK_END)

    expect(markNames()).toContain(MARK_START)
    expect(markNames()).toContain(MARK_END)
    expect(duration).toBeGreaterThanOrEqual(0)
    expect(performance.getEntriesByName('insight-list', 'measure')).toHaveLength(1)
  })

  it('重复调用同一阶段标记不会抛错', () => {
    setDashboardPerformanceEnabled(true)

    expect(() => {
      markDashboardPerformance(MARK_START)
      markDashboardPerformance(MARK_START)
      measureDashboardPerformance('insight-list', MARK_START, MARK_END)
      measureDashboardPerformance('insight-list', MARK_START, MARK_END)
    }).not.toThrow()
  })

  it('标记缺失时测量返回 undefined 且不抛错', () => {
    setDashboardPerformanceEnabled(true)

    expect(measureDashboardPerformance('missing', MARK_START, MARK_END)).toBeUndefined()
  })

  it('生产模式（关闭后）不产生任何标记', () => {
    setDashboardPerformanceEnabled(false)

    markDashboardPerformance(MARK_START)
    markDashboardPerformance(MARK_END)
    const duration = measureDashboardPerformance('insight-list', MARK_START, MARK_END)

    expect(duration).toBeUndefined()
    expect(markNames()).not.toContain(MARK_START)
    expect(markNames()).not.toContain(MARK_END)
    expect(performance.getEntriesByType('measure')).toHaveLength(0)
  })

  it('宿主环境缺少 performance API 时静默跳过', () => {
    setDashboardPerformanceEnabled(true)
    const original = globalThis.performance
    vi.stubGlobal('performance', undefined)

    try {
      expect(() => {
        markDashboardPerformance(MARK_START)
        measureDashboardPerformance('insight-list', MARK_START, MARK_END)
      }).not.toThrow()
    } finally {
      vi.unstubAllGlobals()
      globalThis.performance = original
    }
  })
})
