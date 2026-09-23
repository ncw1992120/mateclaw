/**
 * field-mapping —— 数据集「字段注册表」纯逻辑层
 * =====================================================================
 * 契约（定稿见 docs/策略解读/字段名与展示名契约-实施计划.md）：
 *   - **字段名（name）是技术主键**：数据集内唯一、组件生命周期内不可变，
 *     下推 SQL / Python 脚本 / 数据 Join 一律只认它；底层视图变更走 schema diff → 标记失效 → 重绑。
 *   - **展示名（displayName）是表现层标签**：数据集内唯一（可空，渲染回退字段名），
 *     只有「唯一注册表」一份，所有引用处只存字段名，「一处改、处处生效」由单一事实源结构性保证。
 *
 * 注册表由数据集 schema 自动生成（不支持手工新增字段）：
 *   - 字段名：数据集真实字段名（后端预览返回的 schema），只读；
 *   - 字段描述：指标业务口径 / 维度描述，只读；
 *   - 展示名：默认取后端展示名，用户可改，清空即回退字段名。
 *
 * 本文件只做确定性计算，不访问后端、不依赖 Vue，便于单测覆盖。
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

/**
 * 字段注册表条目：字段名不可变，其余为展示元数据。
 * 展示名 / 单位在此唯一登记（字段级），指标配置等投影层只做运行时解析。
 */
export interface DatasetFieldMeta extends DatasetSchemaField {
  /** 单位（字段级展示配置，自 kpiMetrics.unit 收编） */
  unit?: string
  /** 该字段在最新 schema 中已不存在（决策 5：打失效标记、提示重绑，不静默删除用户配置） */
  stale?: boolean
}

/** 存量「字段映射行」（旧契约遗留，仅用于存量归一时的反解输入） */
export interface LegacyFieldMappingRow {
  source: string
  target?: string
}

/** 展示名默认值：后端展示名优先，缺省回落字段名 */
function defaultDisplayNameOf(field: DatasetSchemaField): string {
  const display = (field.displayName ?? '').trim()
  return display || field.name
}

/* ============================ 解析（唯一取值口径） ============================ */

/**
 * 渲染标签：全站唯一的「字段 → 展示文案」取值口径。
 * 命中注册表且展示名非空 → 展示名；否则回退字段名（含未命中 / 空引用）。
 */
export function resolveFieldLabel(fields: DatasetFieldMeta[] | undefined, name: string): string {
  const key = (name ?? '').trim()
  if (!key) return name ?? ''
  const hit = fields?.find((f) => f.name === key)
  const display = (hit?.displayName ?? '').trim()
  return display || key
}

/** 从已持久化映射构建渲染标签；空标签及未映射字段均回退技术字段名。 */
export function fieldLabelsFromMappings(
  mappings: Array<{ source: string; target?: string }> | undefined,
): Record<string, string> {
  const labels: Record<string, string> = {}
  for (const mapping of mappings ?? []) {
    const source = (mapping.source ?? '').trim()
    if (!source) continue
    const target = (mapping.target ?? '').trim()
    labels[source] = target && target !== source ? target : source
  }
  return labels
}

/**
 * 反解：展示名 → 字段名（决策 1 的展示名唯一性保证一一映射，无歧义）。
 * 命中顺序：① 本身就是字段名 → 原样；② 命中某条目的展示名 → 其字段名；③ 未命中 → 原样返回。
 */
export function resolveFieldName(fields: DatasetFieldMeta[] | undefined, ref: string): string {
  const key = (ref ?? '').trim()
  if (!key || !fields?.length) return ref ?? ''
  if (fields.some((f) => f.name === key)) return key
  const hit = fields.find((f) => (f.displayName ?? '').trim() === key)
  return hit ? hit.name : key
}

/* ============================ 校验（展示名唯一） ============================ */

/**
 * 注册表校验（决策 1）：展示名可空（回退字段名），但数据集内不得重复，
 * 且不得与其它字段的字段名冲突（否则反解仍有歧义）。
 * 通过返回 null，否则返回可展示的错误文案。
 */
export function validateFieldMetas(fields: DatasetFieldMeta[]): string | null {
  const byName = new Map(fields.map((f) => [f.name, f]))
  const seen = new Map<string, string>()
  for (const f of fields) {
    const display = (f.displayName ?? '').trim()
    if (!display) continue
    const dup = seen.get(display)
    if (dup) return `展示名「${display}」重复（字段「${dup}」与「${f.name}」），请修改后再保存`
    const owner = byName.get(display)
    if (owner && owner.name !== f.name) {
      return `展示名「${display}」与字段「${owner.name}」的字段名冲突，请修改后再保存`
    }
    seen.set(display, f.name)
  }
  return null
}

/** 返回展示名重复的字段名集合（供弹窗内联标红；空集合表示无重复） */
export function duplicateFieldNames(fields: DatasetFieldMeta[]): Set<string> {
  const byName = new Map(fields.map((f) => [f.name, f]))
  const owner = new Map<string, string>()
  const dups = new Set<string>()
  fields.forEach((f) => {
    const display = (f.displayName ?? '').trim()
    if (!display) return
    const prev = owner.get(display)
    const conflict = prev || (byName.get(display) && byName.get(display)!.name !== f.name ? display : '')
    if (prev) dups.add(prev)
    if (conflict) dups.add(f.name)
    else owner.set(display, f.name)
  })
  return dups
}

/* ============================ 增量合并（schema diff） ============================ */

