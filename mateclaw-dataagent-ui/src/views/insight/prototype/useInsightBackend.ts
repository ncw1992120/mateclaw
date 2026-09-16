/**
 * useInsightBackend —— 洞察原型（prototype）与 mateclaw-dataagent 后端的对接层
 * =====================================================================
 * 原型此前是一套纯前端假数据演示（useInsight.ts 中的 [MOCK]）。本文件把原型
 * 与后端「真实接口」打通，复用 ui 既有的 api 封装与 utils，避免重复造轮子：
 *
 *   - 仪表盘：  @/api/insight-dashboard（list/get/create/update/executeComponent/...）
 *   - 数据集：  @/api/dataset（confirmDraft 草稿落库 / previewInput 预览）
 *   - Schema：  @/utils/dashboard-schema（旧格式迁移）
 *   - Pipeline：@/utils/component-dataset-pipeline（读写 component.config.datasetPipeline）
 *
 * 后端契约要点（见 mateclaw-dataagent）：
 *   - 仪表盘 Schema 采用 pages[].components[]；组件级数据集编排存放于
 *     `component.config.datasetPipeline = { datasetInputs, script, scriptFilterBindings, ... }`。
 *   - datasetInputs[].datasetId 必须是「真实数据集 ID」；datasetInputs[].inputName 即原型别名(alias)。
 *   - 执行入口：POST /v1/insight/dashboards/{id}/components/{componentId}/executions，
 *     随后轮询 status/logs/result。
 *
 * 鉴权：请求拦截器（@/api/index）自动从 localStorage 注入 token 与 X-Workspace-Id，
 * 因此这里无需手动处理认证；未登录时接口会返回 401 并由拦截器跳转登录页。
 */
import * as dashboardApi from '@/api/insight-dashboard'
import * as datasetApi from '@/api/dataset'
import { migrateInsightDashboardSchema } from '@/utils/dashboard-schema'
import { readComponentDatasetPipeline, writeComponentDatasetPipeline } from '@/utils/component-dataset-pipeline'
import type {
  ComponentDatasetPipeline,
  InsightComponent,
  InsightDashboardSchema,
  InsightComponentData,
} from '@/types'

/** 卡片属性配置编辑器使用的仪表盘名称（首次进入自动创建） */
export const PROTOTYPE_DASHBOARD_NAME = '洞察仪表盘-卡片属性配置'

/** 是否已登录（本地存在 token） */
export function hasAuth(): boolean {
  return !!localStorage.getItem('token')
}

/** 找到同名原型仪表盘，不存在则创建一个草稿仪表盘，返回其 id */
export async function ensurePrototypeDashboard(): Promise<string> {
  const list = (await dashboardApi.list()) as unknown as Array<{ id: string; name: string }>
  const found = (list || []).find((d) => d.name === PROTOTYPE_DASHBOARD_NAME)
  if (found) return String(found.id)
  const created = (await dashboardApi.create({
    name: PROTOTYPE_DASHBOARD_NAME,
    description: '洞察-仪表盘-卡片属性配置',
  })) as unknown as { id: string }
  return String(created.id)
}

/** 读取并规范化为当前版本 Schema（旧 components 格式自动迁移到 pages） */
export async function loadDashboardSchema(id: string): Promise<InsightDashboardSchema> {
  const detail = (await dashboardApi.get(id)) as unknown as { schemaJson?: string }
  let parsed: unknown = {}
  if (detail?.schemaJson) {
    try {
      parsed = JSON.parse(detail.schemaJson)
    } catch {
      parsed = {}
    }
  }
  return migrateInsightDashboardSchema(parsed, '卡片配置')
}

/** 保存 Schema（序列化为 schemaJson 字符串） */
export async function saveDashboardSchema(id: string, schema: InsightDashboardSchema): Promise<void> {
  await dashboardApi.update(id, { schemaJson: JSON.stringify(schema) })
}

/** 读取组件的组件级数据集编排（复用 ui 工具，保证与真实编辑器一致） */
export function pipelineOf(component: InsightComponent | undefined | null): ComponentDatasetPipeline | undefined {
  return readComponentDatasetPipeline(component)
}

/** 把编排写回组件（返回新对象，避免共享引用） */
export function withPipeline(component: InsightComponent, pipeline: ComponentDatasetPipeline): InsightComponent {
  return writeComponentDatasetPipeline(component, pipeline)
}

/**
 * 数据集草稿落库：把原型的“数据集配置”提交后端并拿到真实 datasetId。
 * 注意：需要后端已配置对应类型的数据源（JDBC/Aloudata/API/File 的具体凭据）。
 */
export async function confirmDatasetDraft(
  req: datasetApi.DatasetComposerDraftRequest & { name: string; description?: string },
): Promise<{ datasetId: string }> {
  const res = (await datasetApi.confirmDraft(req)) as unknown as { datasetId: string }
  return res
}

/** 预览数据集草稿（不落库，返回受控预览行） */
export async function previewDatasetDraft(req: datasetApi.DatasetComposerDraftRequest) {
  return (await datasetApi.previewDraft(req)) as unknown as {
    columns?: string[]
    rows?: Record<string, unknown>[]
  }
}

/** 提交组件级 Python 预处理执行 */
export async function submitComponentExecution(
  dashboardId: string,
  componentId: string,
  parameters: Record<string, unknown> = {},
): Promise<{ executionId: string; status?: string }> {
  return (await dashboardApi.executeComponent(dashboardId, componentId, parameters)) as unknown as {
    executionId: string
    status?: string
  }
}

/** 查询执行状态 */
export async function fetchExecutionStatus(executionId: string): Promise<{ status?: string; result?: string; error?: string }> {
  return (await dashboardApi.getExecutionStatus(executionId)) as unknown as {
    status?: string
    result?: string
    error?: string
  }
}

/** 查询执行日志（标准输出与受限错误） */
export async function fetchExecutionLogs(executionId: string): Promise<{ status?: string; output?: string; error?: string }> {
  return (await dashboardApi.getExecutionLogs(executionId)) as unknown as {
    status?: string
    output?: string
    error?: string
  }
}

/** 查询执行结果预览（表格行） */
export async function fetchExecutionResult(executionId: string) {
  return (await dashboardApi.getExecutionResult(executionId)) as unknown as {
    rows?: { columns?: string[]; rows?: unknown[][] } | unknown
    inline?: boolean
    status?: string
  }
}

/** 取消执行 */
export async function cancelExecution(executionId: string) {
  return dashboardApi.cancelExecution(executionId)
}

/** 组件取数预览（Aloudata 指标类组件，即时验证） */
export async function previewInsightComponent(component: InsightComponent): Promise<InsightComponentData> {
  return (await dashboardApi.previewComponent(component)) as unknown as InsightComponentData
}
