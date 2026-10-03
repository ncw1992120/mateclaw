import { beforeEach, describe, expect, it, vi } from 'vitest'
import { datasetFromInput, useInsight } from '../useInsight'
import type { InsightDashboardSchema } from '@/types'

const mocks = vi.hoisted(() => ({
  list: vi.fn(),
  confirmDraft: vi.fn(),
  saveDashboardSchema: vi.fn(),
}))

vi.mock('@/api/dataset', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/dataset')>()
  return { ...actual, list: (...args: unknown[]) => mocks.list(...args) }
})

vi.mock('../useInsightBackend', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../useInsightBackend')>()
  return {
    ...actual,
    confirmDatasetDraft: (...args: unknown[]) => mocks.confirmDraft(...args),
    saveDashboardSchema: (...args: unknown[]) => mocks.saveDashboardSchema(...args),
  }
})

const { state } = useInsight()

describe('旧仪表盘失效数据集引用恢复', () => {
  beforeEach(() => {
    state.backend.dashboardId = 'dashboard-1'
    state.backend.componentId = 'table-1'
    state.backend.online = true
    state.cards = [{
      id: 'table-1', type: 'table', title: '策略表', titleBarStyle: 'standard',
      multiMetric: false, multiTab: false,
    }]
    state.activeCardId = 'table-1'
    state.datasets = [datasetFromInput({
      datasetId: '999', inputName: 'table_ab', displayName: 'table_ab',
      sourceType: 'ALOUDATA_ANALYSIS_VIEW',
      sourceConfig: { datasourceId: 'datasource-1', analysisViewId: 'strategy_view' },
      fieldMappings: [], filters: [],
    }, 0)]
    mocks.list.mockResolvedValue([])
    mocks.confirmDraft.mockResolvedValue({ datasetId: '777' })
    mocks.saveDashboardSchema.mockResolvedValue('updated')
  })

  it('recreates a missing numeric dataset and saves the schema with its replacement ID', async () => {
    const saved = await useInsight().saveDashboard()

    expect(saved).toBe(true)
    expect(mocks.confirmDraft).toHaveBeenCalledWith(expect.objectContaining({
      sourceType: 'ALOUDATA_ANALYSIS_VIEW',
      datasourceId: 'datasource-1',
      sourceConfig: { analysisViewId: 'strategy_view' },
      name: 'table_ab',
    }))
    expect(state.datasets[0].backendDatasetId).toBe('777')
    const schemaJson = mocks.saveDashboardSchema.mock.calls[0][1] as InsightDashboardSchema
    expect(schemaJson.pages[0].components[0].config?.datasetPipeline?.datasetInputs?.[0].datasetId).toBe('777')
  })
})
