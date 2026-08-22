-- ============================================================
-- V215: 会话级「成功查询基座」结构化状态表（P0-2）
-- ============================================================
-- 背景：多轮追问的"追问基座"依赖会话历史 keep-alive，历史被压缩后基座
-- 丢失，导致下一轮全量重新检索、选到不同的 metricName/dimName 与上一轮
-- 自相矛盾。本表把每轮成功 aloudata_metrics_query 的**结构化参数**持久化
-- 到会话维度（与上下文压缩无关，独立存储），下一轮直接读取注入作为基座，
-- 使追问与历史保真度解耦，保证多轮连贯。
--
-- 语义：每条 (conversation_id, datasource_id) 为最新一次成功查询的基座
-- （upsert 覆盖）。删除会话时由 DataAgentConversationService 联动清理。
-- ============================================================

CREATE TABLE `dataagent_query_state` (
    `id`                BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
    `conversation_id`   VARCHAR(128) NOT NULL COMMENT '会话 ID',
    `datasource_id`     BIGINT       NOT NULL COMMENT '数据源 ID',
    `metrics`           VARCHAR(4000) DEFAULT NULL COMMENT '指标英文名列表（JSON 数组）',
    `dimensions`        VARCHAR(2000) DEFAULT NULL COMMENT '维度英文名列表（JSON 数组）',
    `time_constraint`   VARCHAR(2000) DEFAULT NULL COMMENT '时间约束表达式',
    `filters`           VARCHAR(4000) DEFAULT NULL COMMENT '全局筛选条件（JSON 数组）',
    `orders`            VARCHAR(2000) DEFAULT NULL COMMENT '排序定义（JSON 数组）',
    `metric_display_map` TEXT COMMENT '指标英文名→中文展示名/口径映射（JSON 对象）',
    `request_json`      TEXT COMMENT '成功请求的完整参数 JSON（审计/追踪）',
    `query_count`       INT          NOT NULL DEFAULT 1 COMMENT '该基座被复用的次数',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dataagent_query_state_conv_ds` (`conversation_id`, `datasource_id`),
    KEY `idx_dataagent_query_state_conversation` (`conversation_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='会话级成功指标查询基座（多轮追问结构化状态）';
