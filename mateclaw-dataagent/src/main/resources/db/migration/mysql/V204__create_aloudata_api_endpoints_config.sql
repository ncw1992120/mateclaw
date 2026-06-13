-- ============================================================
-- Aloudata API 端点配置：含请求/响应参数规范
-- ============================================================
-- API 版本升级时，只需修改 aloudata.api.endpoints 和 aloudata.api.version 的值即可，
-- 无需改动代码。端点配置为 JSON 格式，包含端点名称、所属服务、路径、HTTP 方法、描述，
-- 以及完整的 requestParams 和 responseParams 参数规范定义。
--
-- 参数规范说明：
-- - name: 参数名称
-- - type: 数据类型（String/Integer/Long/Boolean/Array/Map/Object）
-- - required: 是否必填
-- - defaultValue: 默认值（字符串表示）
-- - description: 参数说明
-- - paramLocation: 传递方式（HEADER/PATH/QUERY/BODY），仅请求参数适用
-- - enumValues: 可选取值范围（逗号分隔）

INSERT INTO `mate_system_setting` (`id`, `setting_key`, `setting_value`, `description`, `create_time`, `update_time`)
VALUES (
    2001,
    'aloudata.api.version',
    'v1',
    'Aloudata API 版本号',
    NOW(),
    NOW()
) ON DUPLICATE KEY UPDATE `setting_value` = VALUES(`setting_value`), `update_time` = NOW();

