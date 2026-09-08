/**
 * 5 字段 cron 表达式（分 时 日 月 周）→ 人类可读描述
 *
 * 从 CronExpressionField 组件抽取的公共逻辑，供编辑态（组件内）与
 * 查看态（MetricPlatformPanel 等只读展示）共用，保证两态描述一致。
 */

/** vue-i18n 翻译函数的最小接口（兼容 Composer 的 t） */
type TranslateFn = (key: string, params?: Record<string, unknown>) => string

function pad(n: string | number): string {
  const s = String(n)
  return s.length < 2 ? '0' + s : s
}

function weekLabel(t: TranslateFn, n: number): string {
  return t('cronField.weekShort.' + n)
}

function describeDow(t: TranslateFn, dow: string): string {
  const fmt = (n: string) => weekLabel(t, Number(n) % 7)
  if (/^\d+-\d+$/.test(dow)) {
    const [a, b] = dow.split('-')
    return `${fmt(a)}-${fmt(b)}`
  }
  if (/^\d+(,\d+)+$/.test(dow)) {
    return dow.split(',').map(fmt).join('/')
  }
  if (/^\d+$/.test(dow)) return fmt(dow)
  return dow
}

/**
 * 将 cron 表达式翻译为描述文案；
 * 空表达式或无法识别的格式返回空串（调用方据此决定是否渲染）。
 */
export function describeCron(expr: string, t: TranslateFn): string {
  if (!expr || !expr.trim()) return ''
  const parts = expr.trim().split(/\s+/)
  if (parts.length !== 5) return ''
  const [mi, ho, dom, mon, dow] = parts
  if (parts.every((p) => p === '*')) return t('cronField.desc.everyMinute')
  if (/^\*\/\d+$/.test(mi) && ho === '*' && dom === '*' && mon === '*' && dow === '*') {
    return t('cronField.desc.everyNMin', { n: mi.slice(2) })
  }
  if (mi === '0' && /^\*\/\d+$/.test(ho) && dom === '*' && mon === '*' && dow === '*') {
    return t('cronField.desc.everyNHour', { n: ho.slice(2) })
  }
  if (/^\d+$/.test(mi) && ho === '*' && dom === '*' && mon === '*' && dow === '*') {
    return t('cronField.desc.hourlyAt', { m: mi })
  }
  if (/^\d+$/.test(mi) && /^\d+$/.test(ho)) {
    const time = `${pad(ho)}:${pad(mi)}`
    if (dom === '*' && mon === '*' && dow === '*') {
      return t('cronField.desc.dailyAt', { time })
    }
    if (dom === '*' && mon === '*' && dow !== '*') {
      return t('cronField.desc.weeklyAt', { days: describeDow(t, dow), time })
    }
    if (/^\d+$/.test(dom) && mon === '*' && dow === '*') {
      return t('cronField.desc.monthlyAt', { day: dom, time })
    }
  }
  return ''
}
