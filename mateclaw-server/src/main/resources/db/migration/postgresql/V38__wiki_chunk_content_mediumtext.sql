-- V38: Expand mate_wiki_chunk.content from TEXT (64KB) to MEDIUMTEXT (16MB)
-- TEXT max = 65,535 bytes; a 30,000-char Chinese chunk needs ~90,000 bytes (3 bytes/char UTF-8).
-- MEDIUMTEXT supports up to 16,777,215 bytes, safely covering any realistic chunk size.

ALTER TABLE mate_wiki_chunk ALTER COLUMN content TYPE TEXT, ALTER COLUMN content SET NOT NULL;
