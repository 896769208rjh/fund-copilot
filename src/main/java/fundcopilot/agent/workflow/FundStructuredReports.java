package fundcopilot.agent.workflow;

import fundcopilot.agent.contract.AnswerComposerAgentContract;
import fundcopilot.agent.contract.ComplianceAgentContract;
import fundcopilot.agent.contract.DataCollectionAgentContract;
import fundcopilot.agent.contract.FactorDebateAgentContract;
import fundcopilot.agent.contract.PeerComparisonAgentContract;
import fundcopilot.agent.contract.PerformanceAgentContract;
import fundcopilot.agent.contract.RiskAgentContract;

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
            DataCollectionAgentContract.Output assessment,
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
            PerformanceAgentContract.Output interpretation
    ) {
    }

    public record RiskReport(
            String riskLevel,
            String maxDrawdown,
            String volatility,
            List<String> riskItems,
            String analysisMode,
            RiskAgentContract.Output assessment
    ) {
    }

    public record PeerComparisonReport(
            String peerUniverse,
            List<String> peers,
            String boundary,
            String analysisMode,
            PeerComparisonAgentContract.Output comparison
    ) {
    }

    public record FactorDiscussionReport(
            List<String> positiveFactors,
            List<String> riskFactors,
            String conclusion,
            String analysisMode,
            FactorDebateAgentContract.Output discussion
    ) {
    }

    public record ComplianceReport(
            ComplianceAgentContract.Output review
    ) {
    }

    public record AnswerReport(
            AnswerComposerAgentContract.Output answer,
            String answerMode,
            String boundary
    ) {
    }

    private FundStructuredReports() {
    }
}
