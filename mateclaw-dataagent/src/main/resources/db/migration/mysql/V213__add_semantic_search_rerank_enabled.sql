-- ============================================================
-- 语义检索 Rerank 开关配置
-- ============================================================
-- 控制 aloudata_search_semantic 语义检索链路是否开启 rerank 精排分支。
-- 默认关闭（false）；开启后需先在模型管理中配置并设为默认的 rerank 类型模型，
-- 检索结果会经 rerank 模型按与用户查询的相关度二次精排。

INSERT INTO `mate_system_setting` (`id`, `setting_key`, `setting_value`, `description`, `create_time`, `update_time`)
VALUES (
    2003,
    'dataagent.search.rerank.enabled',
    'false',
    '语义检索 rerank 精排开关：true 开启（需配置默认 rerank 模型），false 关闭',
    NOW(),
    NOW()
) ON DUPLICATE KEY UPDATE `setting_value` = VALUES(`setting_value`), `update_time` = NOW();
