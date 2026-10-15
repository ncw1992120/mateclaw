CREATE TABLE IF NOT EXISTS `dataagent_user_uid_mapping` (
    `id` BIGINT NOT NULL,
    `username` VARCHAR(128) NOT NULL,
    `tenant_id` VARCHAR(128) NOT NULL,
    `nickname` VARCHAR(128) DEFAULT NULL,
    `aloudata_uid` VARCHAR(500) NOT NULL,
    `status` TINYINT(1) NOT NULL DEFAULT 1,
    `sync_source` VARCHAR(32) DEFAULT NULL,
    `sync_time` DATETIME DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username_tenant` (`username`, `tenant_id`),
    KEY `idx_uid_mapping_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
