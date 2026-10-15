import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'

const STORAGE_PREFIX = 'mateclaw:insight:recent-colors:v1:'

async function createStore(userId: number | string | null) {
  const { useUserStore } = await import('@/stores/useUserStore')
  const userStore = useUserStore()
  userStore.userId = userId
  const { useInsightColorHistoryStore } = await import('@/stores/useInsightColorHistoryStore')
  return useInsightColorHistoryStore()
}

describe('useInsightColorHistoryStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('loads an empty history from the account-scoped key', async () => {
    const key = `${STORAGE_PREFIX}user-17`
    const store = await createStore('user-17')

    expect(store.recentColors).toEqual([])
    expect(localStorage.getItem(key)).toBeNull()
  })

  it('loads and normalizes history from the account-scoped key', async () => {
    const key = `${STORAGE_PREFIX}user-17`
    localStorage.setItem(key, JSON.stringify(['#abc']))

    const store = await createStore('user-17')

    expect(store.recentColors).toEqual(['#AABBCC'])
    expect(localStorage.getItem(key)).toBe(JSON.stringify(['#abc']))
  })

  it('normalizes valid colors and ignores invalid values', async () => {
    const store = await createStore('user-1')

    store.recordColor(' #abc ')
    store.recordColor('#12aBcD')
    store.recordColor('#abcd')
    store.recordColor('red')
    store.recordColor(null)

    expect(store.recentColors).toEqual(['#12ABCD', '#AABBCC'])
    expect(localStorage.getItem(`${STORAGE_PREFIX}user-1`)).toBe(
      JSON.stringify(['#12ABCD', '#AABBCC'])
    )
  })

  it('moves the most recently used color to the front without duplicates', async () => {
    const store = await createStore('user-2')

    store.recordColor('#112233')
    store.recordColor('#445566')
    store.recordColor('#112233')

    expect(store.recentColors).toEqual(['#112233', '#445566'])
  })

  it('keeps only the 12 most recently used colors', async () => {
    const store = await createStore('user-3')
    const colors = Array.from({ length: 13 }, (_, index) =>
      `#${(index + 1).toString(16).padStart(6, '0')}`
    )

    colors.forEach((color) => store.recordColor(color))

    expect(store.recentColors).toHaveLength(12)
    expect(store.recentColors[0]).toBe('#00000D')
    expect(store.recentColors).not.toContain('#000001')
  })

  it('does not access account history storage before a user ID is available', async () => {
    const getItem = vi.spyOn(Storage.prototype, 'getItem')
    const setItem = vi.spyOn(Storage.prototype, 'setItem')
    const store = await createStore(null)
    store.recordColor('#123')

    expect(store.recentColors).toEqual(['#112233'])
    expect(getItem.mock.calls.some(([key]) => key?.startsWith(STORAGE_PREFIX))).toBe(false)
    expect(setItem.mock.calls.some(([key]) => key.startsWith(STORAGE_PREFIX))).toBe(false)
  })

  it('switches histories immediately when the account changes', async () => {
    localStorage.setItem(`${STORAGE_PREFIX}first`, JSON.stringify(['#111111']))
    localStorage.setItem(`${STORAGE_PREFIX}second`, JSON.stringify(['#222222']))
    const store = await createStore('first')
    const { useUserStore } = await import('@/stores/useUserStore')

    useUserStore().userId = 'second'
    await nextTick()

    expect(store.recentColors).toEqual(['#222222'])
  })

  it('refreshes the active account when its storage event arrives', async () => {
    const key = `${STORAGE_PREFIX}user-4`
    const store = await createStore('user-4')
    localStorage.setItem(key, JSON.stringify(['#abcdef']))

    window.dispatchEvent(new StorageEvent('storage', { key, newValue: JSON.stringify(['#abcdef']) }))

    expect(store.recentColors).toEqual(['#ABCDEF'])
  })

  it('clears memory on logout but preserves history for the next login', async () => {
    const key = `${STORAGE_PREFIX}user-5`
    const store = await createStore('user-5')
    store.recordColor('#123456')
    const { useUserStore } = await import('@/stores/useUserStore')

    const userStore = useUserStore()
    userStore.logout()
    expect(store.recentColors).toEqual([])
    expect(localStorage.getItem(key)).toBe(JSON.stringify(['#123456']))

    userStore.userId = 'user-5'
    await nextTick()
    expect(store.recentColors).toEqual(['#123456'])
  })

  it('filters corrupt entries and safely handles invalid JSON and storage failures', async () => {
    const key = `${STORAGE_PREFIX}user-6`
    localStorage.setItem(key, JSON.stringify(['#abc', 'invalid', 42, '#12345678']))
    const store = await createStore('user-6')
    expect(store.recentColors).toEqual(['#AABBCC'])

    localStorage.setItem(key, '{not-json')
    store.reload()
    expect(store.recentColors).toEqual([])

    const getItem = vi.spyOn(Storage.prototype, 'getItem').mockImplementation((storageKey) => {
      if (storageKey === key) throw new DOMException('Blocked', 'SecurityError')
      return null
    })
    expect(() => store.reload()).not.toThrow()
    expect(store.recentColors).toEqual([])
    getItem.mockRestore()

    const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('Quota exceeded', 'QuotaExceededError')
    })
    expect(() => store.recordColor('#fed')).not.toThrow()
    expect(store.recentColors).toEqual(['#FFEEDD'])
    setItem.mockRestore()
  })
})
