import { describe, expect, it } from 'vitest'
import { ALOUDATA_METRIC_TIME_DIMENSION, matchesAloudataMetricTimeDimension } from '../aloudata-system-dimension'

describe('Aloudata system metric-time dimension', () => {
  it('uses the canonical code, Chinese label, and date type', () => {
    expect(ALOUDATA_METRIC_TIME_DIMENSION).toMatchObject({
      dimName: 'metric_time',
      dimDisplayName: '指标日期',
      originDataType: 'DATE',
      isTimeDimension: true,
    })
  })

  it('matches searches by either its display name or technical name', () => {
    expect(matchesAloudataMetricTimeDimension('指标日期')).toBe(true)
    expect(matchesAloudataMetricTimeDimension('metric_time')).toBe(true)
    expect(matchesAloudataMetricTimeDimension('not-a-date')).toBe(false)
    expect(matchesAloudataMetricTimeDimension('')).toBe(true)
  })
})
