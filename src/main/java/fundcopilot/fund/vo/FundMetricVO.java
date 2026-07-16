package fundcopilot.fund.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FundMetricVO(
        BigDecimal oneMonthReturn,
        BigDecimal threeMonthReturn,
        BigDecimal sixMonthReturn,
        BigDecimal oneYearReturn,
        BigDecimal maxDrawdown,
        BigDecimal volatility,
        LocalDate statisticDate,
        int sampleSize,
        LocalDate sampleStartDate,
        LocalDate sampleEndDate,
        long observationDays,
        String sampleBoundary
) {
}
