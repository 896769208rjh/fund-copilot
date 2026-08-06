package fundcopilot.agent.contract;

import java.util.List;

public final class DataCollectionAgentContract {
    private DataCollectionAgentContract() {
    }

    public record Input(
            String fundCode,
            String fundName,
            String fundType,
            String latestNav,
            String navDate,
            String dataSource,
            String dataRoute,
            String dataQuality,
            int sampleSize,
            boolean stale
    ) {
    }

    public record Output(
            String qualityStatus,
            String summary,
            List<String> sourceFindings,
            List<String> dataWarnings,
            String riskBoundary
    ) {
        public Output {
            sourceFindings = immutable(sourceFindings);
            dataWarnings = immutable(dataWarnings);
        }

        public boolean isValid() {
            return hasText(qualityStatus)
                    && hasText(summary)
                    && hasItems(sourceFindings)
                    && hasItems(dataWarnings)
                    && hasText(riskBoundary);
        }

        public String render() {
            return summary.trim()
                    + " 数据来源检查：" + String.join("；", sourceFindings)
                    + " 数据限制：" + String.join("；", dataWarnings)
                    + " 风险边界：" + riskBoundary.trim();
        }
    }

    private static List<String> immutable(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static boolean hasItems(List<String> values) {
        return !values.isEmpty() && values.stream().allMatch(DataCollectionAgentContract::hasText);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
