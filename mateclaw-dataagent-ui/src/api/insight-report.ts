import api from './index'
import type { InsightReport, InsightReportPublishInput } from '@/types'

/** API 路径常量 */
const BASE_URL = '/dataagent/api/v1/insight/dashboards'

/** 报告 API 路径常量 */
const REPORT_BASE_URL = '/dataagent/api/v1/insight/reports'

/** 归因分析请求参数 */
export interface AttributionAnalysisParams {
  /** 数据源 ID */
  datasourceId: number
  /** 指标名称 */
  metric: string
  /** 分析维度列表 */
  dimensions: string[]
  /** 时间粒度：DAY / WEEK / MONTH / QUARTER / YEAR */
  granularity: string
  /** 对比类型：CUSTOM / DOD / YOY / MOM / QOQ / WOW */
  comparisonType: string
  /** 当前时间表达式 */
  currentTimeExpr: string
  /** 对比时间表达式 */
  compareTimeExpr?: string
  /** 自定义对比开始时间 */
  startDateTime?: string
  /** 自定义对比结束时间 */
  endDateTime?: string
  /** 筛选条件表达式列表 */
  filters?: string[]
}

/** 归因校验结果 */
export interface AttributionCheckResult {
  result: boolean
  errorMsg?: string
}

/** 整体变化概要 */
export interface AttributionAllSummary {
  currentValue: number | null
  comparisonValue: number | null
  growth: number | null
  growthRate: number | null
  overallContributionRate: number | null
  relativeContributionRate: number | null
}

/** 维度归因详情 */
export interface DimAttribution {
  dimensionValue: string[]
  currentValue: number[]
  comparisonValue: number[]
  growth: number[]
  growthRate: number[]
  contributionRate: number[]
  overallContributionRate: number[]
  relativeContributionRate: number[]
}

/** 多维归因结果 */
export interface MultiDimResult {
  metric: string
  all: AttributionAllSummary
  dimensions: Record<string, DimAttribution>
}

/** 指标树定义 */
export interface MetricTreeDef {
  rootNode: string
  metricTree: Record<string, string>
  metricTreeNodes: Record<string, string>
  metricDefinitions: Record<string, any>
}

/** 树归因节点归因结果 */
export interface TreeNodeAttribution {
  currentValue: number | null
  comparisonValue: number | null
  growth: number | null
  growthRate: number | null
  relativeContributionRate: number | null
  metricName: string | null
}

/** 归因分析响应 */
export interface AttributionAnalysisResponse {
  success: boolean
  code?: string
  errorMsg?: string
  traceId?: string
  checkResult?: AttributionCheckResult
  multiDimResult?: MultiDimResult
  metricTreeDef?: MetricTreeDef
  treeResult?: Record<string, TreeNodeAttribution>
}

/** 同步生成报告（HTML 格式） */
export function generateReport(dashboardId: string) {
  return api.post<string>(`${BASE_URL}/${dashboardId}/report`)
}

/** 获取已生成的报告 */
export function getReport(dashboardId: string) {
  return api.get<string>(`${BASE_URL}/${dashboardId}/report`)
}

/**
 * SSE 流式生成报告
 * @param dashboardId 仪表盘 ID
 * @param onContent 内容回调（每个 chunk 触发一次）
 * @param onError 错误回调
 * @param onComplete 完成回调
 * @returns AbortController（可调用 .abort() 取消）
 */
export function streamReport(
  dashboardId: string,
  onContent: (chunk: string) => void,
  onError?: (err: string) => void,
  onComplete?: () => void
): AbortController {
  const controller = new AbortController()
  const token = localStorage.getItem('token') ?? ''

  fetch(`${BASE_URL}/${dashboardId}/report/stream`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream',
    },
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        onError?.(`HTTP ${response.status}`)
        return
      }
      const reader = response.body?.getReader()
      if (!reader) {
        onError?.('无法读取响应流')
        return
      }
      const decoder = new TextDecoder()
      let buffer = ''
      while (true) {
        const { done, value } = await reader.read()
        if (done) {
          break
        }
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() ?? ''
        let pendingError = false
        for (const line of lines) {
          if (line.startsWith('event:error')) {
            pendingError = true
          } else if (line.startsWith('data:')) {
            const data = line.slice(5)
            if (pendingError) {
              onError?.(data)
              pendingError = false
            } else {
              onContent(data)
            }
          }
        }
      }
      onComplete?.()
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        onError?.(err.message)
      }
    })

  return controller
}

/** 归因分析 */
export function attributionAnalysis(params: AttributionAnalysisParams) {
  return api.post<AttributionAnalysisResponse>(`${BASE_URL}/attribution`, params)
}

/** 发布报告 */
export function publishReport(data: InsightReportPublishInput) {
  return api.post<InsightReport>(REPORT_BASE_URL, data)
}

/** 查询报告列表 */
export function listReports() {
  return api.get<InsightReport[]>(REPORT_BASE_URL)
}

/** 获取报告详情 */
export function getReportDetail(id: string) {
  return api.get<InsightReport>(`${REPORT_BASE_URL}/${id}`)
}

/** 删除报告 */
export function deleteReport(id: string) {
  return api.delete(`${REPORT_BASE_URL}/${id}`)
}
