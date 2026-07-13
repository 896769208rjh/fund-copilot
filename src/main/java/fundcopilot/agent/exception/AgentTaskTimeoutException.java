package fundcopilot.agent.exception;

public class AgentTaskTimeoutException extends RuntimeException {
    public AgentTaskTimeoutException(String message) {
        super(message);
    }
}
