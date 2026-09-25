import { describe, expect, it } from 'vitest'
import { clampPickerDragOffset, getPickerViewportLayout } from './aloudata-picker-layout'

describe('Aloudata picker viewport layout', () => {
  it('opens above a low trigger and limits the panel to the available viewport height', () => {
    expect(getPickerViewportLayout({ top: 680, bottom: 720 }, 800, 520)).toEqual({
      placement: 'top-start',
      maxHeight: 668,
    })
  })

  it('uses the side with the most room when neither side fits the preferred height', () => {
    expect(getPickerViewportLayout({ top: 500, bottom: 540 }, 900, 520)).toEqual({
      placement: 'top-start',
      maxHeight: 488,
    })
  })

  it('clamps drag movement so the picker remains inside the viewport', () => {
    expect(clampPickerDragOffset(
      { left: 900, right: 1240, top: 650, bottom: 800 },
      { x: 0, y: 0 },
      { x: 120, y: 120 },
      { width: 1280, height: 900 },
    )).toEqual({ x: 32, y: 92 })
  })

  it('preserves the current offset when a previously moved picker is dragged again', () => {
    expect(clampPickerDragOffset(
      { left: 600, right: 950, top: 300, bottom: 700 },
      { x: 30, y: 20 },
      { x: 10, y: 5 },
      { width: 1024, height: 800 },
    )).toEqual({ x: 40, y: 25 })
  })
})
