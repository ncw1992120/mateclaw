import { describe, expect, it } from 'vitest'
import { nextSortState, shouldResetPage } from '../table-sort-state'

/** 表头三态与页码重置（实施计划任务 5 步骤 5 / TC-06 / TC-08） */
describe('table-sort-state', () => {
  it('点击 A：无排序 → A 升序', () => {
    expect(nextSortState(null, 'in_account')).toEqual({ field: 'in_account', direction: 'asc' })
  })

  it('同一字段再点：升序 → 降序 → 取消', () => {
    let state = nextSortState(null, 'in_account')
    state = nextSortState(state, 'in_account')
    expect(state).toEqual({ field: 'in_account', direction: 'desc' })
    state = nextSortState(state, 'in_account')
    expect(state).toBeNull()
  })

  it('A 升序后切换 B：新字段从升序开始', () => {
    const state = nextSortState({ field: 'in_account', direction: 'asc' }, 'metric_date')
    expect(state).toEqual({ field: 'metric_date', direction: 'asc' })
  })

  it('空字段名保持原状态', () => {
    const current = { field: 'in_account', direction: 'desc' as const }
    expect(nextSortState(current, '')).toEqual(current)
  })

  it('排序变化应重置页码；仅翻页不因排序重置', () => {
    expect(shouldResetPage(
      { sort: { field: 'a', direction: 'asc' }, parameters: { x: 1 } },
      { sort: { field: 'b', direction: 'asc' }, parameters: { x: 1 } },
    )).toBe(true)
    expect(shouldResetPage(
      { sort: { field: 'a', direction: 'asc' }, parameters: { x: 1 } },
      { sort: { field: 'a', direction: 'asc' }, parameters: { x: 1 } },
    )).toBe(false)
    expect(shouldResetPage(
      { sort: { field: 'a', direction: 'asc' }, parameters: { x: 1 } },
      { sort: { field: 'a', direction: 'asc' }, parameters: { x: 2 } },
    )).toBe(true)
  })
})
