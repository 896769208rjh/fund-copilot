package fundcopilot.agent.model;

public record AgentModelCallTrace(
        Long taskId,
        String stageCode,
        String agentName,
        String modelName,
        AgentThinkingMode thinkingMode,
        String promptVersion,
        String outputSchema,
        int attemptNo,
        String status,
        Integer inputTokens,
        Integer outputTokens,
        int inputChars,
        int outputChars,
        long elapsedMs,
        String fallbackReason,
        String errorMessage
) {
}
