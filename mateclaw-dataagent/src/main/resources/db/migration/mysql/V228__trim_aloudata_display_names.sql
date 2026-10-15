-- Aloudata 同步表：清洗展示名首尾空格
-- 背景：平台侧展示名可能带首尾空格（复制粘贴/输入法全角空格），
-- 等长精确比对与前缀检索会失配（完整名搜不到、删掉末字符反而命中）。
-- 同步写入侧已改为 trimSpaces 归一（AloudataSemanticSyncServiceImpl），
-- 本迁移清洗存量数据。TRIM 仅处理半角空格，全角空格场景由重新同步覆盖。

UPDATE `dataagent_aloudata_metric`
SET `metric_display_name` = TRIM(`metric_display_name`)
WHERE `metric_display_name` IS NOT NULL
  AND `metric_display_name` <> TRIM(`metric_display_name`);

UPDATE `dataagent_aloudata_dimension`
SET `dim_display_name` = TRIM(`dim_display_name`)
WHERE `dim_display_name` IS NOT NULL
  AND `dim_display_name` <> TRIM(`dim_display_name`);