/**
 * 按最新 schema 增量合并注册表（顺序以 schema 为准）：
 *   - 用户改过的展示名 / 单位保留，未改过的跟随后端最新展示名刷新；
 *   - 描述 / role 始终跟随后端（只读列）；
 *   - 新增字段追加；schema 中消失的字段**不删**，打 stale 标记并排到末尾（决策 5）；
 *   - schema 为空（未取到字段）时原样保留已有注册表，不清空用户配置。
 *
 * @param schema      最新后端字段清单
 * @param existing    已有注册表
 * @param previous    上一次的后端字段清单（可选）。提供时才能判断「用户是否改过展示名」：
 *                    上一版自动填充值 = 上一版后端展示名，与之不同即视为用户手工改过。
 */
export function reconcileFieldMetas(
  schema: DatasetSchemaField[],
  existing: DatasetFieldMeta[] = [],
  previous?: DatasetSchemaField[],
): DatasetFieldMeta[] {
  if (!schema.length) return existing.map((f) => ({ ...f }))
  const prevByName = new Map((previous ?? []).map((f) => [f.name, f]))
  const byName = new Map(existing.map((f) => [f.name, f]))
  const known = new Set<string>()

  const merged: DatasetFieldMeta[] = schema.map((field) => {
    known.add(field.name)
    const prev = byName.get(field.name)
    const display = (field.displayName ?? '').trim()
    // 字段描述列优先展示真实描述（指标业务口径 / 维度描述），缺失时回退展示名
    const description = (field.description ?? '').trim() || display
    const prevDefault = (prevByName.get(field.name)?.displayName ?? '').trim()
    const prevDisplay = (prev?.displayName ?? '').trim()
    // 与上一版自动填充值不同即视为用户手工改过；无上一版可比对时，非空的自定义展示名一律保留
    const userEdited = !!prevDisplay && prevDisplay !== prevDefault
    return {
      name: field.name,
      displayName: userEdited ? prevDisplay : (display || undefined),
      description: description || undefined,
      role: field.role,
      unit: (prev?.unit ?? '').trim() || undefined,
      stale: false,
    }
  })

  // 消失字段：保留配置并打失效标记（不清配置、不静默丢弃用户编辑）
  existing.forEach((f) => {
    if (known.has(f.name)) return
    merged.push({ ...f, stale: true })
  })
  return merged
}

/* ============================ 存量归一（决策 4） ============================ */

/**
 * 存量引用归一：把老配置里存歪的「展示名 / 旧目标名」反解回字段名。
 * 归一顺序：① 精确等于字段名 → 保持；② 等于展示名 → 字段名；
 *          ③ 等于旧 fieldMapping.target → 其 source（字段名）；④ 未命中 → 保留原值并标记 resolved=false。
 */
export function normalizeLegacyFieldRef(
  fields: DatasetFieldMeta[] | undefined,
  legacyRows: LegacyFieldMappingRow[] | undefined,
  ref: string,
): { name: string; resolved: boolean } {
  const key = (ref ?? '').trim()
  if (!key) return { name: ref ?? '', resolved: true }
  if (fields?.length) {
    if (fields.some((f) => f.name === key)) return { name: key, resolved: true }
    const byDisplay = fields.find((f) => (f.displayName ?? '').trim() === key)
    if (byDisplay) return { name: byDisplay.name, resolved: true }
  }
  const legacy = (legacyRows ?? []).find((row) => (row.target ?? '').trim() === key)
  if (legacy) return { name: (legacy.source ?? '').trim() || key, resolved: true }
  return { name: key, resolved: false }
}

/** 批量归一字段引用，返回归一后的值与未识别的引用（保留原顺序，去重） */
export function normalizeLegacyFieldRefs(
  fields: DatasetFieldMeta[] | undefined,
  legacyRows: LegacyFieldMappingRow[] | undefined,
  refs: string[],
): { names: string[]; unresolved: string[] } {
  const names: string[] = []
  const unresolved = new Set<string>()
  refs.forEach((ref) => {
    const { name, resolved } = normalizeLegacyFieldRef(fields, legacyRows, ref)
    names.push(name)
    if (!resolved && name) unresolved.add(name)
  })
  return { names, unresolved: [...unresolved] }
}

/* ============================ 序列化（后端契约） ============================ */

/**
 * 注册表 → 后端 fieldMappings（契约定版，见 §4.3）：
 *   - source = **字段名**，后端下推 SQL 的唯一依据；
 *   - target = **展示名**，仅用于导出表头等展示场景，后端不得用于下推。
 */
export function toFieldMappings(
  fields: DatasetFieldMeta[],
): Array<{ source: string; target: string }> {
  return fields.map((f) => ({ source: f.name, target: resolveFieldLabel(fields, f.name) }))
}

/**
 * 后端 fieldMappings → 注册表（读入老配置）。
 * target 与 source 相同时视为「未设置展示名」（回退字段名），存 undefined 以便跟随后端最新展示名。
 */
export function fieldMetasFromMappings(
  mappings: Array<{ source: string; target?: string }> | undefined,
): DatasetFieldMeta[] {
  const out: DatasetFieldMeta[] = []
  const seen = new Set<string>()
  ;(mappings ?? []).forEach((m) => {
    const name = (m.source ?? '').trim()
    if (!name || seen.has(name)) return
    seen.add(name)
    const target = (m.target ?? '').trim()
    out.push({ name, displayName: target && target !== name ? target : undefined })
  })
  return out
}
