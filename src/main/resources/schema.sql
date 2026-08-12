CREATE TABLE IF NOT EXISTS fund_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fund_code VARCHAR(16) NOT NULL,
    fund_name VARCHAR(128) NOT NULL,
    fund_type VARCHAR(64),
    fund_company VARCHAR(128),
    fund_manager VARCHAR(128),
    risk_level VARCHAR(64),
    purchase_status VARCHAR(64),
    redeem_status VARCHAR(64),
    latest_nav DECIMAL(18, 6),
    latest_nav_date DATE,
    source_url VARCHAR(512),
    stale BOOLEAN NOT NULL DEFAULT FALSE,
    last_sync_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_fund_profile_code (fund_code)
);

CREATE TABLE IF NOT EXISTS fund_nav (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fund_code VARCHAR(16) NOT NULL,
    nav_date DATE NOT NULL,
    unit_nav DECIMAL(18, 6) NOT NULL,
    accumulated_nav DECIMAL(18, 6),
    daily_growth_rate DECIMAL(18, 6),
    source_url VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_fund_nav_code_date (fund_code, nav_date)
);

CREATE TABLE IF NOT EXISTS fund_metric_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fund_code VARCHAR(16) NOT NULL,
    one_month_return DECIMAL(18, 6),
    three_month_return DECIMAL(18, 6),
    six_month_return DECIMAL(18, 6),
    one_year_return DECIMAL(18, 6),
    max_drawdown DECIMAL(18, 6),
    volatility DECIMAL(18, 6),
    statistic_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_fund_metric_code (fund_code)
);

CREATE TABLE IF NOT EXISTS alipay_fund_pool (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fund_code VARCHAR(16) NOT NULL,
    display_tag VARCHAR(64),
    focus BOOLEAN NOT NULL DEFAULT TRUE,
    remark VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_alipay_fund_code (fund_code)
);

CREATE TABLE IF NOT EXISTS fund_master (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    identity_key VARCHAR(255) NOT NULL,
    primary_fund_code VARCHAR(16) NOT NULL,
    fund_name VARCHAR(128) NOT NULL,
    fund_category VARCHAR(32) NOT NULL,
    fund_company VARCHAR(128),
    fund_manager VARCHAR(128),
    latest_scale DECIMAL(20, 6),
    scale_date DATE,
    source_url VARCHAR(512),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_fund_master_identity (identity_key),
    UNIQUE KEY uk_fund_master_primary_code (primary_fund_code)
);

CREATE TABLE IF NOT EXISTS fund_share_class (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    master_id BIGINT NOT NULL,
    fund_code VARCHAR(16) NOT NULL,
    share_class VARCHAR(16) NOT NULL,
    primary_share BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_fund_share_class_code (fund_code),
    KEY idx_fund_share_class_master (master_id)
);

CREATE TABLE IF NOT EXISTS fund_universe (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    master_id BIGINT NOT NULL,
    fund_category VARCHAR(32) NOT NULL,
    scale_rank INT NOT NULL,
    selected_date DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_fund_universe_master_category (master_id, fund_category),
    KEY idx_fund_universe_active_rank (fund_category, active, scale_rank)
);

CREATE TABLE IF NOT EXISTS fund_metric_daily (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    master_id BIGINT NOT NULL,
    metric_date DATE NOT NULL,
    source_metric_date DATE,
    one_month_return DECIMAL(18, 6),
    three_month_return DECIMAL(18, 6),
    six_month_return DECIMAL(18, 6),
    one_year_return DECIMAL(18, 6),
    max_drawdown DECIMAL(18, 6),
    volatility DECIMAL(18, 6),
    return_drawdown_ratio DECIMAL(18, 6),
    sample_size INT NOT NULL DEFAULT 0,
    performance_score DECIMAL(10, 4),
    drawdown_score DECIMAL(10, 4),
    volatility_score DECIMAL(10, 4),
    ratio_score DECIMAL(10, 4),
    data_quality_score DECIMAL(10, 4),
    total_score DECIMAL(10, 4),
    eligible BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_fund_metric_daily (master_id, metric_date),
    KEY idx_fund_metric_daily_date_score (metric_date, eligible, total_score)
);

