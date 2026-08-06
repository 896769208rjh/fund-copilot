package fundcopilot.agent.contract;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class PerformanceAgentContract {
    private PerformanceAgentContract() {
    }

    public record Input(
            String fundCode,
            String fundType,
            LocalDate statisticDate,
            BigDecimal oneMonthReturn,
            BigDecimal threeMonthReturn,
            BigDecimal sixMonthReturn,
            BigDecimal oneYearReturn,
            BigDecimal annualizedReturn,
            BigDecimal downsideVolatility,
            BigDecimal returnDrawdownRatio,
            String sampleBoundary
    ) {
    }

    public record Output(
            String trendSummary,
            List<String> returnObservations,
            List<String> stabilityObservations,
            List<String> dataLimitations
    ) {
        public Output {
            returnObservations = immutable(returnObservations);
            stabilityObservations = immutable(stabilityObservations);
            dataLimitations = immutable(dataLimitations);
        }

        public boolean isValid() {
            return hasText(trendSummary)
                    && hasItems(returnObservations)
                    && hasItems(stabilityObservations)
                    && hasItems(dataLimitations);
        }

        public String render() {
            return trendSummary.trim()
                    + " 收益观察：" + String.join("；", returnObservations)
                    + " 稳定性观察：" + String.join("；", stabilityObservations)
                    + " 数据限制：" + String.join("；", dataLimitations);
        }
    }

    private static List<String> immutable(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static boolean hasItems(List<String> values) {
        return !values.isEmpty() && values.stream().allMatch(PerformanceAgentContract::hasText);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
