ALTER TABLE agent_task
    ADD COLUMN request_key VARCHAR(64);

ALTER TABLE agent_task
    ADD COLUMN deadline_at TIMESTAMP;

CREATE INDEX idx_agent_task_request_status
    ON agent_task (request_key, status);

CREATE TABLE agent_task_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    sequence_no BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_agent_task_event_sequence (task_id, sequence_no),
    KEY idx_agent_task_event_task_id (task_id)
);
