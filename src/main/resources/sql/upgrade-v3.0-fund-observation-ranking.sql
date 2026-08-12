-- Fund Copilot V3.0: fund master data, scale universe and stable observation ranking.
-- Execute this script manually against an existing MySQL fund_copilot database.

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
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_fund_master_identity (identity_key),
    UNIQUE KEY uk_fund_master_primary_code (primary_fund_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS fund_share_class (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    master_id BIGINT NOT NULL,
    fund_code VARCHAR(16) NOT NULL,
    share_class VARCHAR(16) NOT NULL,
    primary_share BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_fund_share_class_code (fund_code),
    KEY idx_fund_share_class_master (master_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS fund_universe (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    master_id BIGINT NOT NULL,
    fund_category VARCHAR(32) NOT NULL,
    scale_rank INT NOT NULL,
    selected_date DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_fund_universe_master_category (master_id, fund_category),
    KEY idx_fund_universe_active_rank (fund_category, active, scale_rank)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS fund_metric_daily (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    master_id BIGINT NOT NULL,
    metric_date DATE NOT NULL,
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
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_fund_metric_daily (master_id, metric_date),
    KEY idx_fund_metric_daily_date_score (metric_date, eligible, total_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS fund_rank_membership (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    master_id BIGINT NOT NULL,
    fund_category VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    qualifying_streak INT NOT NULL DEFAULT 0,
    disqualifying_streak INT NOT NULL DEFAULT 0,
    last_evaluated_date DATE,
    entered_at TIMESTAMP NULL,
    exited_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_fund_rank_membership (master_id, fund_category),
    KEY idx_fund_rank_membership_active (fund_category, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_fund_rank_daily (master_id, rank_date),
    KEY idx_fund_rank_daily_published (fund_category, rank_date, visible, published_rank)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS fund_sync_job (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_type VARCHAR(32) NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NULL,
    error_message VARCHAR(2048),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_fund_sync_job_started (started_at),
    KEY idx_fund_sync_job_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
