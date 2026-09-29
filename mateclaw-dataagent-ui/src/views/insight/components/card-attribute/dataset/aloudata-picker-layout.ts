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

export type PickerResizeHandle = 'nw' | 'ne' | 'sw' | 'se'

export interface ResizedPickerBounds extends PickerPanelBounds {
  width: number
  height: number
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

export function resizePickerBounds(
  panel: PickerPanelBounds,
  handle: PickerResizeHandle,
  delta: PickerOffset,
  viewport: { width: number; height: number },
  minWidth = 280,
  minHeight = 260,
  gutter = 8,
): ResizedPickerBounds {
  const resizeWest = handle.endsWith('w')
  const resizeNorth = handle.startsWith('n')
  const fixedX = resizeWest ? panel.right : panel.left
  const fixedY = resizeNorth ? panel.bottom : panel.top
  const maxWidth = Math.max(0, resizeWest ? fixedX - gutter : viewport.width - gutter - fixedX)
  const maxHeight = Math.max(0, resizeNorth ? fixedY - gutter : viewport.height - gutter - fixedY)
  const requestedWidth = panel.right - panel.left + (resizeWest ? -delta.x : delta.x)
  const requestedHeight = panel.bottom - panel.top + (resizeNorth ? -delta.y : delta.y)
  const width = Math.min(maxWidth, Math.max(Math.min(minWidth, maxWidth), requestedWidth))
  const height = Math.min(maxHeight, Math.max(Math.min(minHeight, maxHeight), requestedHeight))
  const left = resizeWest ? fixedX - width : fixedX
  const top = resizeNorth ? fixedY - height : fixedY

  return {
    left,
    right: left + width,
    top,
    bottom: top + height,
    width,
    height,
  }
}
