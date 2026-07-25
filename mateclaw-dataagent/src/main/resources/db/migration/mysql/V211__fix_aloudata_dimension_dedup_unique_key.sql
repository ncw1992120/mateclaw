-- 修复 dataagent_aloudata_dimension 维度重复问题
--
-- 根因：该表缺少 (datasource_id, dim_name) 唯一约束，唯一键只有 PRIMARY(id)，
--       而 id 每次同步都是新雪花，导致 upsertBatch 的 ON DUPLICATE KEY UPDATE
--       只会在 id 撞键时触发（永不触发）→ 实为纯 INSERT，同名维度无法合并。
--       跨同步的重复靠 sync_version 版本清理掩盖，但同一次同步内源端重复列出
--       同名维度（一维挂多数据集/类目、或分页重叠）会留下同版本重复行，清理删不掉。
--
-- 对比：兄弟表 dataagent_aloudata_metric_dimension 有 uk_metric_dim(datasource_id,
--       metric_name, dim_name)，其同款 upsert 因此正常合并；本表遗漏了对称的唯一键。
--
-- 本迁移：① 清理历史重复行（每个 datasource_id + dim_name 仅保留 sync_version 最大、
--             其次 id 最大的一行）；② 去掉与唯一键完全重复的普通索引 idx_dim_ds_name，
--             并补上唯一键 uk_dim_ds_name，使既有 upsert 的 ON DUPLICATE KEY UPDATE
--             按 (datasource_id, dim_name) 生效（与关联表 uk_metric_dim 对称）。
--             upsert 的 XML/Java 无需改动。

-- ① 去重：凡存在同组（同 datasource_id + dim_name）中更优的行（更高 sync_version，
--        或同版本但更大 id）即删除，最终每组仅保留唯一“最优”行。
DELETE d FROM `dataagent_aloudata_dimension` d
JOIN `dataagent_aloudata_dimension` keep
  ON  d.`datasource_id` = keep.`datasource_id`
  AND d.`dim_name`      = keep.`dim_name`
  AND ( d.`sync_version` <  keep.`sync_version`
     OR (d.`sync_version` = keep.`sync_version` AND d.`id` < keep.`id`) );

-- ② 替换普通索引为唯一约束（idx_dim_ds_name 与 uk_dim_ds_name 列相同，前者冗余）。
ALTER TABLE `dataagent_aloudata_dimension`
    DROP INDEX `idx_dim_ds_name`,
    ADD UNIQUE KEY `uk_dim_ds_name` (`datasource_id`, `dim_name`);
