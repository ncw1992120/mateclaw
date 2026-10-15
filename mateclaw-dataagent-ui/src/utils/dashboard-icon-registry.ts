import type { Component } from 'vue'
import {
  Aim, Calendar, Collection, Coin, Connection, CreditCard, DataAnalysis, DataLine, Histogram, Monitor,
  Money, Odometer, Operation, PieChart, Promotion, Setting, ShoppingCart, Star, Tickets, Timer, TrendCharts,
  User, UserFilled,
} from '@element-plus/icons-vue'

export interface DashboardIconDefinition {
  key: string
  label: string
  component: Component
}

export const DASHBOARD_ICON_REGISTRY: DashboardIconDefinition[] = [
  { key: 'trend-charts', label: '趋势', component: TrendCharts },
  { key: 'chart-bar', label: '图表', component: Histogram },
  { key: 'data-analysis', label: '分析', component: DataAnalysis },
  { key: 'data-line', label: '数据线', component: DataLine },
  { key: 'histogram', label: '柱状图', component: Histogram },
  { key: 'pie-chart', label: '饼图', component: PieChart },
  { key: 'odometer', label: '仪表盘', component: Odometer },
  { key: 'aim', label: '目标', component: Aim },
  { key: 'connection', label: '关联', component: Connection },
  { key: 'collection', label: '集合', component: Collection },
  { key: 'coin', label: '金币', component: Coin },
  { key: 'credit-card', label: '卡片', component: CreditCard },
  { key: 'money', label: '金额', component: Money },
  { key: 'shopping-cart', label: '订单', component: ShoppingCart },
  { key: 'user', label: '用户', component: User },
  { key: 'user-filled', label: '用户组', component: UserFilled },
  { key: 'setting', label: '设置', component: Setting },
  { key: 'operation', label: '操作', component: Operation },
  { key: 'monitor', label: '监控', component: Monitor },
  { key: 'promotion', label: '增长', component: Promotion },
  { key: 'star', label: '重点', component: Star },
  { key: 'timer', label: '时效', component: Timer },
  { key: 'calendar', label: '日期', component: Calendar },
  { key: 'tickets', label: '清单', component: Tickets },
]

const registry = new Map(DASHBOARD_ICON_REGISTRY.map((item) => [item.key, item.component]))

export function resolveDashboardIcon(iconKey: string | null | undefined): Component | null {
  if (!iconKey || !/^[a-z-]+$/.test(iconKey)) return null
  return registry.get(iconKey) ?? null
}
