-- Observation board reliability upgrade for existing MySQL installations.
-- Execute manually after upgrade-v3.0-fund-observation-ranking.sql.

ALTER TABLE fund_metric_daily
    ADD COLUMN source_metric_date DATE NULL AFTER metric_date;

UPDATE fund_metric_daily AS daily
JOIN fund_master AS master ON master.id = daily.master_id
JOIN fund_metric_snapshot AS snapshot ON snapshot.fund_code = master.primary_fund_code
SET daily.source_metric_date = snapshot.statistic_date
WHERE daily.source_metric_date IS NULL;

UPDATE fund_metric_daily
SET source_metric_date = metric_date
WHERE source_metric_date IS NULL;

UPDATE fund_rank_membership AS membership
LEFT JOIN (
    SELECT master_id, MAX(source_metric_date) AS latest_source_metric_date
    FROM fund_metric_daily
    GROUP BY master_id
) AS metric ON metric.master_id = membership.master_id
SET membership.last_evaluated_date = metric.latest_source_metric_date,
    membership.qualifying_streak = 0,
    membership.disqualifying_streak = 0;

ALTER TABLE fund_sync_job
    ADD COLUMN heartbeat_at TIMESTAMP NULL AFTER started_at;

UPDATE fund_sync_job
SET heartbeat_at = COALESCE(updated_at, started_at)
WHERE heartbeat_at IS NULL;

UPDATE fund_sync_job
SET status = 'FAILED',
    completed_at = COALESCE(completed_at, CURRENT_TIMESTAMP),
    error_message = COALESCE(error_message, '升级 V3.1 时终止遗留的运行中任务')
WHERE status = 'RUNNING';

ALTER TABLE fund_sync_job
    MODIFY COLUMN heartbeat_at TIMESTAMP NOT NULL;

ALTER TABLE fund_sync_job
    DROP INDEX idx_fund_sync_job_status,
    ADD KEY idx_fund_sync_job_status_heartbeat (status, heartbeat_at);

ALTER TABLE fund_rank_daily
    DROP INDEX uk_fund_rank_daily,
    ADD UNIQUE KEY uk_fund_rank_daily (master_id, fund_category, rank_date);
