import { beforeEach, describe, expect, it } from 'vitest'
import { readDashboardPreviewFillWidth, saveDashboardPreviewFillWidth } from '../dashboard-preview-preferences'

describe('dashboard preview preferences', () => {
  beforeEach(() => localStorage.clear())

  it('restores the selected fit-width state when re-entering the same dashboard preview', () => {
    expect(readDashboardPreviewFillWidth('dashboard-1')).toBe(false)

    saveDashboardPreviewFillWidth('dashboard-1', true)

    expect(readDashboardPreviewFillWidth('dashboard-1')).toBe(true)
  })

  it('keeps fit-width preferences isolated between dashboards', () => {
    saveDashboardPreviewFillWidth('dashboard-1', true)
    saveDashboardPreviewFillWidth('dashboard-2', false)

    expect(readDashboardPreviewFillWidth('dashboard-1')).toBe(true)
    expect(readDashboardPreviewFillWidth('dashboard-2')).toBe(false)
  })
})
