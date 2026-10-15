import type { FilterCondition } from './filter-conditions'

function text(value: unknown): string {
  return value === null || value === undefined ? '' : String(value)
}

function comparable(value: unknown): number | string {
  if (typeof value === 'number') return value
  const raw = text(value).trim()
  if (raw !== '' && Number.isFinite(Number(raw))) return Number(raw)
  return raw.toLowerCase()
}

function equal(left: unknown, right: string): boolean {
  const a = comparable(left)
  const b = comparable(right)
  return a === b || text(left).trim() === right.trim()
}

function listValues(value: string): string[] {
  return value.split(',').map((item) => item.trim()).filter(Boolean)
}

function matches(row: Record<string, unknown>, condition: FilterCondition): boolean {
  const actual = row[condition.field]
  const expected = condition.value.trim()
  const actualText = text(actual)
  switch (condition.op) {
    case '=': return equal(actual, expected)
    case '!=': return !equal(actual, expected)
    case 'contains': return actualText.toLowerCase().includes(expected.toLowerCase())
    case 'starts_with': return actualText.toLowerCase().startsWith(expected.toLowerCase())
    case 'ends_with': return actualText.toLowerCase().endsWith(expected.toLowerCase())
    case 'in': return listValues(expected).some((item) => equal(actual, item))
    case 'not in': return !listValues(expected).some((item) => equal(actual, item))
    case '>': return comparable(actual) > comparable(expected)
    case '>=': return comparable(actual) >= comparable(expected)
    case '<': return comparable(actual) < comparable(expected)
    case '<=': return comparable(actual) <= comparable(expected)
    case 'between': {
      const [from, to] = listValues(expected)
      return from !== undefined && to !== undefined
        && comparable(actual) >= comparable(from)
        && comparable(actual) <= comparable(to)
    }
    case 'is null': return actual === null || actual === undefined || actualText.trim() === ''
    case 'is not null': return actual !== null && actual !== undefined && actualText.trim() !== ''
    default: return true
  }
}

/** 在已生成的 Python 最终输出上执行查看数据弹窗中的筛选条件。 */
export function applyResultFilters(
  rows: readonly Record<string, unknown>[],
  conditions: readonly FilterCondition[],
): Record<string, unknown>[] {
  return rows.filter((row) => conditions.every((condition) => matches(row, condition)))
}
