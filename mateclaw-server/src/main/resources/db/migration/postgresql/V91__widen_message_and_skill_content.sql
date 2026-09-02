-- V91: Widen mate_message.content / content_parts and mate_skill.skill_content
-- from TEXT (64KB) to MEDIUMTEXT (16MB).
--
-- TEXT caps at 65,535 bytes. A multi-turn ReAct session accumulates tool calls
-- and observations into content_parts JSON well past that cap, and a long
-- Chinese final answer (~22k chars × 3 bytes UTF-8) overflows `content`.
-- The truncation rejects the assistant message INSERT after the SSE stream
-- has already finished, so users see the reply live but it disappears on
-- page reload (only the user message survives in the DB).
--
-- Idempotent: only modifies the column when its current type is still TEXT.

ALTER TABLE mate_message ALTER COLUMN content TYPE TEXT;

ALTER TABLE mate_message ALTER COLUMN content_parts TYPE TEXT;

ALTER TABLE mate_skill ALTER COLUMN skill_content TYPE TEXT;
