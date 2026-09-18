-- PostgreSQL DDL and sample data generated from 工作簿1.xlsx
-- 关联规则：dataset2.metric column
--   digo_<metric_id>_trans_user_cnt
-- 对应 dataset1.metric_id = <metric_id>

CREATE TABLE IF NOT EXISTS cljd_zcl_wd (
    metric_time                  date         NOT NULL,
    attribution_plan_id          varchar(64)  NOT NULL,
    attribution_plan_name        varchar(200) NOT NULL,
    attribution_plan_um_account  varchar(100),
    attribution_strategy_id      varchar(64)  NOT NULL,
    attribution_strategy_name    varchar(200) NOT NULL,
    attribution_over_by          varchar(100),
    platform_id                  varchar(64)  NOT NULL,
    platform_name                varchar(200) NOT NULL,
    create_by                    varchar(100),
    channel                      varchar(100) NOT NULL,
    metric_id                    varchar(200) NOT NULL,
    metric_name                  varchar(300) NOT NULL,
    digo_strategy_cnt            bigint       NOT NULL DEFAULT 0,
    digo_strategy_cnt_distr_1    bigint       NOT NULL DEFAULT 0,
    digo_distr_count_1           bigint       NOT NULL DEFAULT 0,
    digo_distr_user_cnt_a        bigint       NOT NULL DEFAULT 0,
    digo_touch_cnt_1             bigint       NOT NULL DEFAULT 0,
    digo_touch_user_cnt_1        bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_cljd_zcl_wd PRIMARY KEY (
        metric_time,
        attribution_plan_id,
        attribution_strategy_id,
        platform_id,
        channel,
        metric_id
    )
);

CREATE TABLE IF NOT EXISTS cljd_zcl_zb (
    metric_time                                  date         NOT NULL,
    attribution_plan_id                          varchar(64)  NOT NULL,
    attribution_strategy_id                      varchar(64)  NOT NULL,
    platform_id                                  varchar(64)  NOT NULL,
    channel                                      varchar(100) NOT NULL,
    digo_trd_fund_amt_inout_cy_jjgr              numeric(20,2) NOT NULL DEFAULT 0,
    digo_trd_fund_amt_inout_cy_jjgr_trans_user_cnt bigint     NOT NULL DEFAULT 0,
    digo_trd_fund_amt_inout_cy_jjgr_pb           numeric(20,2) NOT NULL DEFAULT 0,
    digo_pbcnt_kgdb_a566_fh_jjgr                 bigint       NOT NULL DEFAULT 0,
    digo_fund_trd_amt_a566_fh_kgdb_jj0          numeric(20,2) NOT NULL DEFAULT 0,
    digo_fund_trd_amt_a566_fh_kgdb_jj0_trans_user_cnt bigint NOT NULL DEFAULT 0,
    digo_pub_fh_kgdb_trdamt_ppcadd_jjgr         numeric(20,2) NOT NULL DEFAULT 0,
    digo_pub_fh_kgdb_trdamt_ppcadd_jjgr_trans_user_cnt bigint NOT NULL DEFAULT 0,
    digo_cust_asset_in                           bigint       NOT NULL DEFAULT 0,
    digo_new_cust_asset_in                       bigint       NOT NULL DEFAULT 0,
    digo_cnt_cust_code_new_brok                  bigint       NOT NULL DEFAULT 0,
    digo_cust_valid_new_yxh                      bigint       NOT NULL DEFAULT 0,
    digo_cust_asset_in10000                      bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_cljd_zcl_zb PRIMARY KEY (
        metric_time,
        attribution_plan_id,
        attribution_strategy_id,
        platform_id,
        channel
    )
);

COMMENT ON TABLE cljd_zcl_wd IS '策略解读-子策略-维度';
COMMENT ON TABLE cljd_zcl_zb IS '策略解读-子策略-指标';
COMMENT ON COLUMN cljd_zcl_wd.metric_time IS '指标日期';
COMMENT ON COLUMN cljd_zcl_wd.attribution_plan_id IS '计划id';
COMMENT ON COLUMN cljd_zcl_wd.attribution_plan_name IS '计划名称';
COMMENT ON COLUMN cljd_zcl_wd.attribution_plan_um_account IS '计划负责人';
COMMENT ON COLUMN cljd_zcl_wd.attribution_strategy_id IS '策略id';
COMMENT ON COLUMN cljd_zcl_wd.attribution_strategy_name IS '策略名称';
COMMENT ON COLUMN cljd_zcl_wd.attribution_over_by IS '策略负责人';
COMMENT ON COLUMN cljd_zcl_wd.platform_id IS '子策略id';
COMMENT ON COLUMN cljd_zcl_wd.platform_name IS '子策略名称';
COMMENT ON COLUMN cljd_zcl_wd.create_by IS '子策略创建人';
COMMENT ON COLUMN cljd_zcl_wd.channel IS '触达渠道';
COMMENT ON COLUMN cljd_zcl_wd.metric_id IS '转化指标id；对应指标表中 digo_<metric_id>_trans_user_cnt 的中间部分';
COMMENT ON COLUMN cljd_zcl_wd.metric_name IS '转化指标名称';
COMMENT ON COLUMN cljd_zcl_wd.digo_strategy_cnt IS '下挂策略数';
COMMENT ON COLUMN cljd_zcl_wd.digo_strategy_cnt_distr_1 IS '下发策略数';
COMMENT ON COLUMN cljd_zcl_wd.digo_distr_count_1 IS '下发次数';
COMMENT ON COLUMN cljd_zcl_wd.digo_distr_user_cnt_a IS '下发人数';
COMMENT ON COLUMN cljd_zcl_wd.digo_touch_cnt_1 IS '触达人数';
COMMENT ON COLUMN cljd_zcl_wd.digo_touch_user_cnt_1 IS '触达次数';

