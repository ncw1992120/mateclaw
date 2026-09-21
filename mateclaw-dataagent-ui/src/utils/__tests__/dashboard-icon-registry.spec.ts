import { describe, expect, it } from 'vitest'
import { DASHBOARD_ICON_REGISTRY, resolveDashboardIcon } from '@/utils/dashboard-icon-registry'

describe('dashboard icon registry', () => {
  it('提供受控的本地图标键和可读名称', () => {
    expect(DASHBOARD_ICON_REGISTRY.length).toBeGreaterThanOrEqual(16)
    expect(DASHBOARD_ICON_REGISTRY[0].label).toBeTruthy()
    expect(DASHBOARD_ICON_REGISTRY.every((item) => /^[a-z-]+$/.test(item.key))).toBe(true)
    expect(resolveDashboardIcon(DASHBOARD_ICON_REGISTRY[0].key)).toBeTruthy()
  })

  it('未知或不可信图标键安全返回 null', () => {
    expect(resolveDashboardIcon('untrusted:remote-icon')).toBeNull()
    expect(resolveDashboardIcon('<svg onload=alert(1)>')).toBeNull()
  })
})
