/**
 * 数据集输入别名约束与兼容转换。
 *
 * 别名是脚本读取数据集时的稳定契约，不把数据库名称或租户名称暴露给脚本。
 */
const INPUT_ALIAS_PATTERN = /^[A-Za-z][A-Za-z0-9_]{0,63}$/
const SCRIPT_PARAMETER_TYPES = new Set(['string', 'number', 'boolean', 'date', 'datetime', 'enum', 'date_range', 'string[]', 'number[]'])
const SCRIPT_PARAMETER_SCOPES = new Set(['dashboard', 'page', 'component'])

export function isValidDatasetInputAlias(alias: string): boolean {
  return INPUT_ALIAS_PATTERN.test(alias)
}

export function assertUniqueDatasetInputAliases(aliases: string[]): void {
  const seen = new Set<string>()
  for (const alias of aliases) {
    if (!isValidDatasetInputAlias(alias)) {
      throw new Error(`数据集输入别名不合法: ${alias}`)
    }
    if (seen.has(alias)) {
      throw new Error(`数据集输入别名重复: ${alias}`)
    }
    seen.add(alias)
  }
}

/** 将旧配置中的空别名规范为默认输入名，避免破坏已有单数据集配置。 */
export function normalizeDatasetInputAlias(alias?: string | null): string {
  const value = alias?.trim() || 'dataset'
  if (!isValidDatasetInputAlias(value)) {
    throw new Error(`数据集输入别名不合法: ${value}`)
  }
  return value
}

/** 校验脚本参数定义，确保执行前参数契约稳定且不会产生重复键。 */
export function assertValidScriptParameters(
  parameters: Array<{ name?: unknown; type?: unknown; scope?: unknown }>,
): void {
  const seen = new Set<string>()
  for (const parameter of parameters) {
    const name = typeof parameter.name === 'string' ? parameter.name.trim() : ''
    if (!isValidDatasetInputAlias(name)) {
      throw new Error(`脚本参数名不合法: ${name}`)
    }
    if (seen.has(name)) {
      throw new Error(`脚本参数名重复: ${name}`)
    }
    if (!SCRIPT_PARAMETER_TYPES.has(String(parameter.type))) {
      throw new Error(`脚本参数类型不支持: ${String(parameter.type)}`)
    }
    if (!SCRIPT_PARAMETER_SCOPES.has(String(parameter.scope))) {
      throw new Error(`脚本参数作用范围不支持: ${String(parameter.scope)}`)
    }
    seen.add(name)
  }
}
