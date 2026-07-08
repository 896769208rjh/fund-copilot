package fundcopilot.fund.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FundNavPointVO(
        LocalDate navDate,
        BigDecimal unitNav,
        BigDecimal accumulatedNav,
        BigDecimal dailyGrowthRate
) {
}
