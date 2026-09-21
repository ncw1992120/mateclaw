/**
 * 「查看数据」弹窗里的筛选条件：字段 + 操作符 + 取值。
 *
 * 形状与 `DatasetConfig.filters`（InputFilter）**完全一致** —— 换的是产生它的界面，
 * 不是存它的结构。后端 DatasetFilter 的宽容解析能把这里的 `op`（`=` / `!=` / `in` …）
 * 归一成下推用的运算符枚举，所以这里一律存符号写法，不存枚举名。
 */

/** 一条筛选条件（= DatasetConfig.filters 的元素） */
export interface FilterCondition {
  /** 字段名（技术主键；展示名只用于下拉 label，不进这里） */
  field: string
  /** 操作符，符号写法：=、!=、>、>=、<、<=、in、between、contains、is null、is not null */
  op: string
  /** 取值；in / between 用英文逗号分隔多个值 */
  value: string
}

/** 操作符选项：label 是用户看到的写法，value 是落库写法 */
export interface OperatorOption {
  value: string
  label: string
  /** 取值输入框的占位提示；null 表示该操作符不需要取值 */
  valueHint: string | null
}

/** 不需要取值的操作符 */
const NO_VALUE_OPS = ['is null', 'is not null']

/**
 * 维度筛选可用操作符。
 *
 * 不含 `contains` / `is null` —— Aloudata 语义层只接受
 * `= <> > >= < <= IN NotIn AND OR ()`，带上这两个的真实请求会被服务端拒绝
 * （见 AloudataAnalysisViewCompiler#toExpression），所以界面上就不给。
 */
export const DIMENSION_OPERATORS: OperatorOption[] = [
  { value: '=', label: '等于', valueHint: '值' },
  { value: '!=', label: '不等于', valueHint: '值' },
  { value: 'in', label: '包含于', valueHint: '多个值用英文逗号分隔' },
  { value: 'not in', label: '不包含于', valueHint: '多个值用英文逗号分隔' },
  { value: 'between', label: '介于', valueHint: '起始值, 结束值' },
  { value: '>', label: '大于', valueHint: '值' },
  { value: '>=', label: '大于等于', valueHint: '值' },
  { value: '<', label: '小于', valueHint: '值' },
  { value: '<=', label: '小于等于', valueHint: '值' },
]

/** 通用（JDBC / 接口 / 文件）可用操作符：比维度多出「包含文本」与两个空值判断 */
export const GENERIC_OPERATORS: OperatorOption[] = [
  ...DIMENSION_OPERATORS,
  { value: 'contains', label: '包含文本', valueHint: '要包含的文本（无需写 %）' },
  { value: 'is null', label: '为空', valueHint: null },
  { value: 'is not null', label: '不为空', valueHint: null },
]

const TEXT_OPERATORS: OperatorOption[] = [
  { value: '=', label: '等于', valueHint: '值' },
  { value: '!=', label: '不等于', valueHint: '值' },
  { value: 'contains', label: '包含文本', valueHint: '要包含的文本（无需写 %）' },
  { value: 'starts_with', label: '前缀匹配', valueHint: '开头文本' },
  { value: 'ends_with', label: '后缀匹配', valueHint: '结尾文本' },
  { value: 'in', label: '包含于', valueHint: '多个值用英文逗号分隔' },
  { value: 'not in', label: '不包含于', valueHint: '多个值用英文逗号分隔' },
  { value: 'is null', label: '为空', valueHint: null },
  { value: 'is not null', label: '不为空', valueHint: null },
]

const NUMBER_OPERATORS: OperatorOption[] = [
  { value: '=', label: '等于', valueHint: '值' },
  { value: '!=', label: '不等于', valueHint: '值' },
  { value: '>', label: '大于', valueHint: '值' },
  { value: '>=', label: '大于等于', valueHint: '值' },
  { value: '<', label: '小于', valueHint: '值' },
  { value: '<=', label: '小于等于', valueHint: '值' },
  { value: 'between', label: '介于', valueHint: '起始值, 结束值' },
  { value: 'in', label: '包含于', valueHint: '多个值用英文逗号分隔' },
  { value: 'not in', label: '不包含于', valueHint: '多个值用英文逗号分隔' },
  { value: 'is null', label: '为空', valueHint: null },
  { value: 'is not null', label: '不为空', valueHint: null },
]

const ALOUDATA_DATE_OPERATORS: OperatorOption[] = [
  { value: '=', label: '等于', valueHint: '值' },
  { value: '>', label: '大于', valueHint: '值' },
  { value: '>=', label: '大于等于', valueHint: '值' },
  { value: '<', label: '小于', valueHint: '值' },
  { value: '<=', label: '小于等于', valueHint: '值' },
  { value: 'between', label: '介于', valueHint: '起始值, 结束值' },
  { value: 'in', label: '包含于', valueHint: '多个值用英文逗号分隔' },
  { value: 'not in', label: '不包含于', valueHint: '多个值用英文逗号分隔' },
]

