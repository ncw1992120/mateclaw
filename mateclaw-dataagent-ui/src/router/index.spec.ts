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
})
