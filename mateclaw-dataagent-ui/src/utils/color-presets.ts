/**
 * 洞察卡片取色器预设色板（集中管理）
 *
 * 背景：此前 PropertyPanel（组合卡片背景色）与 MetricStyleDialog（指标字段颜色）
 * 各自硬编码一份暖色数组，用途不同但来源分散，改色板要改两处且与设计 token 无关联。
 * 统一收敛到这里，按用途分组导出；后续图表系列色也可在此补充。
 */

/** 卡片/容器背景色 · 常用暖色浅底（奶油白 → 浅驼），配合 el-color-picker 的 predefine 使用 */
export const CARD_BG_PRESETS = [
  '#FFF8F0', // 奶油白
  '#FBF0DC', // 米黄
  '#FAE7CE', // 浅杏
  '#FBE3C8', // 杏橘
  '#FFE3D0', // 浅珊瑚
  '#F6DFD2', // 藕粉
  '#F2E8DA', // 亚麻
  '#EFE0CB', // 浅驼
]

/** 指标值/文本颜色 · 暖色系常用色（红/橙/琥珀/暖黄/暖棕），配合 el-color-picker 的 predefine 使用 */
export const TEXT_COLOR_PRESETS = [
  '#e5484d', // 红
  '#ef4444', // 亮红
  '#f43f5e', // 玫红
  '#f76b15', // 橙
  '#f97316', // 亮橙
  '#fb923c', // 浅橙
  '#f59e0b', // 琥珀
  '#ffb224', // 金黄
  '#ffd60a', // 暖黄
  '#d97706', // 深琥珀
  '#c2410c', // 砖橙
  '#a0522d', // 暖棕
]
