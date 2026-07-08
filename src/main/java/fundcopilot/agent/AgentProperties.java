package fundcopilot.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fund-copilot.agent")
public class AgentProperties {
    private String modelName = "qwen-plus";
    private boolean enableLlm = false;

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public boolean isEnableLlm() { return enableLlm; }
    public void setEnableLlm(boolean enableLlm) { this.enableLlm = enableLlm; }
}