/** 按字段类型和数据源能力选择筛选操作符，避免展示无法下推的操作。 */
export function operatorsFor(dataType: string, sourceType: string): OperatorOption[] {
  const normalizedType = dataType.toLowerCase()
  const isAloudata = sourceType === 'ALOUDATA_ANALYSIS_VIEW' || sourceType === 'aloudata'
  if (isAloudata) {
    if (['date', 'datetime', 'timestamp'].includes(normalizedType)) return ALOUDATA_DATE_OPERATORS
    return normalizedType === 'string' ? DIMENSION_OPERATORS.filter((item) => !['>', '>=', '<', '<=', 'between'].includes(item.value)) : DIMENSION_OPERATORS
  }
  if (normalizedType === 'string' || normalizedType === 'text') return TEXT_OPERATORS
  if (['number', 'integer', 'int', 'float', 'double', 'decimal'].includes(normalizedType)) return NUMBER_OPERATORS
  if (['date', 'datetime', 'timestamp'].includes(normalizedType)) return NUMBER_OPERATORS
  return GENERIC_OPERATORS
}

/** 该操作符是否需要用户填值 */
export function needsValue(op: string): boolean {
  return !NO_VALUE_OPS.includes(op)
}

/** 取操作符的取值提示（未知操作符回退成「值」） */
export function valueHintOf(op: string): string {
  const found = [...GENERIC_OPERATORS].find((item) => item.value === op)
  return found?.valueHint ?? '值'
}

const OP_ALIASES: Record<string, string> = {
  '=': '=', '==': '=', eq: '=', equal: '=',
  '!=': '!=', '<>': '!=', neq: '!=',
  '>': '>', gt: '>',
  '>=': '>=', gte: '>=',
  '<': '<', lt: '<',
  '<=': '<=', lte: '<=',
  in: 'in',
  'not in': 'not in', not_in: 'not in', nin: 'not in',
  between: 'between',
  contains: 'contains', has: 'contains', like: 'contains',
  'is null': 'is null', is_null: 'is null', null: 'is null',
  'is not null': 'is not null', is_not_null: 'is not null', not_null: 'is not null',
}

/** 操作符归一：把枚举名 / 符号 / 带下划线的写法收敛到界面写法 */
export function normalizeOperator(op: string | undefined | null): string {
  if (!op) return '='
  const normalized = op.trim().toLowerCase()
  return OP_ALIASES[normalized] ?? OP_ALIASES[normalized.replace(' ', '_')] ?? '='
}

/** 新建一条空条件 —— 字段留空让用户选，操作符给最常用的「等于」 */
export function emptyCondition(): FilterCondition {
  return { field: '', op: '=', value: '' }
}

/** 存量筛选条件 → 可编辑行（数组值用逗号连接，好在单行输入框里编辑） */
export function toCondition(raw: {
  field?: unknown
  op?: unknown
  operator?: unknown
  value?: unknown
}): FilterCondition {
  const value = raw.value
  const text = Array.isArray(value)
    ? value.map((item) => String(item)).join(', ')
    : value === null || value === undefined
      ? ''
      : String(value)
  return {
    field: String(raw.field ?? ''),
    op: normalizeOperator((raw.op ?? raw.operator) as string | undefined),
    value: text,
  }
}

/** 条件是否完整（缺字段或需要取值却没值 → 不完整，查询时直接跳过） */
export function isComplete(condition: FilterCondition): boolean {
  if (!condition.field.trim()) return false
  if (!needsValue(condition.op)) return true
  return condition.value.trim() !== ''
}

/** 只保留完整条件，供查询下推 */
export function completeConditions(list: readonly FilterCondition[]): FilterCondition[] {
  return list
    .filter(isComplete)
    .map((item) => ({ ...item, field: item.field.trim(), op: normalizeOperator(item.op), value: item.value.trim() }))
}

/**
 * 把存量筛选拆成「可编辑行」与「遗留条件」。
 *
 * 字段不在当前可筛选项里（维度被删了、或当初筛的是指标字段）的落到遗留区：
 * 原则是**可见、可清理，绝不静默失效** —— 不让用户在不知情的情况下丢掉已配好的东西。
 *
 * `knownFields` 为空时（可选项还没加载出来）不做归属判断，全部当可编辑行，
 * 避免把已配好的条件误判成遗留。
 */
export function partitionConditions(
  saved: readonly { field?: unknown; op?: unknown; operator?: unknown; value?: unknown }[],
  knownFields: ReadonlySet<string>,
): { rows: FilterCondition[]; legacy: FilterCondition[] } {
  const rows: FilterCondition[] = []
  const legacy: FilterCondition[] = []
  for (const raw of saved) {
    const condition = toCondition(raw)
    if (!condition.field) continue
    if (!knownFields.size || knownFields.has(condition.field)) rows.push(condition)
    else legacy.push(condition)
  }
  return { rows, legacy }
}

/** 过滤掉某字段的条件（用于「移除遗留条件」） */
export function withoutField(list: readonly FilterCondition[], field: string): FilterCondition[] {
  return list.filter((item) => item.field !== field)
}
