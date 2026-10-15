/**
 * Insight 页面不依赖顶部激活模型状态；避免可选模型服务拖慢编辑器真实数据路径。
 */
export function shouldLoadActiveModel(activeNav: string): boolean {
  return activeNav !== 'insight'
}