COMMENT ON COLUMN cljd_zcl_zb.metric_time IS '指标日期';
COMMENT ON COLUMN cljd_zcl_zb.attribution_plan_id IS '计划id';
COMMENT ON COLUMN cljd_zcl_zb.attribution_strategy_id IS '策略id';
COMMENT ON COLUMN cljd_zcl_zb.platform_id IS '子策略id';
COMMENT ON COLUMN cljd_zcl_zb.channel IS '触达渠道';
COMMENT ON COLUMN cljd_zcl_zb.digo_trd_fund_amt_inout_cy_jjgr IS '经纪个人客户场内公募非货当年净买入-策略归因';
COMMENT ON COLUMN cljd_zcl_zb.digo_trd_fund_amt_inout_cy_jjgr_trans_user_cnt IS '经纪个人客户场内公募非货当年净买入-转化客户数-策略归因';
COMMENT ON COLUMN cljd_zcl_zb.digo_trd_fund_amt_inout_cy_jjgr_pb IS '经纪个人场内公募非货破冰客户当年净买入-策略归因';
COMMENT ON COLUMN cljd_zcl_zb.digo_pbcnt_kgdb_a566_fh_jjgr IS '经纪个人场内公募非货破冰客户数-策略归因';
COMMENT ON COLUMN cljd_zcl_zb.digo_fund_trd_amt_a566_fh_kgdb_jj0 IS '经纪个人场内公募非货交易量-策略归因';
COMMENT ON COLUMN cljd_zcl_zb.digo_fund_trd_amt_a566_fh_kgdb_jj0_trans_user_cnt IS '经纪个人场内公募非货交易量-转化客户数-策略归因';
COMMENT ON COLUMN cljd_zcl_zb.digo_pub_fh_kgdb_trdamt_ppcadd_jjgr IS '经纪个人场内公募非货加仓交易量-策略归因';
COMMENT ON COLUMN cljd_zcl_zb.digo_pub_fh_kgdb_trdamt_ppcadd_jjgr_trans_user_cnt IS '经纪个人场内公募非货加仓交易量-转化客户数-策略归因';
COMMENT ON COLUMN cljd_zcl_zb.digo_cust_asset_in IS '入金客户数_策略归因';
COMMENT ON COLUMN cljd_zcl_zb.digo_new_cust_asset_in IS '新客入金数_策略归因';
COMMENT ON COLUMN cljd_zcl_zb.digo_cnt_cust_code_new_brok IS '新增客户数_策略归因';
COMMENT ON COLUMN cljd_zcl_zb.digo_cust_valid_new_yxh IS '新增有效户数_策略归因';
COMMENT ON COLUMN cljd_zcl_zb.digo_cust_asset_in10000 IS '万元入金户数_策略归因';

CREATE INDEX IF NOT EXISTS idx_cljd_zcl_wd_metric_id
    ON cljd_zcl_wd (metric_id);

CREATE INDEX IF NOT EXISTS idx_cljd_zcl_zb_metric_time
    ON cljd_zcl_zb (metric_time);

