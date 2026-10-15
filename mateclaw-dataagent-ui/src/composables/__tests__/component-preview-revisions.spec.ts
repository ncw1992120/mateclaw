import { describe, expect, it } from 'vitest'
import { createComponentPreviewRevisions } from '../component-preview-revisions'

describe('组件预览请求版本', () => {
  it('手动渲染产生新版本后，旧自动预览响应不再有效', () => {
    const revisions = createComponentPreviewRevisions()
    const autoPreview = revisions.begin('card-1')
    const manualRender = revisions.begin('card-1')

    expect(revisions.isCurrent('card-1', autoPreview)).toBe(false)
    expect(revisions.isCurrent('card-1', manualRender)).toBe(true)
  })

  it('不同组件的预览版本互不影响', () => {
    const revisions = createComponentPreviewRevisions()
    const cardPreview = revisions.begin('card-1')
    revisions.begin('card-2')

    expect(revisions.isCurrent('card-1', cardPreview)).toBe(true)
  })
})
