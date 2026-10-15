import { describe, expect, it } from 'vitest'
import { shouldLoadActiveModel } from '../mainLayoutModelLoad'

describe('MainLayout model loading', () => {
  it('skips the optional active-model request on insight routes', () => {
    expect(shouldLoadActiveModel('insight')).toBe(false)
  })

  it('keeps active-model loading for non-insight routes', () => {
    expect(shouldLoadActiveModel('smart-ask')).toBe(true)
  })
})
