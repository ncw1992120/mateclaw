import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const getCurrentUser = vi.fn()

vi.mock('@/api/auth', () => ({
  getCurrentUser,
}))

describe('useUserStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    getCurrentUser.mockReset()
  })

  it('refreshes user metadata without clearing the existing JWT', async () => {
    const { useUserStore } = await import('@/stores/useUserStore')
    localStorage.setItem('token', 'jwt-from-login')
    localStorage.setItem('workspaceId', JSON.stringify('1'))
    getCurrentUser.mockResolvedValue({
      id: '1',
      token: null,
      username: 'admin',
      nickname: 'Admin',
      role: 'admin',
      workspaces: [{ id: '1', memberRole: 'owner', effectiveRole: 'owner' }],
    })

    const store = useUserStore()
    expect(await store.fetchCurrentUser()).toBe(true)
    expect(store.token).toBe('jwt-from-login')
    expect(store.userId).toBe('1')
    expect(store.isAdmin).toBe(true)
  })

  it('starts with no workspace when the saved workspace id is malformed', async () => {
    localStorage.setItem('workspaceId', '{invalid-json')
    const { useUserStore } = await import('@/stores/useUserStore')

    const store = useUserStore()

    expect(store.currentWorkspaceId).toBeNull()
    expect(localStorage.getItem('workspaceId')).toBeNull()
  })
})
