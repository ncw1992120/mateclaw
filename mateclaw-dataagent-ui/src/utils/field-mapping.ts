/**
 * field-mapping —— 「字段名称」弹窗的纯逻辑层
 * =====================================================================
 * 数据集的字段映射不再由用户手工新增，而是由数据集 schema 自动生成：
 *   - 字段名（source）：数据集真实字段名（后端预览返回的 schema），只读；
 *   - 字段描述（desc）：字段的真实描述（指标业务口径 / 维度描述），缺失时回退展示名，只读；
 *   - 目标名称（target）：默认取显示名，无显示名时取源字段名，用户可改。
 *
 * 本文件只做确定性计算，不访问后端、不依赖 Vue，便于单测覆盖。
 * 契约见 docs/策略解读/原型设计.md §4.1。
 */

/**
 * 数据集 schema 字段：真实字段名 + 可选展示名与描述。
 * 展示名/描述来自 Aloudata 指标视图的字段详情（metricDisplayName/businessCaliber、
 * dimDisplayName/dimDescription）；role 区分指标（measure）与维度（dimension）。
 */
export interface DatasetSchemaField {
  name: string
  displayName?: string
  description?: string
  /** measure（指标）| dimension（维度），未知时为空 */
  role?: string
}

/** 字段映射行（对应弹窗三列） */
export interface FieldMappingRow {
  source: string
  desc: string
  target: string
}

/** 目标名称默认值：显示名优先，缺省回落源字段名 */
export function defaultTargetOf(field: DatasetSchemaField): string {
  const display = (field.displayName ?? '').trim()
  return display || field.name
}

/**
 * 按最新 schema 生成映射行，并与已有映射做增量合并（按字段名匹配）：
 *   - 用户改过的目标名称保留；
 *   - 未被改动的默认目标名称跟随最新显示名刷新；
 *   - 新增字段追加、消失字段移除，顺序以 schema 为准；
 *   - schema 为空（未取到字段）时原样保留已有映射，不清空用户配置。
 */
export function buildFieldMappingRows(
  schema: DatasetSchemaField[],
  existing: FieldMappingRow[] = [],
): FieldMappingRow[] {
  if (!schema.length) return existing.map((row) => ({ ...row }))
  const bySource = new Map(existing.map((row) => [row.source, row]))
  return schema.map((field) => {
    const prev = bySource.get(field.name)
    const display = (field.displayName ?? '').trim()
    // 「字段描述」列优先展示真实描述（指标业务口径 / 维度描述），缺失时回退展示名
    const desc = (field.description ?? '').trim() || display
    const prevDesc = (prev?.desc ?? '').trim()
    const prevTarget = (prev?.target ?? '').trim()
    // 上一轮的默认值 = 上一轮显示名（旧描述）或源字段名；与之不同即视为用户手工改过
    const prevDefault = prevDesc || field.name
    const userEdited = !!prevTarget && prevTarget !== prevDefault
    return {
      source: field.name,
      desc,
      target: userEdited ? prevTarget : defaultTargetOf(field),
    }
  })
}

/** 目标名称校验：非空且不重复；通过时返回 null，否则返回可展示的错误文案 */
export function validateFieldMappingRows(rows: FieldMappingRow[]): string | null {
  const seen = new Set<string>()
  for (const row of rows) {
    const target = (row.target ?? '').trim()
    if (!target) return `字段「${row.source}」的目标名称不能为空`
    if (seen.has(target)) return `目标名称「${target}」重复，请修改后再保存`
    seen.add(target)
  }
  return null
}

/**
 * 目标名称 → 源字段名反解。
 * 字段重命名目前只落在前端展示层，下推到数据源（JDBC SQL / Aloudata）时必须使用真实字段名，
 * 因此提交筛选条件前要用本函数反解。未命中映射时原样返回。
 */
export function resolveSourceFieldName(rows: FieldMappingRow[], field: string): string {
  const key = (field ?? '').trim()
  if (!key) return field
  const hit = rows.find((row) => (row.target ?? '').trim() === key)
  if (!hit) return field
  return (hit.source ?? '').trim() || field
}

/** 批量反解筛选条件的字段名（保留运算符/取值等其它属性），返回新数组，不改动入参 */
export function toSourceFilters<T extends { field: string }>(
  rows: FieldMappingRow[],
  filters: T[] | undefined,
): T[] {
  if (!filters?.length) return []
  return filters.map((filter) => ({ ...filter, field: resolveSourceFieldName(rows, filter.field) }))
}
