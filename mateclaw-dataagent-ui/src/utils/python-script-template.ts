import type { DashboardDatasetInput, DashboardScriptFilterBinding, DashboardSystemScriptState } from '@/types'
import { buildSystemScript } from './script-template'

export interface PythonSystemSource {
  inputs: DashboardDatasetInput[]
  bindings: DashboardScriptFilterBinding[]
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
  return buildSystemScript(source.inputs, [], source.bindings)
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
