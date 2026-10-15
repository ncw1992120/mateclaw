-- V226: 与 MySQL 迁移版本对齐，并兼容修复曾从 MySQL 迁入的错误端点配置。
UPDATE mate_system_setting
SET setting_value = REPLACE(
        REPLACE(
            REPLACE(
                REPLACE(setting_value, '固定值"-1"', '固定值“-1”'),
                '传入"-1""', '传入“-1”"'),
            '"2024-05-11"', '“2024-05-11”'),
        '"2024-05-10"', '“2024-05-10”')
WHERE setting_key = 'aloudata.api.endpoints'
  AND (setting_value LIKE '%固定值"-1"%'
       OR setting_value LIKE '%传入"-1""%'
       OR setting_value LIKE '%"2024-05-11"%'
       OR setting_value LIKE '%"2024-05-10"%');
