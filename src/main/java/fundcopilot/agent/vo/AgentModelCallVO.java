package fundcopilot.agent.vo;

import fundcopilot.agent.model.AgentThinkingMode;

import java.time.LocalDateTime;

public record AgentModelCallVO(
        Long id,
        Long taskId,
        String stageCode,
        String agentName,
        String modelName,
        AgentThinkingMode thinkingMode,
        String promptVersion,
        String outputSchema,
        Integer attemptNo,
        String status,
        Integer inputTokens,
        Integer outputTokens,
        Integer inputChars,
        Integer outputChars,
        Long elapsedMs,
        String fallbackReason,
        String errorMessage,
        LocalDateTime createdAt
) {
}
