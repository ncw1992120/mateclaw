import { describe, expect, it } from 'vitest'
import { calculateGridResize } from '../dashboardCanvasResize'

describe('calculateGridResize', () => {
  const metrics = { gridWidth: 800, columns: 24, marginX: 12, rowHeight: 30, marginY: 12 }

  it('uses the rendered grid column step for right-edge resizing', () => {
    expect(calculateGridResize({
      edge: 'right',
      startX: 0,
      startY: 0,
      startW: 4,
      startH: 3,
      startXPos: 0,
      startYPos: 0,
      dx: 100,
      dy: 0,
    }, metrics)).toMatchObject({ newX: 0, newY: 0, newW: 7, newH: 3 })
  })

  it('moves the position while resizing from the left edge', () => {
    expect(calculateGridResize({
      edge: 'left',
      startX: 0,
      startY: 0,
      startW: 8,
      startH: 3,
      startXPos: 8,
      startYPos: 0,
      dx: -100,
      dy: 0,
    }, metrics)).toMatchObject({ newX: 5, newY: 0, newW: 11, newH: 3 })
  })

  it('moves the position while resizing from the top edge', () => {
    expect(calculateGridResize({
      edge: 'top',
      startX: 0,
      startY: 0,
      startW: 8,
      startH: 6,
      startXPos: 0,
      startYPos: 5,
      dx: 0,
      dy: -65,
    }, metrics)).toMatchObject({ newX: 0, newY: 3, newW: 8, newH: 8 })
  })
})
