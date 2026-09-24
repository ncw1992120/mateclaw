import { describe, expect, it } from 'vitest'
import { createComponentPreviewQueryContext } from '../component-preview-query-context'

describe('createComponentPreviewQueryContext', () => {
  it('includes the dashboard and component identity with empty runtime filters', () => {
    expect(createComponentPreviewQueryContext('dashboard-1', 'component-1')).toMatchObject({
      dashboardId: 'dashboard-1',
      componentId: 'component-1',
      parameters: {},
    })
  })
})
