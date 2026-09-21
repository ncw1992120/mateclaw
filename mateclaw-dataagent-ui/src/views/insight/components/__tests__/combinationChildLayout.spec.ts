import { describe, expect, it } from 'vitest'
import { calculateCombinationChildResize } from '../combinationChildLayout'

describe('combination child resize calculation', () => {
  it('uses the resize-start geometry snapshot for the whole gesture', () => {
    const start = {
      direction: 'e',
      startX: 100,
      startY: 100,
      originX: 12,
      originY: 8,
      originCol: 6,
      originHeight: 120,
      bounds: { width: 600, height: 360 },
      columnWidth: 50,
    }

    expect(calculateCombinationChildResize(start, { x: 200, y: 100 })).toEqual({
      x: 12,
      y: 8,
      col: 8,
      height: 120,
    })
  })
})
