package fundcopilot.agent.dto;

import fundcopilot.agent.model.AgentThinkingMode;
import jakarta.validation.constraints.NotBlank;

public record FundAnalysisRequestDTO(
        @NotBlank(message = "fundCode不能为空")
        String fundCode,
        String question,
        Boolean includeHistory,
        Boolean includeRiskNotice,
        AgentThinkingMode thinkingMode
) {
    public FundAnalysisRequestDTO(String fundCode,
                                  String question,
                                  Boolean includeHistory,
                                  Boolean includeRiskNotice) {
        this(fundCode, question, includeHistory, includeRiskNotice, AgentThinkingMode.defaultMode());
    }

    public AgentThinkingMode normalizedThinkingMode() {
        return AgentThinkingMode.fromNullable(thinkingMode);
    }
}
