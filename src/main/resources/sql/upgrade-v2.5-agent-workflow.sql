ALTER TABLE agent_task
    ADD COLUMN state_snapshot TEXT;

ALTER TABLE agent_task
    ADD COLUMN next_stage_code VARCHAR(64);

ALTER TABLE agent_task
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0;

ALTER TABLE agent_task_stage
    ADD COLUMN stage_input TEXT;

ALTER TABLE agent_task_stage
    ADD COLUMN stage_output TEXT;
