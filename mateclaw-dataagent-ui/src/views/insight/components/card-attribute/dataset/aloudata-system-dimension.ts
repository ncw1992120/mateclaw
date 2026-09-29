import type { AloudataSyncedDimension } from '@/types'

/** Aloudata exposes the system time dimension through metric/dimension relations, not the ordinary directory list. */
export const ALOUDATA_METRIC_TIME_DIMENSION: AloudataSyncedDimension = {
  dimName: 'metric_time',
  dimDisplayName: '指标日期',
  originDataType: 'DATE',
  dimDescription: 'Aloudata 系统指标日期维度',
  synonyms: [],
  configType: 'SYSTEM',
  isTimeDimension: true,
  exampleValues: '',
}

export function matchesAloudataMetricTimeDimension(keyword: string): boolean {
  const normalized = keyword.trim().toLocaleLowerCase()
  return !normalized || `${ALOUDATA_METRIC_TIME_DIMENSION.dimDisplayName} ${ALOUDATA_METRIC_TIME_DIMENSION.dimName}`
    .toLocaleLowerCase()
    .includes(normalized)
}
