# 指标异常检测报告

## 检测概要

- **检测指标**: {{METRIC_NAME}}（{{METRIC_ALIAS}}）
- **检测区间**: {{START_TIME}} ~ {{END_TIME}}（{{GRANULARITY}}，共 {{POINT_COUNT}} 个点，有效 {{VALID_POINT_COUNT}} 个）
- **检测口径**: 方法 {{METHODS}}，判定阈值 {{THRESHOLDS}}，方向 {{DIRECTION}}，基线 {{BASELINE_NOTE}}
- **整体结论**: {{CONCLUSION}}

## 异常明细

按严重度排序，仅列最显著的 {{TOP_N}} 条：

| 时间 | 实际值 | 期望值 | 偏离幅度 | 得分 | 严重度 | 类型 | 命中方法 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| {{TIME}} | {{VALUE}} | {{EXPECTED}} | {{DEVIATION_RATIO}} | {{SCORE}} | {{SEVERITY}} | {{TYPE}} | {{METHODS}} |

## 异常片段

连续异常点已合并为片段，持续位移（LEVEL_SHIFT）业务影响大于单点脉冲：

| 片段 | 起止 | 点数 | 类型 | 严重度 | 平均偏离幅度 |
| --- | --- | --- | --- | --- | --- |
| {{EPISODE_NO}} | {{EPISODE_START}} ~ {{EPISODE_END}} | {{EPISODE_LENGTH}} | {{EPISODE_TYPE}} | {{EPISODE_SEVERITY}} | {{EPISODE_DEVIATION}} |

## 根因分析

{{ROOT_CAUSE}}

> 未做维度下钻时填写："本次仅完成异常定位，未做维度根因下钻。"

## 建议动作

{{RECOMMENDATIONS}}

## 数据质量说明

- **缺失情况**: {{MISSING_NOTE}}
- **样本量是否满足**: {{SAMPLE_NOTE}}
- **其他提示**: {{DATA_QUALITY_WARNINGS}}