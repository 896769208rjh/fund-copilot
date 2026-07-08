package fundcopilot.agent.vo;

import fundcopilot.fund.vo.FundAnalysisResultVO;

import java.time.LocalDateTime;
import java.util.List;

public record AgentAnalysisResponseVO(
        String agentName,
        String fundCode,
        String answer,
        FundAnalysisResultVO analysis,
        List<AgentStepVO> steps,
        String disclaimer,
        LocalDateTime generatedAt
) {
}
