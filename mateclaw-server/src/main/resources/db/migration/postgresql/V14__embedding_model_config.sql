-- V14: Embedding model UI config (对标 Dify)
-- MySQL lacks `ADD COLUMN IF NOT EXISTS`; use INFORMATION_SCHEMA guard instead.

ALTER TABLE mate_model_config ADD COLUMN IF NOT EXISTS model_type VARCHAR(32) DEFAULT 'chat';

ALTER TABLE mate_wiki_knowledge_base ADD COLUMN IF NOT EXISTS embedding_model_id BIGINT DEFAULT NULL;

INSERT INTO mate_model_config (id, name, provider, model_name, description, temperature, max_tokens, top_p, builtin, enabled, is_default, model_type, create_time, update_time, deleted)
VALUES
  (1000001001, 'Text Embedding v3', 'dashscope', 'text-embedding-v3', 'DashScope 通义千问 v3 通用文本向量模型（1024 维）', 0, 0, 0, TRUE, TRUE, TRUE, 'embedding', NOW(), NOW(), 0)
ON CONFLICT (id) DO UPDATE SET
    model_type = 'embedding';

INSERT INTO mate_model_config (id, name, provider, model_name, description, temperature, max_tokens, top_p, builtin, enabled, is_default, model_type, create_time, update_time, deleted)
VALUES
  (1000001002, 'Text Embedding v2', 'dashscope', 'text-embedding-v2', 'DashScope 通义千问 v2 文本向量模型（1536 维）', 0, 0, 0, TRUE, TRUE, FALSE, 'embedding', NOW(), NOW(), 0)
ON CONFLICT (id) DO UPDATE SET
    model_type = 'embedding';

INSERT INTO mate_system_setting (id, setting_key, setting_value, description, create_time, update_time)
VALUES
  (1000001100, 'embedding.default.model.id', '1000001001', 'Default embedding model id for wiki semantic search', NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
    setting_value = EXCLUDED.setting_value;
