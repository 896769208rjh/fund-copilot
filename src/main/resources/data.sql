INSERT INTO fund_profile (
    fund_code, fund_name, fund_type, fund_company, fund_manager, risk_level,
    purchase_status, redeem_status, latest_nav, latest_nav_date, source_url, stale, last_sync_at
) VALUES
('000001', '华夏成长混合', '混合型', '华夏基金', '演示基金经理', '中高风险', '开放申购', '开放赎回', 1.1720, DATE '2026-07-07', 'https://fund.eastmoney.com/000001.html', FALSE, CURRENT_TIMESTAMP),
('110022', '易方达消费行业股票', '股票型', '易方达基金', '演示基金经理', '高风险', '开放申购', '开放赎回', 3.4820, DATE '2026-07-07', 'https://fund.eastmoney.com/110022.html', FALSE, CURRENT_TIMESTAMP),
('161725', '招商中证白酒指数', '指数型', '招商基金', '演示基金经理', '高风险', '开放申购', '开放赎回', 0.9120, DATE '2026-07-07', 'https://fund.eastmoney.com/161725.html', FALSE, CURRENT_TIMESTAMP);

INSERT INTO fund_nav (fund_code, nav_date, unit_nav, accumulated_nav, daily_growth_rate, source_url) VALUES
('000001', DATE '2026-07-01', 1.1380, 3.4180, 0.1800, 'https://fund.eastmoney.com/000001.html'),
('000001', DATE '2026-07-02', 1.1450, 3.4250, 0.6200, 'https://fund.eastmoney.com/000001.html'),
('000001', DATE '2026-07-03', 1.1510, 3.4310, 0.5200, 'https://fund.eastmoney.com/000001.html'),
('000001', DATE '2026-07-06', 1.1640, 3.4440, 1.1300, 'https://fund.eastmoney.com/000001.html'),
('000001', DATE '2026-07-07', 1.1720, 3.4520, 0.6900, 'https://fund.eastmoney.com/000001.html'),
('110022', DATE '2026-07-01', 3.3710, 3.3710, -0.4400, 'https://fund.eastmoney.com/110022.html'),
('110022', DATE '2026-07-02', 3.4210, 3.4210, 1.4800, 'https://fund.eastmoney.com/110022.html'),
('110022', DATE '2026-07-03', 3.3980, 3.3980, -0.6700, 'https://fund.eastmoney.com/110022.html'),
('110022', DATE '2026-07-06', 3.4630, 3.4630, 1.9100, 'https://fund.eastmoney.com/110022.html'),
('110022', DATE '2026-07-07', 3.4820, 3.4820, 0.5500, 'https://fund.eastmoney.com/110022.html'),
('161725', DATE '2026-07-01', 0.8840, 2.7120, 0.3400, 'https://fund.eastmoney.com/161725.html'),
('161725', DATE '2026-07-02', 0.8910, 2.7190, 0.7900, 'https://fund.eastmoney.com/161725.html'),
('161725', DATE '2026-07-03', 0.9030, 2.7310, 1.3500, 'https://fund.eastmoney.com/161725.html'),
('161725', DATE '2026-07-06', 0.9060, 2.7340, 0.3300, 'https://fund.eastmoney.com/161725.html'),
('161725', DATE '2026-07-07', 0.9120, 2.7400, 0.6600, 'https://fund.eastmoney.com/161725.html');

INSERT INTO fund_metric_snapshot (
    fund_code, one_month_return, three_month_return, six_month_return, one_year_return,
    max_drawdown, volatility, statistic_date
) VALUES
('000001', 2.9800, 5.1200, 7.8600, 12.4300, -6.8200, 12.1400, DATE '2026-07-07'),
('110022', 4.2200, 8.4500, 9.3100, 18.7600, -18.4200, 24.3600, DATE '2026-07-07'),
('161725', 5.1800, 12.3400, 6.4900, -4.6500, -28.7600, 31.2800, DATE '2026-07-07');

INSERT INTO alipay_fund_pool (fund_code, display_tag, focus, remark) VALUES
('000001', '支付宝常见混合基金', TRUE, '用于 Fund Copilot V1 演示'),
('110022', '消费主题', TRUE, '用于行业主题基金分析演示'),
('161725', '白酒指数', TRUE, '用于高波动指数基金分析演示');
