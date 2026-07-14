package fundcopilot.agent.model;

import java.util.List;

public final class AgentStructuredOutputs {
    private AgentStructuredOutputs() {
    }

    public record StageNarrative(
            String summary,
            List<String> keyPoints,
            String riskNotice
    ) {
        public StageNarrative {
            keyPoints = keyPoints == null ? List.of() : List.copyOf(keyPoints);
        }

        public boolean isValid() {
            return summary != null && !summary.isBlank()
                    && !keyPoints.isEmpty()
                    && keyPoints.stream().allMatch(point -> point != null && !point.isBlank())
                    && riskNotice != null && !riskNotice.isBlank();
        }

        public String render() {
            return summary.trim()
                    + " 关键观察：" + String.join("；", keyPoints)
                    + " 风险边界：" + riskNotice.trim();
        }
    }

    public record FinalAnswer(
            String fundCode,
            String dataDate,
            String summary,
            List<String> historicalPerformance,
            List<String> riskPoints,
            String suitabilityBoundary,
            String disclaimer
    ) {
        public FinalAnswer {
            historicalPerformance = historicalPerformance == null ? List.of() : List.copyOf(historicalPerformance);
            riskPoints = riskPoints == null ? List.of() : List.copyOf(riskPoints);
        }

        public boolean isValid(String expectedFundCode) {
            return fundCode != null && fundCode.equals(expectedFundCode)
                    && dataDate != null && !dataDate.isBlank()
                    && summary != null && !summary.isBlank()
                    && !historicalPerformance.isEmpty()
                    && !riskPoints.isEmpty()
                    && suitabilityBoundary != null && !suitabilityBoundary.isBlank()
                    && disclaimer != null && !disclaimer.isBlank();
        }

        public String render() {
            return summary.trim()
                    + "\n\n基金代码：" + fundCode
                    + "\n数据日期：" + dataDate
                    + "\n历史表现：" + String.join("；", historicalPerformance)
                    + "\n风险点：" + String.join("；", riskPoints)
                    + "\n适用边界：" + suitabilityBoundary.trim()
                    + "\n\n免责声明：" + disclaimer.trim();
        }
    }
}
