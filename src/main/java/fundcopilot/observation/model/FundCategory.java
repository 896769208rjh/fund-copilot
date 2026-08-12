package fundcopilot.observation.model;

import fundcopilot.marketdata.MarketDataDtos.MarketFundUniverseItem;

import java.util.Arrays;

public enum FundCategory {
    ACTIVE_EQUITY("主动股票型", "25"),
    EQUITY_HYBRID("偏股混合型", "27"),
    BOND("债券型", "31"),
    INDEX("指数型", "26");

    private final String displayName;
    private final String eastmoneyFundType;

    FundCategory(String displayName, String eastmoneyFundType) {
        this.displayName = displayName;
        this.eastmoneyFundType = eastmoneyFundType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEastmoneyFundType() {
        return eastmoneyFundType;
    }

    public boolean accepts(MarketFundUniverseItem fund) {
        String text = (fund.fundType() == null ? "" : fund.fundType()) + " " + fund.fundName();
        return switch (this) {
            case ACTIVE_EQUITY -> text.contains("股票") && !text.contains("指数");
            case EQUITY_HYBRID -> text.contains("偏股");
            case BOND -> text.contains("债");
            case INDEX -> text.contains("指数") || text.toUpperCase().contains("ETF");
        };
    }

    public static FundCategory fromCode(String code) {
        return Arrays.stream(values())
                .filter(category -> category.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的基金类型: " + code));
    }
}
