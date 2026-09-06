import i18n from '@/i18n'

const { t } = i18n.global

/**
 * 把后端返回的时间戳格式化为相对时间（用于历史会话等紧凑展示）。
 * <p>
 * 不到 1 分钟：刚刚；1-59 分钟：x 分钟前；1-23 小时：x 小时前；
 * 当天但更早：今天 HH:mm；昨天：昨天 HH:mm；7 天内：x 天前；
 * 更早：直接 yyyy-MM-dd。
 */
export function formatRelativeTime(value: string | undefined): string {
  if (!value) return ''
  const date = new Date(value)
  const time = date.getTime()
  if (Number.isNaN(time)) return ''
  const now = Date.now()
  const diff = Math.max(0, now - time)
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour

  if (diff < minute) return t('time.justNow')
  if (diff < hour) return t('time.minutesAgo', { n: Math.floor(diff / minute) })
  if (diff < day) return t('time.hoursAgo', { n: Math.floor(diff / hour) })

  const pad = (n: number): string => n.toString().padStart(2, '0')
  const ymd = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`

  const isSameDay = (a: Date, b: Date): boolean =>
    a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
  const yesterday = new Date(now - day)
  if (isSameDay(date, new Date(now))) {
    return t('time.todayAt', { time: `${pad(date.getHours())}:${pad(date.getMinutes())}` })
  }
  if (isSameDay(date, yesterday)) {
    return t('time.yesterdayAt', { time: `${pad(date.getHours())}:${pad(date.getMinutes())}` })
  }
  if (diff < 7 * day) return t('time.daysAgo', { n: Math.floor(diff / day) })
  return ymd
}
