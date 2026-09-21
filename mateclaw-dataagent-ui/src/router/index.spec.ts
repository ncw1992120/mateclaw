import { describe, expect, it } from 'vitest'
import router from './index'

describe('insight dashboard editor route', () => {
  it('uses the documented dashboard editor path', () => {
    const resolved = router.resolve('/insight/dashboard/editor')
    expect(resolved.name).toBe('insight-dashboard-editor')
    expect(resolved.matched).toHaveLength(1)
  })

  it('does not expose the old prototype path', () => {
    const resolved = router.resolve('/insight-prototype')
    expect(resolved.matched).toHaveLength(0)
  })

  it('uses one lazy-loaded formal editor and keeps prototypes development-only', () => {
    const editor = router.getRoutes().find((route) => route.name === 'insight-dashboard-editor')
    expect(typeof editor?.components?.default).toBe('function')
    expect(router.hasRoute('insight-card-container-prototype')).toBe(import.meta.env.DEV)
  })
})
