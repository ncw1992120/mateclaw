import { describe, expect, it } from 'vitest'
import { createDashboardEditHistory, resolveDashboardHistoryShortcut } from '../dashboard-edit-history'

describe('dashboard edit history', () => {
  it('restores the previous edit and re-applies it with redo', () => {
    const history = createDashboardEditHistory<{ title: string }>(2)
    history.reset({ title: '初始标题' })
    history.push({ title: '修改后的标题' })

    expect(history.canUndo).toBe(true)
    expect(history.undo()).toEqual({ title: '初始标题' })
    expect(history.canRedo).toBe(true)
    expect(history.redo()).toEqual({ title: '修改后的标题' })
  })

  it('drops the redo branch when a new edit follows undo', () => {
    const history = createDashboardEditHistory<{ title: string }>()
    history.reset({ title: 'A' })
    history.push({ title: 'B' })
    history.undo()
    history.push({ title: 'C' })

    expect(history.redo()).toBeNull()
    expect(history.undo()).toEqual({ title: 'A' })
  })

  it('does not create duplicate history entries and keeps the configured limit', () => {
    const history = createDashboardEditHistory<{ title: string }>(2)
    history.reset({ title: 'A' })
    history.push({ title: 'A' })
    history.push({ title: 'B' })
    history.push({ title: 'C' })

    expect(history.undo()).toEqual({ title: 'B' })
    expect(history.undo()).toBeNull()
  })

  it.each([
    [{ key: 'z', ctrlKey: true }, false, 'undo'],
    [{ key: 'z', metaKey: true, shiftKey: true }, false, 'redo'],
    [{ key: 'y', ctrlKey: true }, false, 'redo'],
    [{ key: 'z', ctrlKey: true }, true, null],
    [{ key: 'z', ctrlKey: true, altKey: true }, false, null],
  ] as const)('resolves accessible keyboard shortcuts %#', (event, editingText, expected) => {
    expect(resolveDashboardHistoryShortcut(event, editingText)).toBe(expected)
  })
})
