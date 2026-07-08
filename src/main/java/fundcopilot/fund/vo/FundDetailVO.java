package fundcopilot.fund.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FundDetailVO(
        String fundCode,
        String fundName,
        String fundType,
        String fundCompany,
        String fundManager,
        String riskLevel,
        String purchaseStatus,
        String redeemStatus,
        BigDecimal latestNav,
        LocalDate latestNavDate,
        String sourceUrl,
        Boolean stale,
        LocalDateTime lastSyncAt
) {
}
