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

/** 列/行步进（舞台像素）：换算与呈现共用，避免两处公式漂移。 */
export function gridSteps(metrics: GridResizeMetrics): { columnStep: number; rowStep: number } {
  const columnWidth = (metrics.gridWidth - metrics.marginX * (metrics.columns + 1)) / metrics.columns
  return {
    columnStep: Math.max(1, columnWidth + metrics.marginX),
    rowStep: Math.max(1, metrics.rowHeight + metrics.marginY),
  }
}

/** 将鼠标位移换算成栅格尺寸，过程和最终提交共用这一纯函数。 */
export function calculateGridResize(start: GridResizeStart, metrics: GridResizeMetrics): GridResizeResult {
  const { columnStep, rowStep } = gridSteps(metrics)
  // w/x 使用连续列坐标，GridItem 会把其换算成像素盒；避免横向每跨整列才跳一次。
  const columnDelta = start.dx / columnStep
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

  return { newX: precise(newX), newY, newW: precise(newW), newH }
}

/** 限制浮点误差，布局在反复拖动及 JSON 保存/重载后保持稳定。 */
function precise(value: number): number {
  return Math.round(value * 10000) / 10000
}

/** 栅格项对应的像素盒（与 grid-layout-plus 的取整公式保持一致）。 */
export function gridItemPixelBox(w: number, h: number, metrics: GridResizeMetrics): { width: number; height: number } {
  const columnWidth = (metrics.gridWidth - metrics.marginX * (metrics.columns + 1)) / metrics.columns
  return {
    width: Math.max(1, Math.round(columnWidth * w + Math.max(0, w - 1) * metrics.marginX)),
    height: Math.max(1, Math.round(metrics.rowHeight * h + Math.max(0, h - 1) * metrics.marginY)),
  }
}

/**
 * 拖动中的连续预览：按最终鼠标点计算浮点列宽并按 GridItem 公式转换为像素盒；
 * 松手时写入相同布局值，避免横向整列吸附和预览/落点公式差异造成跳变。
 * left/top 方向的位移用负 margin 让卡片向反方向生长。
 */
export function calculateResizePreview(
  start: Pick<GridResizeStart, 'edge' | 'startW' | 'startH' | 'startXPos' | 'startYPos' | 'dx' | 'dy'>,
  metrics: GridResizeMetrics
): Record<string, string> {
  const result = calculateGridResize({ ...start, startX: 0, startY: 0 }, metrics)
  const { columnStep, rowStep } = gridSteps(metrics)
  const box = gridItemPixelBox(result.newW, result.newH, metrics)
  const gridItemOffset = (position: number, size: number, margin: number): number =>
    Math.round(position * size + (position + 1) * margin)
  return {
    width: `${box.width}px`,
    height: `${box.height}px`,
    marginLeft: `${gridItemOffset(result.newX, columnStep - metrics.marginX, metrics.marginX) - gridItemOffset(start.startXPos, columnStep - metrics.marginX, metrics.marginX)}px`,
    marginTop: `${gridItemOffset(result.newY, rowStep - metrics.marginY, metrics.marginY) - gridItemOffset(start.startYPos, rowStep - metrics.marginY, metrics.marginY)}px`,
  }
}
