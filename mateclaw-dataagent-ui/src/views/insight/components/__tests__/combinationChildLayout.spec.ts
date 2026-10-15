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

  it('keeps pixel-continuous preview values when snap is disabled and snaps on release', () => {
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

    // 拖动预览：col 像素连续（不取整），跟随鼠标
    expect(calculateCombinationChildResize(start, { x: 125, y: 100 }, false)).toEqual({
      x: 12,
      y: 8,
      col: 6.5,
      height: 120,
    })
    // 松手落格：吸附整列
    expect(calculateCombinationChildResize(start, { x: 125, y: 100 })).toEqual({
      x: 12,
      y: 8,
      col: 7,
      height: 120,
    })
    // west 方向预览：左边缘像素连续移动（负 x 被边界 clamp 到 0）
    expect(calculateCombinationChildResize({ ...start, direction: 'w' }, { x: 75, y: 100 }, false)).toEqual({
      x: 0,
      y: 8,
      col: 6.5,
      height: 120,
    })
  })
})
