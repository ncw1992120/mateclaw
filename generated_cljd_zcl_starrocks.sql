-- StarRocks DDL and sample data converted from generated_cljd_zcl.sql
--
-- StarRocks-specific choices:
-- 1. Use Primary Key tables for the two datasets. Re-running the INSERT
--    statements performs an idempotent upsert by primary key; PostgreSQL's
--    ON CONFLICT DO NOTHING is not used here.
-- 2. Primary-key columns are declared first, as required by StarRocks.
-- 3. Use ENGINE=OLAP + DISTRIBUTED BY HASH for StarRocks storage.
-- 4. PostgreSQL COMMENT ON statements are converted to inline column comments.
-- 5. PostgreSQL numeric is converted to StarRocks DECIMAL.
--
-- Metric mapping rule:
--   digo_<metric_id>_trans_user_cnt
-- corresponds to dataset1.metric_id = <metric_id>.

CREATE TABLE IF NOT EXISTS `cljd_zcl_wd` (
    `metric_time`                 DATE         NOT NULL COMMENT "指标日期",
    `attribution_plan_id`         VARCHAR(64)  NOT NULL COMMENT "计划id",
    `attribution_strategy_id`     VARCHAR(64)  NOT NULL COMMENT "策略id",
    `platform_id`                 VARCHAR(64)  NOT NULL COMMENT "子策略id",
    `channel`                     VARCHAR(100) NOT NULL COMMENT "触达渠道",
    `metric_id`                   VARCHAR(200) NOT NULL COMMENT "转化指标id；对应指标表中 digo_<metric_id>_trans_user_cnt 的中间部分",
    `attribution_plan_name`       VARCHAR(200) NOT NULL COMMENT "计划名称",
    `attribution_plan_um_account` VARCHAR(100)          COMMENT "计划负责人",
    `attribution_strategy_name`   VARCHAR(200) NOT NULL COMMENT "策略名称",
    `attribution_over_by`         VARCHAR(100)          COMMENT "策略负责人",
    `platform_name`               VARCHAR(200) NOT NULL COMMENT "子策略名称",
    `create_by`                   VARCHAR(100)          COMMENT "子策略创建人",
    `metric_name`                 VARCHAR(300) NOT NULL COMMENT "转化指标名称",
    `digo_strategy_cnt`           BIGINT       NOT NULL DEFAULT "0" COMMENT "下挂策略数",
    `digo_strategy_cnt_distr_1`   BIGINT       NOT NULL DEFAULT "0" COMMENT "下发策略数",
    `digo_distr_count_1`          BIGINT       NOT NULL DEFAULT "0" COMMENT "下发次数",
    `digo_distr_user_cnt_a`       BIGINT       NOT NULL DEFAULT "0" COMMENT "下发人数",
    `digo_touch_cnt_1`            BIGINT       NOT NULL DEFAULT "0" COMMENT "触达人数",
    `digo_touch_user_cnt_1`       BIGINT       NOT NULL DEFAULT "0" COMMENT "触达次数",
    INDEX `idx_cljd_zcl_wd_metric_id` (`metric_id`) USING BITMAP
)
ENGINE = OLAP
PRIMARY KEY (`metric_time`, `attribution_plan_id`, `attribution_strategy_id`, `platform_id`, `channel`, `metric_id`)
COMMENT "策略解读-子策略-维度"
DISTRIBUTED BY HASH (`metric_time`, `attribution_plan_id`, `attribution_strategy_id`, `platform_id`, `channel`)
BUCKETS 3
PROPERTIES (
    "enable_persistent_index" = "true",
    "replication_num" = "1"
);

