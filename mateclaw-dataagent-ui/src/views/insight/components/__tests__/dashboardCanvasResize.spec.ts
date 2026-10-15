import { describe, expect, it } from 'vitest'
import { calculateGridResize } from '../dashboardCanvasResize'

describe('calculateGridResize', () => {
  const metrics = { gridWidth: 800, columns: 24, marginX: 12, rowHeight: 30, marginY: 12 }

  it('uses the rendered grid column step for right-edge resizing', () => {
    const result = calculateGridResize({
      edge: 'right',
      startX: 0,
      startY: 0,
      startW: 4,
      startH: 3,
      startXPos: 0,
      startYPos: 0,
      dx: 100,
      dy: 0,
    }, metrics)
    expect(result).toMatchObject({ newX: 0, newY: 0, newH: 3 })
    expect(result.newW).toBeCloseTo(4 + 100 / ((metrics.gridWidth - metrics.marginX) / metrics.columns), 4)
  })

  it('keeps horizontal resize proportional to pointer movement instead of snapping by whole columns', () => {
    const start = {
      edge: 'right' as const,
      startX: 0,
      startY: 0,
      startW: 4,
      startH: 3,
      startXPos: 0,
      startYPos: 0,
      dx: 16,
      dy: 0,
    }

    const result = calculateGridResize(start, metrics)
    const columnStep = (metrics.gridWidth - metrics.marginX) / metrics.columns
    expect(result.newW).toBeCloseTo(4 + 16 / columnStep, 4)
    expect(result.newW).toBeGreaterThan(4)
    expect(result.newW).toBeLessThan(5)
  })

  it('moves the position while resizing from the left edge', () => {
    const result = calculateGridResize({
      edge: 'left',
      startX: 0,
      startY: 0,
      startW: 8,
      startH: 3,
      startXPos: 8,
      startYPos: 0,
      dx: -100,
      dy: 0,
    }, metrics)
    expect(result).toMatchObject({ newY: 0, newH: 3 })
    expect(result.newX).toBeCloseTo(8 - 100 / ((metrics.gridWidth - metrics.marginX) / metrics.columns), 4)
    expect(result.newW).toBeCloseTo(8 + 100 / ((metrics.gridWidth - metrics.marginX) / metrics.columns), 4)
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
