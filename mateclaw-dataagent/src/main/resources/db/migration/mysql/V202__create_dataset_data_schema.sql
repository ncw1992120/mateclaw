-- ============================================================
-- DataAgent 数据集业务数据表：保存数据集的行级业务数据
-- ============================================================

CREATE TABLE IF NOT EXISTS `dataagent_dataset_data` (
    `id`                BIGINT       NOT NULL COMMENT '主键 ID',
    `dataset_id`        BIGINT       NOT NULL COMMENT '所属数据集 ID',
    `row_data`          JSON         NOT NULL COMMENT '行数据（JSON 对象，key 为字段名，value 为字段值）',
    `row_hash`          VARCHAR(64)  DEFAULT NULL COMMENT '行数据哈希（用于变更检测）',
    `source_row_number` INT          DEFAULT NULL COMMENT '源表原始行号（用于溯源）',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_data_dataset_id` (`dataset_id`),
    KEY `idx_data_row_hash` (`dataset_id`, `row_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据集业务数据表';
