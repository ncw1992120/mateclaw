-- RFC-063r §2.9: bind a cron job to its originating channel (MySQL dialect).

ALTER TABLE mate_cron_job ADD COLUMN IF NOT EXISTS channel_id BIGINT;

ALTER TABLE mate_cron_job ADD COLUMN IF NOT EXISTS delivery_config TEXT;

CREATE INDEX IF NOT EXISTS idx_cron_channel ON mate_cron_job (channel_id);
