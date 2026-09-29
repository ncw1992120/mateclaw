-- 补齐历史数据库缺失的维度值端点；仅追加缺失键，不覆盖管理员已配置的端点。
UPDATE mate_system_setting
SET setting_value = JSON_SET(
        setting_value,
        '$.dimension_values',
        CAST('{"service":"anymetrics","path":"/anymetrics/api/v1/dimension/values","method":"POST","description":"查询维度值，支持关键词和分页。","requestParams":[{"name":"tenant-id","type":"String","required":true,"paramLocation":"HEADER"},{"name":"auth-type","type":"String","required":true,"paramLocation":"HEADER"},{"name":"auth-value","type":"String","required":true,"paramLocation":"HEADER"},{"name":"dimName","type":"String","required":true,"paramLocation":"BODY"},{"name":"dimValueKeyword","type":"String","required":false,"paramLocation":"BODY"},{"name":"pageNumber","type":"Integer","required":false,"defaultValue":1,"paramLocation":"BODY"},{"name":"pageSize","type":"Integer","required":false,"defaultValue":200,"paramLocation":"BODY"}],"responseParams":[{"name":"success","type":"Boolean","required":true},{"name":"data.table","type":"Object","required":true},{"name":"data.table.<dimName>","type":"Array[Array]","required":true}]}' AS JSON)
    ),
    update_time = NOW()
WHERE setting_key = 'aloudata.api.endpoints'
  AND setting_value IS NOT NULL
  AND JSON_VALID(setting_value)
  AND JSON_CONTAINS_PATH(setting_value, 'one', '$.dimension_values') = 0;