CREATE TABLE IF NOT EXISTS fund_rank_membership (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    master_id BIGINT NOT NULL,
    fund_category VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    qualifying_streak INT NOT NULL DEFAULT 0,
    disqualifying_streak INT NOT NULL DEFAULT 0,
    last_evaluated_date DATE,
    entered_at TIMESTAMP,
    exited_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_fund_rank_membership (master_id, fund_category),
    KEY idx_fund_rank_membership_active (fund_category, active)
);

CREATE TABLE IF NOT EXISTS fund_rank_daily (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    master_id BIGINT NOT NULL,
    fund_category VARCHAR(32) NOT NULL,
    rank_date DATE NOT NULL,
    raw_rank INT,
    published_rank INT,
    total_score DECIMAL(10, 4),
    visible BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_fund_rank_daily (master_id, fund_category, rank_date),
    KEY idx_fund_rank_daily_published (fund_category, rank_date, visible, published_rank)
);

CREATE TABLE IF NOT EXISTS fund_sync_job (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_type VARCHAR(32) NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    started_at TIMESTAMP NOT NULL,
    heartbeat_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    error_message VARCHAR(2048),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_fund_sync_job_started (started_at),
    KEY idx_fund_sync_job_status_heartbeat (status, heartbeat_at)
);

CREATE TABLE IF NOT EXISTS agent_run_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_name VARCHAR(64) NOT NULL,
    fund_code VARCHAR(16),
    question VARCHAR(1024),
    tool_trace TEXT,
    status VARCHAR(32) NOT NULL,
    elapsed_ms BIGINT,
    error_message VARCHAR(1024),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS agent_model_call (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    stage_code VARCHAR(64) NOT NULL,
    agent_name VARCHAR(128) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    thinking_mode VARCHAR(32) NOT NULL,
    prompt_version VARCHAR(64) NOT NULL,
    output_schema VARCHAR(128) NOT NULL,
    attempt_no INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    input_tokens INT,
    output_tokens INT,
    input_chars INT NOT NULL DEFAULT 0,
    output_chars INT NOT NULL DEFAULT 0,
    elapsed_ms BIGINT NOT NULL DEFAULT 0,
    fallback_reason VARCHAR(1024),
    error_message VARCHAR(1024),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_agent_model_call_task_stage (task_id, stage_code),
    KEY idx_agent_model_call_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS agent_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL,
    fund_code VARCHAR(16) NOT NULL,
    question VARCHAR(1024),
    thinking_mode VARCHAR(32) NOT NULL DEFAULT 'BALANCED',
    request_key VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    restricted BOOLEAN NOT NULL DEFAULT FALSE,
    final_answer TEXT,
    disclaimer VARCHAR(1024),
    error_message VARCHAR(1024),
    state_snapshot TEXT,
    next_stage_code VARCHAR(64),
    retry_count INT NOT NULL DEFAULT 0,
    deadline_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    elapsed_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_agent_task_no (task_no),
    KEY idx_agent_task_fund_code_created_at (fund_code, created_at),
    KEY idx_agent_task_request_status (request_key, status)
);

CREATE TABLE IF NOT EXISTS agent_task_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    sequence_no BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_agent_task_event_sequence (task_id, sequence_no),
    KEY idx_agent_task_event_task_id (task_id)
);

CREATE TABLE IF NOT EXISTS agent_task_stage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    stage_code VARCHAR(64) NOT NULL,
    stage_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    summary VARCHAR(2048),
    sort_order INT NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    elapsed_ms BIGINT,
    error_message VARCHAR(1024),
    stage_input TEXT,
    stage_output TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_agent_task_stage (task_id, stage_code),
    KEY idx_agent_task_stage_task_id (task_id)
);

CREATE TABLE IF NOT EXISTS agent_report_section (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    stage_code VARCHAR(64) NOT NULL,
    section_type VARCHAR(64) NOT NULL,
    title VARCHAR(128) NOT NULL,
    content TEXT NOT NULL,
    sort_order INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_agent_report_section_task_id (task_id)
);

CREATE TABLE IF NOT EXISTS agent_memory_entry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fund_code VARCHAR(16) NOT NULL,
    task_id BIGINT NOT NULL,
    question VARCHAR(1024),
    summary TEXT,
    risk_summary TEXT,
    reflection TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_agent_memory_fund_code_created_at (fund_code, created_at)
);
