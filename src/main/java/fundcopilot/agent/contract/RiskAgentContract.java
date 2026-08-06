package fundcopilot.agent.contract;

import java.math.BigDecimal;
import java.util.List;

public final class RiskAgentContract {
    private RiskAgentContract() {
    }

    public record Input(
            String fundCode,
            String fundType,
            String riskLevel,
            BigDecimal maxDrawdown,
            BigDecimal volatility,
            BigDecimal downsideVolatility,
            List<String> knownRisks,
            String sampleBoundary
    ) {
        public Input {
            knownRisks = knownRisks == null ? List.of() : List.copyOf(knownRisks);
        }
    }

    public record Output(
            String riskSummary,
            List<String> primaryRisks,
            List<String> drawdownObservations,
            List<String> volatilityObservations,
            List<String> dataLimitations
    ) {
        public Output {
            primaryRisks = immutable(primaryRisks);
            drawdownObservations = immutable(drawdownObservations);
            volatilityObservations = immutable(volatilityObservations);
            dataLimitations = immutable(dataLimitations);
        }

        public boolean isValid() {
            return hasText(riskSummary)
                    && hasItems(primaryRisks)
                    && hasItems(drawdownObservations)
                    && hasItems(volatilityObservations)
                    && hasItems(dataLimitations);
        }

        public String render() {
            return riskSummary.trim()
                    + " 主要风险：" + String.join("；", primaryRisks)
                    + " 回撤观察：" + String.join("；", drawdownObservations)
                    + " 波动观察：" + String.join("；", volatilityObservations)
                    + " 数据限制：" + String.join("；", dataLimitations);
        }
    }

    private static List<String> immutable(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static boolean hasItems(List<String> values) {
        return !values.isEmpty() && values.stream().allMatch(RiskAgentContract::hasText);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
