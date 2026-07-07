import { onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'

/** ECharts option 顶层 key 白名单（安全过滤） */
const ECHARTS_ALLOWED_KEYS = new Set([
  'title', 'tooltip', 'legend', 'xAxis', 'yAxis', 'series',
  'grid', 'color', 'dataset', 'graphic', 'radar', 'polar',
  'angleAxis', 'radiusAxis', 'visualMap',
])

/** ECharts option 最大尺寸（100KB） */
const ECHARTS_MAX_OPTION_SIZE = 100 * 1024

/** 图表实例与 ResizeObserver 的映射 */
interface ChartBinding {
  chart: echarts.ECharts
  observer: ResizeObserver
}

/**
 * 递归剥离 ECharts option 中的函数值，防止 XSS
 */
function sanitizeEchartsOption(obj: Record<string, any>): void {
  for (const key of Object.keys(obj)) {
    const val = obj[key]
    if (typeof val === 'string' && val.trimStart().startsWith('function')) {
      delete obj[key]
    } else if (typeof val === 'function') {
      delete obj[key]
    } else if (val && typeof val === 'object') {
      if (Array.isArray(val)) {
        val.forEach((item: any) => {
          if (item && typeof item === 'object') {
            sanitizeEchartsOption(item)
          }
        })
      } else {
        sanitizeEchartsOption(val)
      }
    }
  }
}

/**
 * 过滤 ECharts option 顶层 key，仅保留白名单内的字段
 */
function filterEchartsTopLevelKeys(option: Record<string, any>): Record<string, any> {
  const filtered: Record<string, any> = {}
  for (const key of Object.keys(option)) {
    if (ECHARTS_ALLOWED_KEYS.has(key)) {
      filtered[key] = option[key]
    }
  }
  return filtered
}

/**
 * 修正 ECharts 布局：统一标题/图例/网格间距，避免元素重叠
 */
function applyLayoutAdjustment(option: Record<string, any>): void {
  const hasTitle = !!option.title
  const hasLegend = !!option.legend
  option.title = {
    left: 'center',
    top: 8,
    textStyle: { fontSize: 13, fontWeight: 600 },
    ...(typeof option.title === 'object' ? option.title : {}),
  }
  option.legend = {
    bottom: 8,
    top: hasTitle ? 32 : 8,
    type: 'scroll',
    textStyle: { fontSize: 11 },
    ...(typeof option.legend === 'object' ? option.legend : {}),
  }
  option.grid = {
    left: 40,
    right: 24,
    top: hasTitle ? (hasLegend ? 72 : 56) : (hasLegend ? 56 : 28),
    bottom: hasLegend ? 48 : 24,
    containLabel: true,
    ...(typeof option.grid === 'object' ? option.grid : {}),
  }
}

/**
 * ECharts 渲染 composable
 * 提供单图渲染（含布局修正、ResizeObserver）和销毁能力，供洞察仪表盘组件复用。
 * 参照 ChatView.vue 的 scanAndMountEChartsBlocks 渲染逻辑。
 */
export function useEChartsRenderer() {
  /** 已挂载的图表实例映射（container -> binding） */
  const bindings = new Map<HTMLElement, ChartBinding>()

  /**
   * 渲染单个 ECharts 图表到指定容器
   * @param container 目标 DOM 容器
   * @param optionRaw 原始 ECharts option
   * @returns echarts 实例（失败返回 null）
   */
  function renderECharts(container: HTMLElement, optionRaw: Record<string, unknown>): echarts.ECharts | null {
    if (!container) {
      return null
    }

    const optionStr = JSON.stringify(optionRaw)
    if (optionStr.length > ECHARTS_MAX_OPTION_SIZE) {
      container.textContent = 'Chart option too large'
      return null
    }

    try {
      const option = filterEchartsTopLevelKeys(optionRaw as Record<string, any>)
      sanitizeEchartsOption(option)

      if (!option || typeof option !== 'object' || !option.series) {
        container.textContent = 'Invalid chart option'
        return null
      }

      applyLayoutAdjustment(option)

      if (!container.style.height) {
        container.style.height = '300px'
      }
      if (!container.style.width) {
        container.style.width = '100%'
      }

      // 销毁旧实例（若存在）
      disposeChart(container)

      const chart = echarts.init(container)
      chart.setOption(option)

      // 监听容器尺寸变化，debounce 50ms 避免动画过程高频触发
      let resizeRafId: number | null = null
      const observer = new ResizeObserver(() => {
        if (resizeRafId !== null) {
          cancelAnimationFrame(resizeRafId)
        }
        resizeRafId = requestAnimationFrame(() => {
          resizeRafId = null
          if (!chart.isDisposed()) {
            chart.resize()
          }
        })
      })
      observer.observe(container)

      bindings.set(container, { chart, observer })
      return chart
    } catch (e) {
      console.error('[useEChartsRenderer] chart render error:', e)
      container.textContent = 'Chart render error'
      return null
    }
  }

  /**
   * 销毁指定容器的图表实例并断开 ResizeObserver
   */
  function disposeChart(container: HTMLElement): void {
    const binding = bindings.get(container)
    if (binding) {
      if (!binding.chart.isDisposed()) {
        binding.chart.dispose()
      }
      binding.observer.disconnect()
      bindings.delete(container)
    }
  }

  /**
   * 销毁所有图表实例
   */
  function disposeAll(): void {
    bindings.forEach((binding) => {
      if (!binding.chart.isDisposed()) {
        binding.chart.dispose()
      }
      binding.observer.disconnect()
    })
    bindings.clear()
  }

  /** 组件卸载时自动清理所有图表 */
  onBeforeUnmount(() => {
    disposeAll()
  })

  return {
    renderECharts,
    disposeChart,
    disposeAll,
  }
}
