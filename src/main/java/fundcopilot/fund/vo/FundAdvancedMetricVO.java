package fundcopilot.fund.vo;

import java.math.BigDecimal;

public record FundAdvancedMetricVO(
        BigDecimal annualizedReturn,
        BigDecimal downsideVolatility,
        BigDecimal returnDrawdownRatio,
        long observationDays,
        int sampleSize,
        String sampleBoundary
) {
}
