import { describe, expect, it } from 'vitest'
import { FILTER_ALL_VALUE, getFilterSelectionBehavior, normalizeFilterSelection } from '@/utils/filter-selection'

describe('filter selection behavior', () => {
  it('defaults to single-select and keeps the legacy clearable behavior', () => {
    expect(getFilterSelectionBehavior()).toEqual({
      selectionMode: 'single',
      allowSelectAll: false,
      allowNoFilter: true,
    })
    expect(normalizeFilterSelection('east')).toBe('east')
    expect(normalizeFilterSelection(undefined)).toBeUndefined()
  })

  it('supports an explicit all option without sending its sentinel downstream', () => {
    expect(normalizeFilterSelection(FILTER_ALL_VALUE, {
      allowSelectAll: true,
      allowNoFilter: false,
    })).toBeUndefined()
  })

  it('supports multiple values and removes the all sentinel', () => {
    expect(normalizeFilterSelection(['east', 'west'], {
      selectionMode: 'multiple',
      allowSelectAll: true,
    })).toEqual(['east', 'west'])
    expect(normalizeFilterSelection([FILTER_ALL_VALUE], {
      selectionMode: 'multiple',
      allowSelectAll: true,
    })).toBeUndefined()
    expect(normalizeFilterSelection([FILTER_ALL_VALUE, 'east'], {
      selectionMode: 'multiple',
      allowSelectAll: true,
    })).toEqual(['east'])
  })

  it('does not turn a non-clearable filter into an omitted condition', () => {
    expect(normalizeFilterSelection(undefined, {
      allowNoFilter: false,
    })).toBe('')
    expect(normalizeFilterSelection([], {
      selectionMode: 'multiple',
      allowNoFilter: false,
    })).toEqual([])
  })
})
