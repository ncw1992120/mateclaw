ALTER TABLE `dataagent_dataset`
    MODIFY COLUMN `datasource_id` BIGINT DEFAULT NULL COMMENT '关联数据源 ID；FILE 数据集为空';
