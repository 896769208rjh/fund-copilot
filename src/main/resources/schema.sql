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
