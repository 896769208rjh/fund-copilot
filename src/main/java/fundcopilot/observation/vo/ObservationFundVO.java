package fundcopilot.observation.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ObservationFundVO(
        int rank,
        String fundCode,
        String fundName,
        BigDecimal scaleInBillions,
        BigDecimal oneMonthReturn,
        BigDecimal threeMonthReturn,
        BigDecimal sixMonthReturn,
        BigDecimal oneYearReturn,
        BigDecimal maxDrawdown,
        BigDecimal volatility,
        BigDecimal totalScore,
        String membershipStatus,
        LocalDate metricDate
) {
}
