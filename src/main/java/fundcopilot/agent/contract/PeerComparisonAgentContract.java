package fundcopilot.agent.contract;

import java.util.List;

public final class PeerComparisonAgentContract {
    private PeerComparisonAgentContract() {
    }

    public record Input(
            String fundCode,
            String fundType,
            String peerUniverse,
            List<String> peerMetrics,
            String comparisonBoundary
    ) {
        public Input {
            peerMetrics = peerMetrics == null ? List.of() : List.copyOf(peerMetrics);
        }
    }

    public record Output(
            String comparisonSummary,
            String comparability,
            List<String> metricDifferences,
            List<String> comparisonWarnings,
            String boundary
    ) {
        public Output {
            metricDifferences = immutable(metricDifferences);
            comparisonWarnings = immutable(comparisonWarnings);
        }

        public boolean isValid() {
            return hasText(comparisonSummary)
                    && hasText(comparability)
                    && hasItems(metricDifferences)
                    && hasItems(comparisonWarnings)
                    && hasText(boundary);
        }

        public String render() {
            return comparisonSummary.trim()
                    + " 可比性：" + comparability.trim()
                    + " 指标差异：" + String.join("；", metricDifferences)
                    + " 比较限制：" + String.join("；", comparisonWarnings)
                    + " 边界：" + boundary.trim();
        }
    }

    private static List<String> immutable(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static boolean hasItems(List<String> values) {
        return !values.isEmpty() && values.stream().allMatch(PeerComparisonAgentContract::hasText);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
