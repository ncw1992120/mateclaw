-- ============================================================
-- 工作台资源归属：语义模型 / 逻辑关系增加 owner_id
-- ============================================================
-- 落实"谁创建谁管理"：
--   owner_id 记录创建者用户 ID；
--   更新/删除时校验「创建者本人 或 工作区 admin/owner」。
-- 历史数据 owner_id 为 NULL，视为无主资源，非管理员不可修改/删除，
-- 由管理员认领或维护。

ALTER TABLE `dataagent_semantic_model`
    ADD COLUMN `owner_id` BIGINT NULL COMMENT '创建者用户ID（资源归属人）' AFTER `workspace_id`;

ALTER TABLE `dataagent_logical_relation`
    ADD COLUMN `owner_id` BIGINT NULL COMMENT '创建者用户ID（资源归属人）' AFTER `workspace_id`;
