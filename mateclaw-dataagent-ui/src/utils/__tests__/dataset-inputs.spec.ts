import { describe, expect, it } from 'vitest'
import {
  assertUniqueDatasetInputAliases,
  assertValidScriptParameters,
  isValidDatasetInputAlias,
  normalizeDatasetInputAlias,
} from '@/utils/dataset-inputs'

describe('dataset input aliases', () => {
  it('accepts stable script aliases and rejects unsafe names', () => {
    expect(isValidDatasetInputAlias('orders')).toBe(true)
    expect(isValidDatasetInputAlias('orders_2026')).toBe(true)
    expect(isValidDatasetInputAlias('_orders')).toBe(false)
    expect(isValidDatasetInputAlias('orders-all')).toBe(false)
    expect(isValidDatasetInputAlias('1_orders')).toBe(false)
  })

  it('normalizes only an empty legacy alias', () => {
    expect(normalizeDatasetInputAlias()).toBe('dataset')
    expect(normalizeDatasetInputAlias('  orders  ')).toBe('orders')
    expect(() => normalizeDatasetInputAlias('orders-all')).toThrow('别名不合法')
  })

  it('rejects duplicate aliases before save or execution', () => {
    expect(() => assertUniqueDatasetInputAliases(['orders', 'users'])).not.toThrow()
    expect(() => assertUniqueDatasetInputAliases(['orders', 'orders'])).toThrow('别名重复')
  })

  it('rejects duplicate or malformed script parameters before execution', () => {
    expect(() => assertValidScriptParameters([
      { name: 'region', type: 'string', scope: 'dashboard' },
      { name: 'region', type: 'string', scope: 'page' },
    ])).toThrow('脚本参数名重复')
    expect(() => assertValidScriptParameters([
      { name: '1region', type: 'string', scope: 'dashboard' },
    ])).toThrow('脚本参数名不合法')
  })

  it('accepts all declared script parameter types and scopes', () => {
    expect(() => assertValidScriptParameters([
      { name: 'keyword', type: 'string', scope: 'dashboard' },
      { name: 'limit', type: 'number', scope: 'page' },
      { name: 'enabled', type: 'boolean', scope: 'component' },
      { name: 'day', type: 'date', scope: 'dashboard' },
      { name: 'at', type: 'datetime', scope: 'page' },
      { name: 'status', type: 'enum', scope: 'dashboard' },
      { name: 'range', type: 'date_range', scope: 'page' },
      { name: 'regions', type: 'string[]', scope: 'component' },
      { name: 'ids', type: 'number[]', scope: 'dashboard' },
    ])).not.toThrow()
  })
})
