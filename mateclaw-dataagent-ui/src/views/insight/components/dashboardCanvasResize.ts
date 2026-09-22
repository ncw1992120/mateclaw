export type GridResizeEdge = 'top' | 'right' | 'bottom' | 'left'

export interface GridResizeStart {
  edge: GridResizeEdge
  startX: number
  startY: number
  startW: number
  startH: number
  startXPos: number
  startYPos: number
  dx: number
  dy: number
}

export interface GridResizeMetrics {
  gridWidth: number
  columns: number
  marginX: number
  rowHeight: number
  marginY: number
}

export interface GridResizeResult {
  newX: number
  newY: number
  newW: number
  newH: number
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

/** 将鼠标位移换算成栅格尺寸，过程和最终提交共用这一纯函数。 */
export function calculateGridResize(start: GridResizeStart, metrics: GridResizeMetrics): GridResizeResult {
  const columnWidth = (metrics.gridWidth - metrics.marginX * (metrics.columns + 1)) / metrics.columns
  const columnStep = Math.max(1, columnWidth + metrics.marginX)
  const rowStep = Math.max(1, metrics.rowHeight + metrics.marginY)
  const columnDelta = Math.round(start.dx / columnStep)
  const rowDelta = Math.round(start.dy / rowStep)

  let newX = start.startXPos
  let newY = start.startYPos
  let newW = start.startW
  let newH = start.startH

  if (start.edge === 'left') {
    const requestedLeft = start.startXPos + columnDelta
    newX = clamp(requestedLeft, 0, start.startXPos + start.startW - 1)
    newW = start.startXPos + start.startW - newX
  } else if (start.edge === 'right') {
    const requestedRight = start.startXPos + start.startW + columnDelta
    const right = clamp(requestedRight, start.startXPos + 1, metrics.columns)
    newW = right - start.startXPos
  } else if (start.edge === 'top') {
    const requestedTop = start.startYPos + rowDelta
    newY = clamp(requestedTop, 0, start.startYPos + start.startH - 1)
    newH = start.startYPos + start.startH - newY
  } else if (start.edge === 'bottom') {
    newH = Math.max(1, start.startH + rowDelta)
  }

  return { newX, newY, newW, newH }
}
