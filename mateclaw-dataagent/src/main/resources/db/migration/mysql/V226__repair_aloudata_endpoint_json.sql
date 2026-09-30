-- V226: 修复 MySQL V209 种入的 Aloudata 端点 JSON。
-- MySQL 字符串解析吞掉了 V209 中描述文本的反斜杠转义，导致内嵌双引号破坏整段 JSON。
-- 使用全角引号表达示例文字，避免改动参数值或端点结构。
UPDATE `mate_system_setting`
SET `setting_value` = REPLACE(
        REPLACE(
            REPLACE(
                REPLACE(`setting_value`, '固定值"-1"', '固定值“-1”'),
                '传入"-1""', '传入“-1”"'),
            '"2024-05-11"', '“2024-05-11”'),
        '"2024-05-10"', '“2024-05-10”')
WHERE `setting_key` = 'aloudata.api.endpoints'
  AND JSON_VALID(`setting_value`) = 0
  AND (LOCATE('固定值"-1"', `setting_value`) > 0
       OR LOCATE('传入"-1""', `setting_value`) > 0
       OR LOCATE('"2024-05-11"', `setting_value`) > 0
       OR LOCATE('"2024-05-10"', `setting_value`) > 0);
