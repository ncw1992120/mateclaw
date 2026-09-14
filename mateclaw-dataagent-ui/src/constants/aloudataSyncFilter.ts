/**
 * Aloudata 元数据同步过滤规则（QLExpress 黑名单）相关的共享常量
 * <p>
 * 数据源编辑表单与指标平台面板两处展示同一段规则说明，统一在此维护，避免重复文案漂移。
 */

/**
 * 同步过滤规则输入框的提示文案
 * <p>
 * 重点说明两点易误解的语义：
 * 1. 指标类目树与维度类目树常使用同一套业务域命名，按名称写的规则会同时命中两棵树，
 *    只过滤其中一侧需用 categoryType 显式限定；
 * 2. 指标/维度上下文中的 categoryName 表示「所属类目的名字」，不是指标/维度自身名称。
 */
export const ALOUDATA_SYNC_FILTER_TOOLTIP =
  "元数据同步黑名单（QLExpress 布尔表达式，每行一条，命中任一即不入库持久化；类目命中后同类目树内的子类目一并过滤）。" +
  "注意：指标类目树与维度类目树常同名，按名称写的规则会同时命中两棵树；只想过滤其中一侧请用 categoryType 限定，" +
  "如 categoryType == 'CATEGORY_DIMENSION' && categoryName in ('测试类目')。" +
  "可用变量：类目 categoryName/categoryType/type/parentId，指标 metricName/metricDisplayName/categoryName(所属类目名)/owner/businessOwner 等，" +
  "维度 dimName/dimDisplayName/datasetName/categoryName(所属类目名) 等；data.xxx 可访问任意原始字段。" +
  "示例：categoryName in ('测试类目', '敏感数据')、metricName.startsWith('test_')"
