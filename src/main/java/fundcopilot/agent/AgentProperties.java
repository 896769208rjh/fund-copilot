package fundcopilot.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fund-copilot.agent")
public class AgentProperties {
    private String baseUrl;
    private String apiKey;
    private String modelName = "gpt-5.4";
    private boolean enableLlm = false;
    private int stageMaxIterations = 3;
    private int finalMaxIterations = 6;
    private int requestTimeoutSeconds = 60;
    private int taskTimeoutSeconds = 300;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public boolean isEnableLlm() {
        return enableLlm;
    }

    public void setEnableLlm(boolean enableLlm) {
        this.enableLlm = enableLlm;
    }

    public int getStageMaxIterations() {
        return stageMaxIterations;
    }

    public void setStageMaxIterations(int stageMaxIterations) {
        this.stageMaxIterations = stageMaxIterations;
    }

    public int getFinalMaxIterations() {
        return finalMaxIterations;
    }

    public void setFinalMaxIterations(int finalMaxIterations) {
        this.finalMaxIterations = finalMaxIterations;
    }

    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    public int getTaskTimeoutSeconds() {
        return taskTimeoutSeconds;
    }

    public void setTaskTimeoutSeconds(int taskTimeoutSeconds) {
        this.taskTimeoutSeconds = taskTimeoutSeconds;
    }
}
