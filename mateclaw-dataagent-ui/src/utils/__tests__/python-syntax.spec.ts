import { describe, expect, it } from 'vitest'
import { highlightPython } from '../python-syntax'

describe('highlightPython', () => {
  it('highlights Python keywords and built-ins while escaping source text', () => {
    const html = highlightPython('def load(value):\n    return len(value) < 2')

    expect(html).toContain('hljs-keyword')
    expect(html).toContain('hljs-built_in')
    expect(html).toContain('&lt;')
    expect(html).toContain('hljs-number')
  })
})
