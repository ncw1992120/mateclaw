import { describe, expect, it } from 'vitest'
import { insightDashboardListLocation } from './insightDashboardNavigation'

describe('insight dashboard navigation', () => {
  it('returns to the insight dashboard list while preserving route context', () => {
    expect(insightDashboardListLocation({ tenant: 'demo', nav: 'insight' })).toEqual({
      path: '/',
      query: { tenant: 'demo', nav: 'insight' },
    })
  })

  it('overrides a stale navigation query when returning', () => {
    expect(insightDashboardListLocation({ nav: 'report' })).toEqual({
      path: '/',
      query: { nav: 'insight' },
    })
  })
})
