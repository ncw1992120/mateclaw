export type PickerPlacement = 'top-start' | 'bottom-start'

export interface PickerViewportLayout {
  placement: PickerPlacement
  maxHeight: number
}

export interface PickerAnchorBounds {
  top: number
  bottom: number
}

export interface PickerPanelBounds {
  left: number
  right: number
  top: number
  bottom: number
}

export interface PickerOffset {
  x: number
  y: number
}

export function getPickerViewportLayout(
  anchor: PickerAnchorBounds,
  viewportHeight: number,
  preferredHeight: number,
  gutter = 12,
): PickerViewportLayout {
  const topSpace = Math.max(0, anchor.top - gutter)
  const bottomSpace = Math.max(0, viewportHeight - anchor.bottom - gutter)

  if (bottomSpace >= preferredHeight) {
    return { placement: 'bottom-start', maxHeight: bottomSpace }
  }
  if (topSpace >= preferredHeight) {
    return { placement: 'top-start', maxHeight: topSpace }
  }
  return topSpace >= bottomSpace
    ? { placement: 'top-start', maxHeight: topSpace }
    : { placement: 'bottom-start', maxHeight: bottomSpace }
}

export function clampPickerDragOffset(
  panel: PickerPanelBounds,
  current: PickerOffset,
  delta: PickerOffset,
  viewport: { width: number; height: number },
  gutter = 8,
): PickerOffset {
  const minX = gutter - panel.left - current.x
  const maxX = viewport.width - gutter - panel.right - current.x
  const minY = gutter - panel.top - current.y
  const maxY = viewport.height - gutter - panel.bottom - current.y

  return {
    x: current.x + Math.min(maxX, Math.max(minX, delta.x)),
    y: current.y + Math.min(maxY, Math.max(minY, delta.y)),
  }
}
