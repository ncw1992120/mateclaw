/** 为每个组件追踪最新预览请求，避免旧响应覆盖较新的手动渲染结果。 */
export function createComponentPreviewRevisions() {
  const revisions = new Map<string, number>()

  return {
    begin(componentId: string): number {
      const revision = (revisions.get(componentId) ?? 0) + 1
      revisions.set(componentId, revision)
      return revision
    },
    isCurrent(componentId: string, revision: number): boolean {
      return revisions.get(componentId) === revision
    },
  }
}
