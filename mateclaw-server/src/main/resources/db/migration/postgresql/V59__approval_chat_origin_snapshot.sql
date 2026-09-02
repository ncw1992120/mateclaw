-- RFC-063r §2.12: persist ChatOrigin Memento snapshot on approval (MySQL dialect).

ALTER TABLE mate_tool_approval ADD COLUMN IF NOT EXISTS chat_origin TEXT;
