package fundcopilot.marketdata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class MarketDataDtos {
    private MarketDataDtos() {
    }

    public record MarketFundSnapshot(
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
            boolean stale,
            LocalDateTime syncedAt,
            List<MarketNavPoint> navPoints
    ) {
    }

    public record MarketFundSearchItem(
            String fundCode,
            String fundName,
            String fundType,
            String fundCompany,
            String fundManager,
            BigDecimal latestNav,
            LocalDate latestNavDate,
            String sourceUrl
    ) {
    }

    public record MarketNavPoint(
            LocalDate navDate,
            BigDecimal unitNav,
            BigDecimal accumulatedNav,
            BigDecimal dailyGrowthRate,
            String sourceUrl
    ) {
    }
}
