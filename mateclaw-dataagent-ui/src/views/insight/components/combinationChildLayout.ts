export interface CombinationChildResizeStart {
  direction: string
  startX: number
  startY: number
  originX: number
  originY: number
  originCol: number
  originHeight: number
  bounds?: { width: number; height: number } | null
  columnWidth: number
}

export interface CombinationChildResizeResult {
  x: number
  y: number
  col: number
  height: number
}

function clampBox(
  bounds: CombinationChildResizeStart['bounds'],
  x: number,
  y: number,
  width: number,
  height: number,
  snap: boolean,
): { x: number; y: number } {
  const round = (value: number): number => (snap ? Math.round(value) : value)
  if (!bounds) return { x: Math.max(0, round(x)), y: Math.max(0, round(y)) }
  return {
    x: round(Math.min(Math.max(x, 0), Math.max(0, bounds.width - width))),
    y: round(Math.min(Math.max(y, 0), Math.max(0, bounds.height - height))),
  }
}

/** 列数边界：snap=true 时吸附整列（松手落格），false 时像素连续（拖动预览）。 */
function clampCol(col: number, snap: boolean): number {
  const rounded = snap ? Math.round(col) : col
  return Math.max(1, Math.min(12, rounded))
}

export function calculateCombinationChildResize(
  start: CombinationChildResizeStart,
  last: { x: number; y: number },
  snap = true,
): CombinationChildResizeResult {
  const colW = start.columnWidth || 40
  const dx = last.x - start.startX
  const dy = last.y - start.startY
  let x = start.originX
  let y = start.originY
  let col = start.originCol
  let height = start.originHeight

  if (start.direction.includes('e')) col = clampCol(start.originCol + dx / colW, snap)
  if (start.direction.includes('w')) {
    const next = clampCol(start.originCol - dx / colW, snap)
    x = start.originX + (start.originCol - next) * colW
    col = next
  }
  if (start.direction.includes('s')) height = Math.max(60, start.originHeight + dy)
  if (start.direction.includes('n')) {
    const next = Math.max(60, start.originHeight - dy)
    y = start.originY + (start.originHeight - next)
    height = next
  }

  const box = clampBox(start.bounds, x, y, col * colW, height, snap)
  return { x: box.x, y: box.y, col, height: snap ? Math.round(height) : height }
}