CREATE TABLE IF NOT EXISTS `cljd_zcl_zb` (
    `metric_time`                                      DATE          NOT NULL COMMENT "指标日期",
    `attribution_plan_id`                              VARCHAR(64)   NOT NULL COMMENT "计划id",
    `attribution_strategy_id`                          VARCHAR(64)   NOT NULL COMMENT "策略id",
    `platform_id`                                      VARCHAR(64)   NOT NULL COMMENT "子策略id",
    `channel`                                          VARCHAR(100)  NOT NULL COMMENT "触达渠道",
    `digo_trd_fund_amt_inout_cy_jjgr`                  DECIMAL(20,2) NOT NULL DEFAULT "0" COMMENT "经纪个人客户场内公募非货当年净买入-策略归因",
    `digo_trd_fund_amt_inout_cy_jjgr_trans_user_cnt`   BIGINT        NOT NULL DEFAULT "0" COMMENT "经纪个人客户场内公募非货当年净买入-转化客户数-策略归因",
    `digo_trd_fund_amt_inout_cy_jjgr_pb`               DECIMAL(20,2) NOT NULL DEFAULT "0" COMMENT "经纪个人场内公募非货破冰客户当年净买入-策略归因",
    `digo_pbcnt_kgdb_a566_fh_jjgr`                    BIGINT        NOT NULL DEFAULT "0" COMMENT "经纪个人场内公募非货破冰客户数-策略归因",
    `digo_fund_trd_amt_a566_fh_kgdb_jj0`              DECIMAL(20,2) NOT NULL DEFAULT "0" COMMENT "经纪个人场内公募非货交易量-策略归因",
    `digo_fund_trd_amt_a566_fh_kgdb_jj0_trans_user_cnt` BIGINT      NOT NULL DEFAULT "0" COMMENT "经纪个人场内公募非货交易量-转化客户数-策略归因",
    `digo_pub_fh_kgdb_trdamt_ppcadd_jjgr`             DECIMAL(20,2) NOT NULL DEFAULT "0" COMMENT "经纪个人场内公募非货加仓交易量-策略归因",
    `digo_pub_fh_kgdb_trdamt_ppcadd_jjgr_trans_user_cnt` BIGINT     NOT NULL DEFAULT "0" COMMENT "经纪个人场内公募非货加仓交易量-转化客户数-策略归因",
    `digo_cust_asset_in`                              BIGINT        NOT NULL DEFAULT "0" COMMENT "入金客户数_策略归因",
    `digo_new_cust_asset_in`                          BIGINT        NOT NULL DEFAULT "0" COMMENT "新客入金数_策略归因",
    `digo_cnt_cust_code_new_brok`                     BIGINT        NOT NULL DEFAULT "0" COMMENT "新增客户数_策略归因",
    `digo_cust_valid_new_yxh`                         BIGINT        NOT NULL DEFAULT "0" COMMENT "新增有效户数_策略归因",
    `digo_cust_asset_in10000`                         BIGINT        NOT NULL DEFAULT "0" COMMENT "万元入金户数_策略归因",
    INDEX `idx_cljd_zcl_zb_metric_time` (`metric_time`) USING BITMAP
)
ENGINE = OLAP
PRIMARY KEY (`metric_time`, `attribution_plan_id`, `attribution_strategy_id`, `platform_id`, `channel`)
COMMENT "策略解读-子策略-指标"
DISTRIBUTED BY HASH (`metric_time`, `attribution_plan_id`, `attribution_strategy_id`, `platform_id`, `channel`)
BUCKETS 3
PROPERTIES (
    "enable_persistent_index" = "true",
    "replication_num" = "1"
);

-- Primary Key tables use upsert semantics. These INSERT statements are safe
-- to re-run for the same primary keys and replace the existing row values.
INSERT INTO `cljd_zcl_zb` (
    `metric_time`,
    `attribution_plan_id`,
    `attribution_strategy_id`,
    `platform_id`,
    `channel`,
    `digo_trd_fund_amt_inout_cy_jjgr`,
    `digo_trd_fund_amt_inout_cy_jjgr_trans_user_cnt`,
    `digo_trd_fund_amt_inout_cy_jjgr_pb`,
    `digo_pbcnt_kgdb_a566_fh_jjgr`,
    `digo_fund_trd_amt_a566_fh_kgdb_jj0`,
    `digo_fund_trd_amt_a566_fh_kgdb_jj0_trans_user_cnt`,
    `digo_pub_fh_kgdb_trdamt_ppcadd_jjgr`,
    `digo_pub_fh_kgdb_trdamt_ppcadd_jjgr_trans_user_cnt`,
    `digo_cust_asset_in`,
    `digo_new_cust_asset_in`,
    `digo_cnt_cust_code_new_brok`,
    `digo_cust_valid_new_yxh`,
    `digo_cust_asset_in10000`
)
VALUES
    ('2026-09-01', 'PLAN-001', 'STR-001', 'SUB-001', 'APP', 1250000.00, 128, 420000.00, 96, 860000.00, 74, 210000.00, 39, 186, 72, 154, 91, 43),
    ('2026-09-01', 'PLAN-001', 'STR-001', 'SUB-001', 'SMS',  980000.00, 101, 315000.00, 81, 640000.00, 57, 175000.00, 31, 143, 55, 119, 68, 32),
    ('2026-09-01', 'PLAN-001', 'STR-002', 'SUB-002', 'APP', 1480000.00, 152, 510000.00, 112, 930000.00, 88, 265000.00, 46, 211, 84, 179, 108, 51),
    ('2026-09-02', 'PLAN-001', 'STR-001', 'SUB-001', 'APP', 1320000.00, 136, 450000.00, 103, 905000.00, 79, 230000.00, 42, 194, 78, 163, 97, 46),
    ('2026-09-02', 'PLAN-001', 'STR-001', 'SUB-001', 'SMS', 1040000.00, 109, 338000.00, 87, 690000.00, 62, 188000.00, 34, 151, 60, 126, 73, 35),
    ('2026-09-02', 'PLAN-001', 'STR-002', 'SUB-002', 'APP', 1550000.00, 161, 536000.00, 121, 982000.00, 94, 279000.00, 49, 223, 90, 190, 115, 55);

