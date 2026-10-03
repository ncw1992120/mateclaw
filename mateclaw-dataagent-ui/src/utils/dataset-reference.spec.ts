import { describe, expect, it } from 'vitest'
import { isPersistedDatasetReferenceAvailable } from './dataset-reference'

describe('isPersistedDatasetReferenceAvailable', () => {
  const datasets = [{ id: '101' }, { id: '202' }]

  it('accepts a numeric ID only when the dataset still exists', () => {
    expect(isPersistedDatasetReferenceAvailable('101', datasets)).toBe(true)
    expect(isPersistedDatasetReferenceAvailable('999', datasets)).toBe(false)
  })

  it('rejects temporary and malformed IDs even if the list contains a matching string', () => {
    expect(isPersistedDatasetReferenceAvailable('ds-101', [...datasets, { id: 'ds-101' }])).toBe(false)
    expect(isPersistedDatasetReferenceAvailable('0', [{ id: '0' }])).toBe(false)
  })
})
