package fundcopilot.agent.workflow;

import java.util.List;

public final class FundStructuredReports {
    public record DataReport(
            String fundCode,
            String fundName,
            String fundType,
            String latestNav,
            String navDate,
            String dataSource,
            String dataRoute,
            String dataQuality,
            String analysisMode,
            String agentNarrative,
            int sampleSize
    ) {
    }

    public record PerformanceReport(
            String oneMonthReturn,
            String threeMonthReturn,
            String sixMonthReturn,
            String oneYearReturn,
            String annualizedReturn,
            String downsideVolatility,
            String returnDrawdownRatio,
            String sampleBoundary,
            String statisticDate,
            String analysisMode,
            String agentNarrative
    ) {
    }

    public record RiskReport(
            String riskLevel,
            String maxDrawdown,
            String volatility,
            List<String> riskItems,
            String analysisMode,
            String agentNarrative
    ) {
    }

    public record PeerComparisonReport(
            String peerUniverse,
            List<String> peers,
            String boundary,
            String analysisMode,
            String agentNarrative
    ) {
    }

    public record FactorDiscussionReport(
            List<String> positiveFactors,
            List<String> riskFactors,
            String conclusion,
            String analysisMode,
            String agentNarrative
    ) {
    }

    public record ComplianceReport(
            boolean restricted,
            String message,
            String disclaimer
    ) {
    }

    public record AnswerReport(
            String answer,
            String answerMode,
            String boundary
    ) {
    }

    private FundStructuredReports() {
    }
}
