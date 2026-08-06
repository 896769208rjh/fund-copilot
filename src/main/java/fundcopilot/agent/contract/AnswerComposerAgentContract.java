package fundcopilot.agent.contract;

import java.util.List;

public final class AnswerComposerAgentContract {
    private AnswerComposerAgentContract() {
    }

    public record Input(
            String fundCode,
            String dataDate,
            String question,
            String complianceMessage,
            String pastContext,
            List<String> workflowReports,
            List<String> sourceSections
    ) {
        public Input {
            workflowReports = workflowReports == null ? List.of() : List.copyOf(workflowReports);
            sourceSections = sourceSections == null ? List.of() : List.copyOf(sourceSections);
        }
    }

    public record Output(
            String fundCode,
            String dataDate,
            String summary,
            List<String> historicalPerformance,
            List<String> riskPoints,
            List<String> sourceSections,
            String suitabilityBoundary,
            String disclaimer
    ) {
        public Output {
            historicalPerformance = immutable(historicalPerformance);
            riskPoints = immutable(riskPoints);
            sourceSections = immutable(sourceSections);
        }

        public boolean isValid(String expectedFundCode) {
            return hasText(fundCode)
                    && fundCode.equals(expectedFundCode)
                    && hasText(dataDate)
                    && hasText(summary)
                    && hasItems(historicalPerformance)
                    && hasItems(riskPoints)
                    && hasItems(sourceSections)
                    && hasText(suitabilityBoundary)
                    && hasText(disclaimer);
        }

        public String render() {
            return summary.trim()
                    + "\n\n基金代码：" + fundCode
                    + "\n数据日期：" + dataDate
                    + "\n历史表现：" + String.join("；", historicalPerformance)
                    + "\n风险点：" + String.join("；", riskPoints)
                    + "\n信息来源区块：" + String.join("；", sourceSections)
                    + "\n适用边界：" + suitabilityBoundary.trim()
                    + "\n\n免责声明：" + disclaimer.trim();
        }
    }

    private static List<String> immutable(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static boolean hasItems(List<String> values) {
        return !values.isEmpty() && values.stream().allMatch(AnswerComposerAgentContract::hasText);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
