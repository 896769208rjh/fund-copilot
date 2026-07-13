package fundcopilot.agent.service;

import fundcopilot.agent.model.AgentThinkingMode;
import fundcopilot.compliance.ComplianceService.ComplianceResult;
import fundcopilot.fund.vo.FundAnalysisResultVO;

import java.util.List;
import java.util.Map;

public record FundAgentStateSnapshot(
        Long taskId,
        String taskNo,
        String fundCode,
        String question,
        AgentThinkingMode thinkingMode,
        FundAnalysisResultVO analysis,
        ComplianceResult complianceResult,
        String finalAnswer,
        String dataRoute,
        String dataQuality,
        String pastContext,
        List<String> positiveFactors,
        List<String> riskFactors,
        Map<String, Object> structuredReports
) {
}
