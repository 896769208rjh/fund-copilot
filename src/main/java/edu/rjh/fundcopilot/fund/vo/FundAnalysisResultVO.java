package edu.rjh.fundcopilot.fund.vo;

import java.time.LocalDateTime;
import java.util.List;

public record FundAnalysisResultVO(
        FundDetailVO detail,
        FundMetricVO metrics,
        List<FundNavPointVO> navPoints,
        List<String> highlights,
        List<String> risks,
        String riskNotice,
        String dataSource,
        LocalDateTime generatedAt
) {
}
