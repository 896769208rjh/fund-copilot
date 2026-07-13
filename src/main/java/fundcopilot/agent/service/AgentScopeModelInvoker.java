package fundcopilot.agent.service;

import fundcopilot.agent.AgentProperties;
import fundcopilot.agent.tool.FundAnalysisTools;
import fundcopilot.fund.constant.FundConstants;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AgentScopeModelInvoker {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentScopeModelInvoker.class);
    private static final String STAGE_SYSTEM_PROMPT = """
            你是 Fund Copilot 的基金工作流阶段 Agent。
            只允许基于输入的公开数据做事实分析和风险揭示。
            禁止给出买入、卖出、仓位、收益承诺或未来涨跌预测。
            输出 2 到 4 句中文短评。
            """;
    private static final String FINAL_SYSTEM_PROMPT = """
            你是 Fund Copilot 的基金分析工作流最终回答 Agent。
            你只能基于工具返回的公开基金数据、历史净值、指标、工作流结构化报告和合规检查结果回答。
            禁止给出买入、卖出、加仓、减仓、仓位建议、收益承诺或未来涨跌预测。
            回答必须包含基金代码、数据日期、历史表现、风险点、适用边界和免责声明。
            """;

    private final AgentProperties agentProperties;
    private final FundAnalysisTools fundAnalysisTools;
    private final String dashScopeApiKey;

    public AgentScopeModelInvoker(AgentProperties agentProperties,
                                  FundAnalysisTools fundAnalysisTools,
                                  @Value("${fund-copilot.agent.api-key:}") String dashScopeApiKey) {
        this.agentProperties = agentProperties;
        this.fundAnalysisTools = fundAnalysisTools;
        this.dashScopeApiKey = dashScopeApiKey;
    }

    public boolean isEnabled() {
        return agentProperties.isEnableLlm() && dashScopeApiKey != null && !dashScopeApiKey.isBlank();
    }

    public String analysisMode() {
        return isEnabled() ? "AgentScope LLM 分析" : "本地确定性分析";
    }

    public String generateNarrative(String agentName, String instruction, String context, String fallback) {
        if (!isEnabled()) {
            return fallback;
        }
        String prompt = instruction + "\n\n输入数据：\n" + context;
        return invoke(agentName, STAGE_SYSTEM_PROMPT, prompt, new Toolkit(),
                agentProperties.getStageMaxIterations(), fallback);
    }

    public String generateFinalAnswer(String prompt, String fallback) {
        if (!isEnabled()) {
            return fallback;
        }
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(fundAnalysisTools);
        return invoke(FundConstants.AGENT_NAME_FUND_ANALYSIS, FINAL_SYSTEM_PROMPT, prompt, toolkit,
                agentProperties.getFinalMaxIterations(), fallback);
    }

    private String invoke(String agentName,
                          String systemPrompt,
                          String prompt,
                          Toolkit toolkit,
                          int maxIterations,
                          String fallback) {
        try {
            ReActAgent agent = ReActAgent.builder()
                    .name(agentName)
                    .sysPrompt(systemPrompt)
                    .model(DashScopeChatModel.builder()
                            .apiKey(dashScopeApiKey)
                            .modelName(agentProperties.getModelName())
                            .build())
                    .toolkit(toolkit)
                    .maxIters(maxIterations)
                    .build();
            Msg response = agent.call(Msg.builder().textContent(prompt).build())
                    .block(Duration.ofSeconds(agentProperties.getRequestTimeoutSeconds()));
            if (response == null || response.getTextContent() == null || response.getTextContent().isBlank()) {
                LOGGER.warn("AgentScope returned empty content, agentName={}", agentName);
                return fallback + " AgentScope 未返回有效内容，已使用本地 fallback。";
            }
            return response.getTextContent();
        } catch (RuntimeException exception) {
            LOGGER.warn("AgentScope invocation failed, agentName={}", agentName, exception);
            return fallback + " AgentScope 调用失败，已使用本地 fallback。";
        }
    }
}