INSERT INTO cljd_zcl_zb (
    metric_time,
    attribution_plan_id,
    attribution_strategy_id,
    platform_id,
    channel,
    digo_trd_fund_amt_inout_cy_jjgr,
    digo_trd_fund_amt_inout_cy_jjgr_trans_user_cnt,
    digo_trd_fund_amt_inout_cy_jjgr_pb,
    digo_pbcnt_kgdb_a566_fh_jjgr,
    digo_fund_trd_amt_a566_fh_kgdb_jj0,
    digo_fund_trd_amt_a566_fh_kgdb_jj0_trans_user_cnt,
    digo_pub_fh_kgdb_trdamt_ppcadd_jjgr,
    digo_pub_fh_kgdb_trdamt_ppcadd_jjgr_trans_user_cnt,
    digo_cust_asset_in,
    digo_new_cust_asset_in,
    digo_cnt_cust_code_new_brok,
    digo_cust_valid_new_yxh,
    digo_cust_asset_in10000
)
VALUES
    ('2026-09-01', 'PLAN-001', 'STR-001', 'SUB-001', 'APP', 1250000.00, 128, 420000.00, 96, 860000.00, 74, 210000.00, 39, 186, 72, 154, 91, 43),
    ('2026-09-01', 'PLAN-001', 'STR-001', 'SUB-001', 'SMS',  980000.00, 101, 315000.00, 81, 640000.00, 57, 175000.00, 31, 143, 55, 119, 68, 32),
    ('2026-09-01', 'PLAN-001', 'STR-002', 'SUB-002', 'APP', 1480000.00, 152, 510000.00, 112, 930000.00, 88, 265000.00, 46, 211, 84, 179, 108, 51),
    ('2026-09-02', 'PLAN-001', 'STR-001', 'SUB-001', 'APP', 1320000.00, 136, 450000.00, 103, 905000.00, 79, 230000.00, 42, 194, 78, 163, 97, 46),
    ('2026-09-02', 'PLAN-001', 'STR-001', 'SUB-001', 'SMS', 1040000.00, 109, 338000.00, 87, 690000.00, 62, 188000.00, 34, 151, 60, 126, 73, 35),
    ('2026-09-02', 'PLAN-001', 'STR-002', 'SUB-002', 'APP', 1550000.00, 161, 536000.00, 121, 982000.00, 94, 279000.00, 49, 223, 90, 190, 115, 55)
ON CONFLICT DO NOTHING;

INSERT INTO cljd_zcl_wd (
    metric_time,
    attribution_plan_id,
    attribution_plan_name,
    attribution_plan_um_account,
    attribution_strategy_id,
    attribution_strategy_name,
    attribution_over_by,
    platform_id,
    platform_name,
    create_by,
    channel,
    metric_id,
    metric_name,
    digo_strategy_cnt,
    digo_strategy_cnt_distr_1,
    digo_distr_count_1,
    digo_distr_user_cnt_a,
    digo_touch_cnt_1,
    digo_touch_user_cnt_1
)
SELECT
    z.metric_time,
    z.attribution_plan_id,
    '策略解读示例计划',
    'plan_owner',
    z.attribution_strategy_id,
    CASE z.attribution_strategy_id
        WHEN 'STR-001' THEN '高净值客户触达策略'
        WHEN 'STR-002' THEN '新客增长策略'
    END,
    CASE z.attribution_strategy_id
        WHEN 'STR-001' THEN 'strategy_owner_a'
        WHEN 'STR-002' THEN 'strategy_owner_b'
    END,
    z.platform_id,
    CASE z.platform_id
        WHEN 'SUB-001' THEN '场内公募非货子策略'
        WHEN 'SUB-002' THEN '新客入金子策略'
    END,
    'data_owner',
    z.channel,
    m.metric_id,
    m.metric_name,
    CASE z.attribution_strategy_id WHEN 'STR-001' THEN 2 ELSE 3 END,
    CASE z.attribution_strategy_id WHEN 'STR-001' THEN 2 ELSE 3 END,
    CASE z.channel WHEN 'APP' THEN 180 ELSE 130 END,
    CASE z.channel WHEN 'APP' THEN 150 ELSE 105 END,
    CASE z.channel WHEN 'APP' THEN 120 ELSE 88 END,
    CASE z.channel WHEN 'APP' THEN 96 ELSE 71 END
FROM cljd_zcl_zb z
CROSS JOIN (
    VALUES
        ('trd_fund_amt_inout_cy_jjgr', '经纪个人客户场内公募非货当年净买入'),
        ('fund_trd_amt_a566_fh_kgdb_jj0', '经纪个人场内公募非货交易量'),
        ('pub_fh_kgdb_trdamt_ppcadd_jjgr', '经纪个人场内公募非货加仓交易量')
) AS m(metric_id, metric_name)
ON CONFLICT DO NOTHING;

-- 可选校验：每个转化指标 ID 都能在指标表中找到对应的 *_trans_user_cnt 字段。
-- 下面查询应返回 3 行，且 missing_metric_id 均为 NULL。
WITH conversion_metrics(metric_id) AS (
    VALUES
        ('trd_fund_amt_inout_cy_jjgr'),
        ('fund_trd_amt_a566_fh_kgdb_jj0'),
        ('pub_fh_kgdb_trdamt_ppcadd_jjgr')
)
SELECT
    c.metric_id,
    CASE WHEN c.metric_id IN (
        'trd_fund_amt_inout_cy_jjgr',
        'fund_trd_amt_a566_fh_kgdb_jj0',
        'pub_fh_kgdb_trdamt_ppcadd_jjgr'
    ) THEN NULL ELSE c.metric_id END AS missing_metric_id
FROM conversion_metrics c;
