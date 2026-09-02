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

CREATE TABLE IF NOT EXISTS dataagent_query_state (
    id BIGINT NOT NULL,
    conversation_id VARCHAR(128) NOT NULL,
    datasource_id BIGINT NOT NULL,
    metrics VARCHAR(4000) DEFAULT NULL,
    dimensions VARCHAR(2000) DEFAULT NULL,
    time_constraint VARCHAR(2000) DEFAULT NULL,
    filters VARCHAR(4000) DEFAULT NULL,
    orders VARCHAR(2000) DEFAULT NULL,
    metric_display_map TEXT,
    request_json TEXT,
    query_count INTEGER NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_dataagent_query_state_conv_ds ON dataagent_query_state (conversation_id, datasource_id);

CREATE INDEX IF NOT EXISTS idx_dataagent_query_state_conversation ON dataagent_query_state (conversation_id);

COMMENT ON COLUMN dataagent_query_state.id IS '主键（雪花 ID）';
COMMENT ON COLUMN dataagent_query_state.conversation_id IS '会话 ID';
COMMENT ON COLUMN dataagent_query_state.datasource_id IS '数据源 ID';
COMMENT ON COLUMN dataagent_query_state.metrics IS '指标英文名列表（JSON 数组）';
COMMENT ON COLUMN dataagent_query_state.dimensions IS '维度英文名列表（JSON 数组）';
COMMENT ON COLUMN dataagent_query_state.time_constraint IS '时间约束表达式';
COMMENT ON COLUMN dataagent_query_state.filters IS '全局筛选条件（JSON 数组）';
COMMENT ON COLUMN dataagent_query_state.orders IS '排序定义（JSON 数组）';
COMMENT ON COLUMN dataagent_query_state.metric_display_map IS '指标英文名→中文展示名/口径映射（JSON 对象）';
COMMENT ON COLUMN dataagent_query_state.request_json IS '成功请求的完整参数 JSON（审计/追踪）';
COMMENT ON COLUMN dataagent_query_state.query_count IS '该基座被复用的次数';
COMMENT ON COLUMN dataagent_query_state.create_time IS '创建时间';
COMMENT ON COLUMN dataagent_query_state.update_time IS '更新时间';

DROP TRIGGER IF EXISTS trg_dataagent_query_state_upd_ts ON dataagent_query_state;
CREATE TRIGGER trg_dataagent_query_state_upd_ts BEFORE UPDATE ON dataagent_query_state
    FOR EACH ROW EXECUTE FUNCTION set_update_time();
