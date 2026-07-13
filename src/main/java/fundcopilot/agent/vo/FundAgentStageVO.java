package fundcopilot.agent.vo;

import java.time.LocalDateTime;

public record FundAgentStageVO(
        Long id,
        String stageCode,
        String stageName,
        String status,
        String summary,
        Integer sortOrder,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        Long elapsedMs,
        String errorMessage,
        String stageInput,
        String stageOutput
) {
}