INSERT INTO `mate_system_setting` (`id`, `setting_key`, `setting_value`, `description`, `create_time`, `update_time`)
VALUES (
    2002,
    'aloudata.api.endpoints',
    '{
  "metrics_list": {
    "service":"anymetrics",
    "path":"/anymetrics/api/v1/metrics/list",
    "method":"GET",
    "description":"获取某个租户下所有的指标列表",
    "requestParams":[
      {"name":"tenant-id","type":"String","required":true,"description":"租户ID","paramLocation":"HEADER"},
      {"name":"auth-type","type":"String","required":true,"description":"认证方式","paramLocation":"HEADER","enumValues":"UID,TOKEN,ACCOUNT,APIKEY"},
      {"name":"auth-value","type":"String","required":true,"description":"与auth-type对应的认证值","paramLocation":"HEADER"},
      {"name":"query-user-account","type":"String","required":false,"description":"鉴权用户名，为空则使用auth-value对应用户","paramLocation":"HEADER"},
      {"name":"keyword","type":"String","required":false,"description":"搜索关键词","paramLocation":"QUERY"},
      {"name":"metricCategoryId","type":"String","required":false,"description":"指标所属类目ID，-1表示未分类","paramLocation":"QUERY"},
      {"name":"statusFilters","type":"Array","required":false,"description":"指标状态过滤，枚举：UNPUBLISHED/PUBLISHED/SAVED_NOT_PUBLISHED/OFFLINE/PENDING_PUBLISH/PENDING_OFFLINE/PENDING_DELETE","paramLocation":"QUERY"},
      {"name":"pageNumber","type":"Integer","required":false,"defaultValue":"1","description":"当前页码","paramLocation":"QUERY"},
      {"name":"pageSize","type":"Integer","required":false,"defaultValue":"20","description":"每页大小","paramLocation":"QUERY"}
    ],
    "responseParams":[
      {"name":"code","type":"String","required":true,"description":"接口响应码"},
      {"name":"success","type":"Boolean","required":true,"description":"请求是否成功"},
      {"name":"errorMsg","type":"String","required":true,"description":"报错信息"},
      {"name":"traceId","type":"String","required":true,"description":"追踪ID"},
      {"name":"data","type":"Object","required":true,"description":"响应结果（含total/pageNumber/pageSize/hasNext/data列表）"},
      {"name":"data.data[].code","type":"String","required":false,"description":"指标编码（系统内部生成）"},
      {"name":"data.data[].metricName","type":"String","required":true,"description":"指标名称（英文名）"},
      {"name":"data.data[].metricDisplayName","type":"String","required":true,"description":"指标展示名"},
      {"name":"data.data[].type","type":"String","required":true,"description":"指标类型：ATOMIC/derived/composite"},
      {"name":"data.data[].businessCaliber","type":"String","required":false,"description":"指标描述信息"},
      {"name":"data.data[].owner","type":"String","required":true,"description":"指标负责人"},
      {"name":"data.data[].businessOwner","type":"String","required":true,"description":"业务负责人"},
      {"name":"data.data[].metricCategoryId","type":"String","required":true,"description":"指标类目ID"},
      {"name":"data.data[].status","type":"String","required":true,"description":"指标终态：ONLINE/OFFLINE"},
      {"name":"data.data[].publishStatus","type":"String","required":true,"description":"发布状态：DRAFT/PUBLISHED"},
      {"name":"data.data[].unit","type":"String","required":false,"description":"指标单位枚举值"}
    ]
  },
  "metrics_query": {
    "service":"semantic",
    "path":"/semantic/api/v1.1/metrics/query",
    "method":"POST",
    "description":"使用指标和维度组合，查询指定的指标计算结果",
    "requestParams":[
      {"name":"tenant-id","type":"String","required":true,"description":"租户ID","paramLocation":"HEADER"},
      {"name":"auth-type","type":"String","required":true,"description":"认证方式","paramLocation":"HEADER","enumValues":"UID,TOKEN,ACCOUNT,APIKEY"},
      {"name":"auth-value","type":"String","required":true,"description":"与auth-type对应的认证值","paramLocation":"HEADER"},
      {"name":"query-user-account","type":"String","required":false,"description":"鉴权用户名，为空则使用auth-value对应用户","paramLocation":"HEADER"},
      {"name":"metrics","type":"Array[String]","required":true,"description":"查询指标列表，支持直接引用、快速计算（同环比/占比/排名/时间限定）","paramLocation":"BODY"},
      {"name":"metricDefinitions","type":"Map","required":false,"description":"临时指标定义，key为临时指标名，value包含refMetric和specifyDimension","paramLocation":"BODY"},
      {"name":"dimensions","type":"Array[String]","required":false,"description":"查询维度列表，支持日期粒度切换（如metric_time__day）","paramLocation":"BODY"},
      {"name":"filters","type":"Array[String]","required":false,"description":"全局筛选，对全部指标进行维度数据过滤","paramLocation":"BODY"},
      {"name":"resultFilters","type":"Array[String]","required":false,"description":"结果筛选，对查询结果进行筛选","paramLocation":"BODY"},
      {"name":"timeConstraint","type":"String","required":false,"description":"指标日期范围，如 ([metric_time__month]= DateTrunc(Today(),\"MONTH\"))","paramLocation":"BODY"},
      {"name":"orders","type":"Map","required":false,"description":"排序，内容需包含在metrics或dimensions中","paramLocation":"BODY"},
      {"name":"limit","type":"Integer","required":false,"defaultValue":"100","description":"返回结果条数","paramLocation":"BODY"},
      {"name":"offset","type":"Integer","required":false,"defaultValue":"1","description":"返回结果偏移量","paramLocation":"BODY"},
      {"name":"queryResultType","type":"String","required":false,"defaultValue":"SQL_AND_DATA","description":"返回数据类型：SQL_AND_DATA/SQL/DATA","paramLocation":"BODY"},
      {"name":"source","type":"String","required":false,"description":"数据查询来源标识（自定义）","paramLocation":"BODY"},
      {"name":"isQueryTotalCount","type":"Boolean","required":false,"defaultValue":"false","description":"是否返回数据总条数","paramLocation":"BODY"},
      {"name":"specialMvConfig","type":"Map","required":false,"description":"物化加速配置，控制是否启用指定物化表加速","paramLocation":"BODY"}
    ],
    "responseParams":[
      {"name":"success","type":"Boolean","required":true,"description":"查询是否成功"},
      {"name":"code","type":"String","required":true,"description":"接口响应码"},
      {"name":"message","type":"String","required":false,"description":"报错信息"},
      {"name":"traceId","type":"String","required":true,"description":"追踪ID"},
      {"name":"data.queryId","type":"String","required":true,"description":"查询ID"},
      {"name":"data.sql","type":"String","required":false,"description":"查询SQL（queryResultType含SQL时返回）"},
      {"name":"data.warning","type":"String","required":false,"description":"查询警告信息"},
      {"name":"data.table","type":"Object","required":true,"description":"查询结果数据，key为列名，value为值数组"},
      {"name":"data.metas","type":"Array[Object]","required":true,"description":"列元数据信息"},
      {"name":"data.metas[].name","type":"String","required":true,"description":"字段名称"},
      {"name":"data.metas[].dataTypeName","type":"String","required":true,"description":"字段类型名称（如DATETIME/BIGINT/DECIMAL）"}
    ]
  },
  "dimensions_list": {
    "service":"anymetrics",
    "path":"/anymetrics/api/v1/dimensions/list",
    "method":"GET",
    "description":"获取指定条件下的维度列表",
    "requestParams":[
      {"name":"tenant-id","type":"String","required":true,"description":"租户ID","paramLocation":"HEADER"},
      {"name":"auth-type","type":"String","required":true,"description":"认证方式","paramLocation":"HEADER","enumValues":"UID,TOKEN,ACCOUNT,APIKEY"},
      {"name":"auth-value","type":"String","required":true,"description":"与auth-type对应的认证值","paramLocation":"HEADER"},
      {"name":"query-user-account","type":"String","required":false,"description":"鉴权用户名，为空则使用auth-value对应用户","paramLocation":"HEADER"},
      {"name":"keyword","type":"String","required":false,"description":"搜索关键词","paramLocation":"QUERY"},
      {"name":"dimCategoryId","type":"String","required":false,"description":"维度所属类目ID","paramLocation":"QUERY"},
      {"name":"statusFilters","type":"Array","required":false,"description":"维度状态过滤","paramLocation":"QUERY"},
      {"name":"pageNumber","type":"Integer","required":false,"defaultValue":"1","description":"当前页码","paramLocation":"QUERY"},
      {"name":"pageSize","type":"Integer","required":false,"defaultValue":"20","description":"每页大小","paramLocation":"QUERY"}
    ],
    "responseParams":[
      {"name":"code","type":"String","required":true,"description":"接口响应码"},
      {"name":"success","type":"Boolean","required":true,"description":"请求是否成功"},
      {"name":"errorMsg","type":"String","required":true,"description":"报错信息"},
      {"name":"traceId","type":"String","required":true,"description":"追踪ID"},
      {"name":"data","type":"Object","required":true,"description":"响应结果（含total/pageNumber/pageSize/data列表）"},
      {"name":"data.data[].dimName","type":"String","required":true,"description":"维度英文名"},
      {"name":"data.data[].dimDisplayName","type":"String","required":true,"description":"维度展示名"},
      {"name":"data.data[].originDataType","type":"String","required":true,"description":"维度数据类型"},
      {"name":"data.data[].dimDescription","type":"String","required":false,"description":"维度描述"}
    ]
  },
  "dimension_detail": {
    "service":"anymetrics",
    "path":"/anymetrics/api/v1/dimensions/detail",
    "method":"GET",
    "description":"查询维度详情",
    "requestParams":[
      {"name":"tenant-id","type":"String","required":true,"description":"租户ID","paramLocation":"HEADER"},
      {"name":"auth-type","type":"String","required":true,"description":"认证方式","paramLocation":"HEADER","enumValues":"UID,TOKEN,ACCOUNT,APIKEY"},
      {"name":"auth-value","type":"String","required":true,"description":"与auth-type对应的认证值","paramLocation":"HEADER"},
      {"name":"query-user-account","type":"String","required":false,"description":"鉴权用户名，为空则使用auth-value对应用户","paramLocation":"HEADER"},
      {"name":"dimName","type":"String","required":true,"description":"维度名称","paramLocation":"QUERY"}
    ],
    "responseParams":[
      {"name":"code","type":"String","required":true,"description":"接口响应码"},
      {"name":"success","type":"Boolean","required":true,"description":"请求是否成功"},
      {"name":"errorMsg","type":"String","required":true,"description":"报错信息"},
      {"name":"traceId","type":"String","required":true,"description":"追踪ID"},
      {"name":"data","type":"Object","required":true,"description":"维度详情对象"}
    ]
  },
  "dimension_values": {
    "service":"anymetrics",
    "path":"/anymetrics/api/v1/dimension/values",
    "method":"POST",
    "description":"预览指定维度的取值情况",
    "requestParams":[
      {"name":"tenant-id","type":"String","required":true,"description":"租户ID","paramLocation":"HEADER"},
      {"name":"auth-type","type":"String","required":true,"description":"认证方式","paramLocation":"HEADER","enumValues":"UID,TOKEN,ACCOUNT,APIKEY"},
      {"name":"auth-value","type":"String","required":true,"description":"与auth-type对应的认证值","paramLocation":"HEADER"},
      {"name":"query-user-account","type":"String","required":false,"description":"鉴权用户名，为空则使用auth-value对应用户","paramLocation":"HEADER"},
      {"name":"dimName","type":"String","required":true,"description":"维度名称","paramLocation":"BODY"},
      {"name":"dimValueKeyword","type":"String","required":false,"description":"维度值关键词（模糊匹配，仅对字符串类型维度有效）","paramLocation":"BODY"},
      {"name":"pageNumber","type":"Integer","required":false,"defaultValue":"1","description":"页码，从1开始","paramLocation":"BODY"},
      {"name":"pageSize","type":"Integer","required":false,"defaultValue":"200","description":"每页记录条数","paramLocation":"BODY"}
    ],
    "responseParams":[
      {"name":"code","type":"String","required":true,"description":"接口响应码"},
      {"name":"success","type":"Boolean","required":true,"description":"请求是否成功"},
      {"name":"data.queryId","type":"String","required":true,"description":"查询ID"},
      {"name":"data.metas","type":"Array[Object]","required":true,"description":"列元数据"},
      {"name":"data.metas[].name","type":"String","required":true,"description":"字段名称"},
      {"name":"data.metas[].dataTypeName","type":"String","required":true,"description":"字段类型名称"},
      {"name":"data.table","type":"Object","required":true,"description":"查询结果数据，key为列名"}
    ]
  },
  "metric_available_dimensions": {
    "service":"anymetrics",
    "path":"/anymetrics/api/v1/metrics/dimension",
    "method":"GET",
    "description":"查询指标可用维度（支持多指标交集）",
    "requestParams":[
      {"name":"tenant-id","type":"String","required":true,"description":"租户ID","paramLocation":"HEADER"},
      {"name":"auth-type","type":"String","required":true,"description":"认证方式","paramLocation":"HEADER","enumValues":"UID,TOKEN,ACCOUNT,APIKEY"},
      {"name":"auth-value","type":"String","required":true,"description":"与auth-type对应的认证值","paramLocation":"HEADER"},
      {"name":"query-user-account","type":"String","required":false,"description":"鉴权用户名，为空则使用auth-value对应用户","paramLocation":"HEADER"},
      {"name":"metricNames","type":"Array[String]","required":true,"description":"指标名称集合，单个指标返回所有可用维度，多个指标返回交集","paramLocation":"QUERY"}
    ],
    "responseParams":[
      {"name":"code","type":"String","required":true,"description":"接口响应码"},
      {"name":"success","type":"Boolean","required":true,"description":"请求是否成功"},
      {"name":"errorMsg","type":"String","required":true,"description":"报错信息"},
      {"name":"traceId","type":"String","required":true,"description":"追踪ID"},
      {"name":"data","type":"Array[Object]","required":true,"description":"可用维度列表"},
      {"name":"data[].dimName","type":"String","required":true,"description":"维度英文名"},
      {"name":"data[].dimDisplayName","type":"String","required":true,"description":"维度展示名"},
      {"name":"data[].originDataType","type":"String","required":true,"description":"维度数据类型"},
      {"name":"data[].datasetName","type":"String","required":false,"description":"维度绑定的数据集名称"},
      {"name":"data[].config.type","type":"String","required":true,"description":"维度类型：COLUMN_BIND/CUSTOM"},
      {"name":"data[].config.value","type":"String","required":true,"description":"列名或自定义表达式"}
    ]
  },
  "metric_detail": {
    "service":"anymetrics",
    "path":"/anymetrics/api/v1/metrics/detail",
    "method":"GET",
    "description":"查询单个指标详情",
    "requestParams":[
      {"name":"tenant-id","type":"String","required":true,"description":"租户ID","paramLocation":"HEADER"},
      {"name":"auth-type","type":"String","required":true,"description":"认证方式","paramLocation":"HEADER","enumValues":"UID,TOKEN,ACCOUNT,APIKEY"},
      {"name":"auth-value","type":"String","required":true,"description":"与auth-type对应的认证值","paramLocation":"HEADER"},
      {"name":"query-user-account","type":"String","required":false,"description":"鉴权用户名，为空则使用auth-value对应用户","paramLocation":"HEADER"},
      {"name":"metricName","type":"String","required":true,"description":"指标名称","paramLocation":"QUERY"}
    ],
    "responseParams":[
      {"name":"code","type":"String","required":true,"description":"接口响应码"},
      {"name":"success","type":"Boolean","required":true,"description":"请求是否成功"},
      {"name":"errorMsg","type":"String","required":true,"description":"报错信息"},
      {"name":"traceId","type":"String","required":true,"description":"追踪ID"},
      {"name":"data","type":"Object","required":true,"description":"指标详情对象"}
    ]
  },
  "metric_batch_detail": {
    "service":"anymetrics",
    "path":"/anymetrics/api/v1/metrics/batch-detail",
    "method":"POST",
    "description":"批量查询指标详情",
    "requestParams":[
      {"name":"tenant-id","type":"String","required":true,"description":"租户ID","paramLocation":"HEADER"},
      {"name":"auth-type","type":"String","required":true,"description":"认证方式","paramLocation":"HEADER","enumValues":"UID,TOKEN,ACCOUNT,APIKEY"},
      {"name":"auth-value","type":"String","required":true,"description":"与auth-type对应的认证值","paramLocation":"HEADER"},
      {"name":"query-user-account","type":"String","required":false,"description":"鉴权用户名，为空则使用auth-value对应用户","paramLocation":"HEADER"},
      {"name":"metricNames","type":"Array[String]","required":true,"description":"指标名称集合","paramLocation":"BODY"}
    ],
    "responseParams":[
      {"name":"code","type":"String","required":true,"description":"接口响应码"},
      {"name":"success","type":"Boolean","required":true,"description":"请求是否成功"},
      {"name":"errorMsg","type":"String","required":true,"description":"报错信息"},
      {"name":"traceId","type":"String","required":true,"description":"追踪ID"},
      {"name":"data","type":"Array[Object]","required":true,"description":"指标详情列表"}
    ]
  },
  "metric_tree": {
    "service":"anymetrics",
    "path":"/anymetrics/api/v1/metrics/tree",
    "method":"GET",
    "description":"获取树状结构的指标列表",
    "requestParams":[
      {"name":"tenant-id","type":"String","required":true,"description":"租户ID","paramLocation":"HEADER"},
      {"name":"auth-type","type":"String","required":true,"description":"认证方式","paramLocation":"HEADER","enumValues":"UID,TOKEN,ACCOUNT,APIKEY"},
      {"name":"auth-value","type":"String","required":true,"description":"与auth-type对应的认证值","paramLocation":"HEADER"},
      {"name":"query-user-account","type":"String","required":false,"description":"鉴权用户名，为空则使用auth-value对应用户","paramLocation":"HEADER"},
      {"name":"keyword","type":"String","required":false,"description":"搜索关键词","paramLocation":"QUERY"},
      {"name":"metricCategoryId","type":"String","required":false,"description":"指标所属类目ID","paramLocation":"QUERY"}
    ],
    "responseParams":[
      {"name":"code","type":"String","required":true,"description":"接口响应码"},
      {"name":"success","type":"Boolean","required":true,"description":"请求是否成功"},
      {"name":"errorMsg","type":"String","required":true,"description":"报错信息"},
      {"name":"traceId","type":"String","required":true,"description":"追踪ID"},
      {"name":"data","type":"Array[Object]","required":true,"description":"树状结构指标列表"}
    ]
  },
  "attribution_tree": {
    "service":"semantic",
    "path":"/semantic/api/v1/attribution/tree",
    "method":"POST",
    "description":"针对指标以树解耦进行归因分析报告查询",
    "requestParams":[
      {"name":"tenant-id","type":"String","required":true,"description":"租户ID","paramLocation":"HEADER"},
      {"name":"auth-type","type":"String","required":true,"description":"认证方式","paramLocation":"HEADER","enumValues":"UID,TOKEN,ACCOUNT,APIKEY"},
      {"name":"auth-value","type":"String","required":true,"description":"与auth-type对应的认证值","paramLocation":"HEADER"},
      {"name":"query-user-account","type":"String","required":false,"description":"鉴权用户名，为空则使用auth-value对应用户","paramLocation":"HEADER"},
      {"name":"metricName","type":"String","required":true,"description":"指标名称","paramLocation":"BODY"},
      {"name":"dimensions","type":"Array[String]","required":false,"description":"分析维度列表","paramLocation":"BODY"},
      {"name":"timeConstraint","type":"String","required":false,"description":"时间范围约束","paramLocation":"BODY"},
      {"name":"filters","type":"Array[String]","required":false,"description":"全局筛选条件","paramLocation":"BODY"}
    ],
    "responseParams":[
      {"name":"success","type":"Boolean","required":true,"description":"请求是否成功"},
      {"name":"code","type":"String","required":true,"description":"接口响应码"},
      {"name":"errorMsg","type":"String","required":true,"description":"报错信息"},
      {"name":"traceId","type":"String","required":true,"description":"追踪ID"},
      {"name":"data","type":"Object","required":true,"description":"归因分析树结果"}
    ]
  },
  "attribution_multi_dim": {
    "service":"semantic",
    "path":"/semantic/api/v1/attribution/multi-dim",
    "method":"POST",
    "description":"针对指标及维度进行多维归因结果查询",
    "requestParams":[
      {"name":"tenant-id","type":"String","required":true,"description":"租户ID","paramLocation":"HEADER"},
      {"name":"auth-type","type":"String","required":true,"description":"认证方式","paramLocation":"HEADER","enumValues":"UID,TOKEN,ACCOUNT,APIKEY"},
      {"name":"auth-value","type":"String","required":true,"description":"与auth-type对应的认证值","paramLocation":"HEADER"},
      {"name":"query-user-account","type":"String","required":false,"description":"鉴权用户名，为空则使用auth-value对应用户","paramLocation":"HEADER"},
      {"name":"metricName","type":"String","required":true,"description":"指标名称","paramLocation":"BODY"},
      {"name":"dimensions","type":"Array[String]","required":true,"description":"分析维度列表","paramLocation":"BODY"},
      {"name":"timeConstraint","type":"String","required":false,"description":"时间范围约束","paramLocation":"BODY"},
      {"name":"filters","type":"Array[String]","required":false,"description":"全局筛选条件","paramLocation":"BODY"}
    ],
    "responseParams":[
      {"name":"success","type":"Boolean","required":true,"description":"请求是否成功"},
      {"name":"code","type":"String","required":true,"description":"接口响应码"},
      {"name":"errorMsg","type":"String","required":true,"description":"报错信息"},
      {"name":"traceId","type":"String","required":true,"description":"追踪ID"},
      {"name":"data","type":"Object","required":true,"description":"多维归因分析结果"}
    ]
  },
  "attribution_validate": {
    "service":"semantic",
    "path":"/semantic/api/v1/attribution/validate",
    "method":"POST",
    "description":"校验指标是否能够进行归因分析",
    "requestParams":[
      {"name":"tenant-id","type":"String","required":true,"description":"租户ID","paramLocation":"HEADER"},
      {"name":"auth-type","type":"String","required":true,"description":"认证方式","paramLocation":"HEADER","enumValues":"UID,TOKEN,ACCOUNT,APIKEY"},
      {"name":"auth-value","type":"String","required":true,"description":"与auth-type对应的认证值","paramLocation":"HEADER"},
      {"name":"query-user-account","type":"String","required":false,"description":"鉴权用户名，为空则使用auth-value对应用户","paramLocation":"HEADER"},
      {"name":"metricName","type":"String","required":true,"description":"指标名称","paramLocation":"BODY"},
      {"name":"dimensions","type":"Array[String]","required":false,"description":"分析维度列表","paramLocation":"BODY"}
    ],
    "responseParams":[
      {"name":"success","type":"Boolean","required":true,"description":"请求是否成功"},
      {"name":"code","type":"String","required":true,"description":"接口响应码"},
      {"name":"errorMsg","type":"String","required":true,"description":"报错信息"},
      {"name":"traceId","type":"String","required":true,"description":"追踪ID"},
      {"name":"data","type":"Object","required":true,"description":"校验结果"}
    ]
  },
  "attribution_drilldown": {
    "service":"semantic",
    "path":"/semantic/api/v1/attribution/drilldown",
    "method":"POST",
    "description":"针对指标及维度进行多维归因下钻查询",
    "requestParams":[
      {"name":"tenant-id","type":"String","required":true,"description":"租户ID","paramLocation":"HEADER"},
      {"name":"auth-type","type":"String","required":true,"description":"认证方式","paramLocation":"HEADER","enumValues":"UID,TOKEN,ACCOUNT,APIKEY"},
      {"name":"auth-value","type":"String","required":true,"description":"与auth-type对应的认证值","paramLocation":"HEADER"},
      {"name":"query-user-account","type":"String","required":false,"description":"鉴权用户名，为空则使用auth-value对应用户","paramLocation":"HEADER"},
      {"name":"metricName","type":"String","required":true,"description":"指标名称","paramLocation":"BODY"},
      {"name":"dimensions","type":"Array[String]","required":true,"description":"下钻维度列表","paramLocation":"BODY"},
      {"name":"timeConstraint","type":"String","required":false,"description":"时间范围约束","paramLocation":"BODY"},
      {"name":"filters","type":"Array[String]","required":false,"description":"全局筛选条件","paramLocation":"BODY"}
    ],
    "responseParams":[
      {"name":"success","type":"Boolean","required":true,"description":"请求是否成功"},
      {"name":"code","type":"String","required":true,"description":"接口响应码"},
      {"name":"errorMsg","type":"String","required":true,"description":"报错信息"},
      {"name":"traceId","type":"String","required":true,"description":"追踪ID"},
      {"name":"data","type":"Object","required":true,"description":"下钻查询结果"}
    ]
  }
}',
    'Aloudata API 端点配置（JSON格式，含完整的 requestParams 和 responseParams 参数规范定义）',
    NOW(),
    NOW()
) ON DUPLICATE KEY UPDATE `setting_value` = VALUES(`setting_value`), `update_time` = NOW();
