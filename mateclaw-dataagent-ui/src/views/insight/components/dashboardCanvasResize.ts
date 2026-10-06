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

/** 栅格项对应的像素盒（相对其 GridItem 定位原点，即卡片内容应呈现的宽高）。 */
export function gridItemPixelBox(w: number, h: number, metrics: GridResizeMetrics): { width: number; height: number } {
  const columnWidth = (metrics.gridWidth - metrics.marginX * (metrics.columns + 1)) / metrics.columns
  const columnStep = Math.max(1, columnWidth + metrics.marginX)
  const rowStep = Math.max(1, metrics.rowHeight + metrics.marginY)
  return { width: Math.max(1, w * columnStep - metrics.marginX), height: Math.max(1, h * rowStep - metrics.marginY) }
}

/**
 * 拖拽中的像素级预览盒：完全跟随鼠标（无吸附），不触碰网格布局。
 * 返回应用在卡片内容上的 CSS 盒模型增量（left/top 方向用负 margin 让卡片向反方向生长）。
 */
export function calculateResizePreview(
  start: Pick<GridResizeStart, 'edge' | 'startW' | 'startH' | 'dx' | 'dy'>,
  metrics: GridResizeMetrics
): Record<string, string> {
  const base = gridItemPixelBox(start.startW, start.startH, metrics)
  const style: Record<string, string> = {
    width: `${base.width}px`,
    height: `${base.height}px`,
    marginLeft: '0px',
    marginTop: '0px',
  }
  if (start.edge === 'right') {
    style.width = `${Math.max(1, base.width + start.dx)}px`
  } else if (start.edge === 'left') {
    style.width = `${Math.max(1, base.width - start.dx)}px`
    style.marginLeft = `${start.dx}px`
  } else if (start.edge === 'bottom') {
    style.height = `${Math.max(1, base.height + start.dy)}px`
  } else {
    style.height = `${Math.max(1, base.height - start.dy)}px`
    style.marginTop = `${start.dy}px`
  }
  return style
}
