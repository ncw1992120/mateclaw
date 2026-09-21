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
): { x: number; y: number } {
  if (!bounds) return { x: Math.max(0, Math.round(x)), y: Math.max(0, Math.round(y)) }
  return {
    x: Math.round(Math.min(Math.max(x, 0), Math.max(0, bounds.width - width))),
    y: Math.round(Math.min(Math.max(y, 0), Math.max(0, bounds.height - height))),
  }
}

function clampCol(col: number): number {
  return Math.max(1, Math.min(12, Math.round(col)))
}

export function calculateCombinationChildResize(
  start: CombinationChildResizeStart,
  last: { x: number; y: number },
): CombinationChildResizeResult {
  const colW = start.columnWidth || 40
  const dx = last.x - start.startX
  const dy = last.y - start.startY
  let x = start.originX
  let y = start.originY
  let col = start.originCol
  let height = start.originHeight

  if (start.direction.includes('e')) col = clampCol(start.originCol + dx / colW)
  if (start.direction.includes('w')) {
    const next = clampCol(start.originCol - dx / colW)
    x = start.originX + (start.originCol - next) * colW
    col = next
  }
  if (start.direction.includes('s')) height = Math.max(60, start.originHeight + dy)
  if (start.direction.includes('n')) {
    const next = Math.max(60, start.originHeight - dy)
    y = start.originY + (start.originHeight - next)
    height = next
  }

  const box = clampBox(start.bounds, x, y, col * colW, height)
  return { x: box.x, y: box.y, col, height: Math.round(height) }
}