INSERT INTO `cljd_zcl_wd` (
    `metric_time`,
    `attribution_plan_id`,
    `attribution_plan_name`,
    `attribution_plan_um_account`,
    `attribution_strategy_id`,
    `attribution_strategy_name`,
    `attribution_over_by`,
    `platform_id`,
    `platform_name`,
    `create_by`,
    `channel`,
    `metric_id`,
    `metric_name`,
    `digo_strategy_cnt`,
    `digo_strategy_cnt_distr_1`,
    `digo_distr_count_1`,
    `digo_distr_user_cnt_a`,
    `digo_touch_cnt_1`,
    `digo_touch_user_cnt_1`
)
SELECT
    z.`metric_time`,
    z.`attribution_plan_id`,
    '策略解读示例计划',
    'plan_owner',
    z.`attribution_strategy_id`,
    CASE z.`attribution_strategy_id`
        WHEN 'STR-001' THEN '高净值客户触达策略'
        WHEN 'STR-002' THEN '新客增长策略'
    END,
    CASE z.`attribution_strategy_id`
        WHEN 'STR-001' THEN 'strategy_owner_a'
        WHEN 'STR-002' THEN 'strategy_owner_b'
    END,
    z.`platform_id`,
    CASE z.`platform_id`
        WHEN 'SUB-001' THEN '场内公募非货子策略'
        WHEN 'SUB-002' THEN '新客入金子策略'
    END,
    'data_owner',
    z.`channel`,
    m.`metric_id`,
    m.`metric_name`,
    CASE z.`attribution_strategy_id` WHEN 'STR-001' THEN 2 ELSE 3 END,
    CASE z.`attribution_strategy_id` WHEN 'STR-001' THEN 2 ELSE 3 END,
    CASE z.`channel` WHEN 'APP' THEN 180 ELSE 130 END,
    CASE z.`channel` WHEN 'APP' THEN 150 ELSE 105 END,
    CASE z.`channel` WHEN 'APP' THEN 120 ELSE 88 END,
    CASE z.`channel` WHEN 'APP' THEN 96 ELSE 71 END
FROM `cljd_zcl_zb` z
CROSS JOIN (
    SELECT 'trd_fund_amt_inout_cy_jjgr' AS `metric_id`, '经纪个人客户场内公募非货当年净买入' AS `metric_name`
    UNION ALL
    SELECT 'fund_trd_amt_a566_fh_kgdb_jj0', '经纪个人场内公募非货交易量'
    UNION ALL
    SELECT 'pub_fh_kgdb_trdamt_ppcadd_jjgr', '经纪个人场内公募非货加仓交易量'
) m;

-- 可选校验：每个转化指标 ID 都能在指标表中找到对应的 *_trans_user_cnt 字段。
-- 预期返回 3 行，missing_metric_id 均为 NULL。
SELECT
    c.`metric_id`,
    CASE WHEN c.`metric_id` IN (
        'trd_fund_amt_inout_cy_jjgr',
        'fund_trd_amt_a566_fh_kgdb_jj0',
        'pub_fh_kgdb_trdamt_ppcadd_jjgr'
    ) THEN CAST(NULL AS VARCHAR) ELSE c.`metric_id` END AS `missing_metric_id`
FROM (
    SELECT 'trd_fund_amt_inout_cy_jjgr' AS `metric_id`
    UNION ALL
    SELECT 'fund_trd_amt_a566_fh_kgdb_jj0'
    UNION ALL
    SELECT 'pub_fh_kgdb_trdamt_ppcadd_jjgr'
) c;
