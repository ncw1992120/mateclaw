-- Issue #50: deduplicate accumulated cron jobs and prevent future duplicates
-- at the DB level. MySQL doesn't allow DELETE with a subquery that scans the
-- same table directly, so we use the LEFT JOIN + IS NULL pattern.
--
-- Step 1 — purge duplicate active rows, keeping the earliest id per
-- (workspace_id, agent_id, name). Hard delete because this entity has no
-- @TableLogic; deleteById() already performs physical deletes.

DELETE FROM mate_cron_job
WHERE id NOT IN (
    SELECT MIN(id)
    FROM mate_cron_job
    GROUP BY workspace_id, agent_id, name
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_cron_job_workspace_agent_name ON mate_cron_job (workspace_id, agent_id, name);
