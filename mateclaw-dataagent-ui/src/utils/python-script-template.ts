import type { DashboardDatasetInput, DashboardScriptFilterBinding, DashboardSystemScriptState } from '@/types'
import { buildSystemScript, SYSTEM_SCRIPT_END, SYSTEM_SCRIPT_START } from './script-template'

export const PYTHON_EDITOR_USER_MARKER = '# ===== 自定义处理代码（可编辑） ====='
export const PYTHON_EDITOR_OUTPUT_MARKER = '# ===== 输出结果示例（参考） ====='

export interface PythonSystemSource {
  inputs: DashboardDatasetInput[]
  bindings: DashboardScriptFilterBinding[]
  queryParameters?: Array<{ field: string; title: string; parameterName: string; operators: string[] }>
  outputContract?: {
    kind: string
    example: string
    fieldRules: { minColumns: number; minDimensionColumns: number; minNumericColumns: number }
  }
}

export function buildPythonEditorDocument(
  source: PythonSystemSource,
  userCode = '',
  samplesByInputName: Record<string, Record<string, unknown>[]> = {},
): string {
  const system = buildSystemScript(source.inputs, [], source.bindings, undefined, samplesByInputName, source.queryParameters)
    .replace(SYSTEM_SCRIPT_START, '# ===== 造数示例与上游数据读取 =====')
    .replace(SYSTEM_SCRIPT_END, '# ===== 数据读取结束 =====')
  const outputExample = source.outputContract?.example
    ? source.outputContract.example.split('\n').map((line) => `# ${line}`).join('\n')
    : '# 当前组件暂无输出示例。'
  return [
    system,
    PYTHON_EDITOR_USER_MARKER,
    userCode.trim(),
    PYTHON_EDITOR_OUTPUT_MARKER,
    '# 示例仅用于参考；实际输出仍按组件契约校验。',
    outputExample,
  ].join('\n\n')
}

export function parsePythonEditorDocument(document: string): { systemCode: string; userCode: string } {
  const userMarkerIndex = document.indexOf(PYTHON_EDITOR_USER_MARKER)
  if (userMarkerIndex < 0) return { systemCode: '', userCode: document.trim() }

  const outputMarkerIndex = document.indexOf(PYTHON_EDITOR_OUTPUT_MARKER, userMarkerIndex + PYTHON_EDITOR_USER_MARKER.length)
  const systemCode = document.slice(0, userMarkerIndex).trim()
  const userStart = userMarkerIndex + PYTHON_EDITOR_USER_MARKER.length
  const userCode = document.slice(userStart, outputMarkerIndex >= 0 ? outputMarkerIndex : undefined).trim()
  return { systemCode, userCode }
}

export interface ReconciledSystemScript extends DashboardSystemScriptState {
  hasGeneratedUpdate: boolean
}

const USER_REGION_MARKER = '# ===== 用户处理区域 ====='

function stableValue(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(stableValue)
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.keys(value as Record<string, unknown>).sort().map((key) => [key, stableValue((value as Record<string, unknown>)[key])]))
  }
  return value
}

function sourceJson(source: PythonSystemSource): string {
  return JSON.stringify(stableValue(source))
}

export function fingerprintSystemSource(source: PythonSystemSource): string {
  let hash = 2166136261
  for (const char of sourceJson(source)) {
    hash ^= char.charCodeAt(0)
    hash = Math.imul(hash, 16777619)
  }
  return (hash >>> 0).toString(16).padStart(8, '0')
}

export function generateSystemScript(source: PythonSystemSource): string {
  return buildSystemScript(source.inputs, [], source.bindings, source.outputContract)
}

export function effectiveSystemCode(state: DashboardSystemScriptState): string {
  return state.mode === 'managed' ? (state.managedCode || state.generatedCode) : state.generatedCode
}

export function composeExecutionScript(state: DashboardSystemScriptState): string {
  const system = effectiveSystemCode(state).trim()
  const user = state.userCode.trim()
  return [system, user ? `${USER_REGION_MARKER}\n${user}` : USER_REGION_MARKER].filter(Boolean).join('\n\n').trim()
}

export function reconcileSystemScript(state: DashboardSystemScriptState, source: PythonSystemSource): ReconciledSystemScript {
  const generatedCode = generateSystemScript(source)
  const generatedFingerprint = fingerprintSystemSource(source)
  return {
    ...state,
    generatedCode,
    generatedFingerprint,
    hasGeneratedUpdate: state.generatedFingerprint !== generatedFingerprint,
  }
}

export function restoreGenerated(state: DashboardSystemScriptState): ReconciledSystemScript {
  return { ...state, mode: 'generated', managedCode: undefined, hasGeneratedUpdate: false }
}
