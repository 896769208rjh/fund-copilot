package fundcopilot.agent.model;

public enum AgentThinkingMode {
    FAST("low", "快速思考"),
    BALANCED("medium", "适中思考"),
    DEEP("high", "仔细思考");

    private final String reasoningEffort;
    private final String displayName;

    AgentThinkingMode(String reasoningEffort, String displayName) {
        this.reasoningEffort = reasoningEffort;
        this.displayName = displayName;
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static AgentThinkingMode defaultMode() {
        return BALANCED;
    }

    public static AgentThinkingMode fromNullable(AgentThinkingMode thinkingMode) {
        return thinkingMode == null ? defaultMode() : thinkingMode;
    }

    public static AgentThinkingMode fromValue(String value) {
        if (value == null || value.isBlank()) {
            return defaultMode();
        }
        try {
            return AgentThinkingMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return defaultMode();
        }
    }
}
