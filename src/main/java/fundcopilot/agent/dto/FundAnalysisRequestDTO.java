package fundcopilot.agent.dto;

import jakarta.validation.constraints.NotBlank;

public record FundAnalysisRequestDTO(
        @NotBlank(message = "fundCode不能为空")
        String fundCode,
        String question,
        Boolean includeHistory,
        Boolean includeRiskNotice
) {
}
