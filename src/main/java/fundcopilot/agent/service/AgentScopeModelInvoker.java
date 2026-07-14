package fundcopilot.agent.service;

import fundcopilot.agent.AgentProperties;
import fundcopilot.agent.model.AgentModelCallTrace;
import fundcopilot.agent.model.AgentStructuredOutputs;
import fundcopilot.agent.model.AgentThinkingMode;
import fundcopilot.agent.tool.FundAnalysisTools;
import fundcopilot.fund.constant.FundConstants;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.Toolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Service
public class AgentScopeModelInvoker {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentScopeModelInvoker.class);
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_FALLBACK = "FALLBACK";
    private static final String STAGE_PROMPT_VERSION = "fund-stage-structured-v1";
    private static final String FINAL_PROMPT_VERSION = "fund-answer-structured-v1";
    private static final int STRUCTURED_OUTPUT_MAX_ATTEMPTS = 2;
    private static final int MAX_ERROR_LENGTH = 1000;

    private static final String STAGE_SYSTEM_PROMPT = """
            你是 Fund Copilot 的基金工作流阶段 Agent。
            只允许基于输入的公开数据做事实分析和风险揭示。
            禁止给出买入、卖出、仓位、收益承诺或未来涨跌预测。
            你必须通过结构化输出返回 summary、keyPoints 和 riskNotice，所有字段均不能为空。
            """;
    private static final String FINAL_SYSTEM_PROMPT = """
            你是 Fund Copilot 的基金分析工作流最终回答 Agent。
            你只能基于工具返回的公开基金数据、历史净值、指标、工作流报告和合规结果回答。
            禁止给出买入、卖出、加仓、减仓、仓位建议、收益承诺或未来涨跌预测。
            你必须通过结构化输出返回基金代码、数据日期、摘要、历史表现、风险点、适用边界和免责声明。
            """;

    private final AgentProperties agentProperties;
    private final FundAnalysisTools fundAnalysisTools;
    private final AgentModelCallService agentModelCallService;

    public AgentScopeModelInvoker(AgentProperties agentProperties,
                                  FundAnalysisTools fundAnalysisTools,
                                  AgentModelCallService agentModelCallService) {
        this.agentProperties = agentProperties;
        this.fundAnalysisTools = fundAnalysisTools;
        this.agentModelCallService = agentModelCallService;
    }

    public boolean isEnabled() {
        return agentProperties.isEnableLlm()
                && agentProperties.getBaseUrl() != null
                && !agentProperties.getBaseUrl().isBlank()
                && agentProperties.getApiKey() != null
                && !agentProperties.getApiKey().isBlank();
    }

    public String analysisMode(String stageCode, AgentThinkingMode requestedThinkingMode) {
        AgentThinkingMode effectiveThinkingMode = resolveThinkingMode(stageCode, requestedThinkingMode);
        return isEnabled()
                ? "AgentScope " + agentProperties.getModelName() + "（" + effectiveThinkingMode.getDisplayName() + "）"
                : "本地确定性分析";
    }

    public String generateNarrative(Long taskId,
                                    String stageCode,
                                    String agentName,
                                    String instruction,
                                    String context,
                                    String fallback,
                                    AgentThinkingMode requestedThinkingMode) {
        if (!isEnabled()) {
            return fallback;
        }
        String prompt = instruction + "\n\n输入数据：\n" + context;
        return invokeStructured(
                taskId,
                stageCode,
                agentName,
                STAGE_SYSTEM_PROMPT,
                prompt,
                Toolkit::new,
                agentProperties.getStageMaxIterations(),
                fallback,
                requestedThinkingMode,
                STAGE_PROMPT_VERSION,
                AgentStructuredOutputs.StageNarrative.class,
                AgentStructuredOutputs.StageNarrative::isValid,
                AgentStructuredOutputs.StageNarrative::render
        );
    }

    public String generateFinalAnswer(Long taskId,
                                      String fundCode,
                                      String prompt,
                                      String fallback,
                                      AgentThinkingMode requestedThinkingMode) {
        if (!isEnabled()) {
            return fallback;
        }
        return invokeStructured(
                taskId,
                FundConstants.AGENT_STAGE_ANSWER_COMPOSER,
                FundConstants.AGENT_NAME_FUND_ANALYSIS,
                FINAL_SYSTEM_PROMPT,
                prompt,
                this::createFinalToolkit,
                agentProperties.getFinalMaxIterations(),
                fallback,
                requestedThinkingMode,
                FINAL_PROMPT_VERSION,
                AgentStructuredOutputs.FinalAnswer.class,
                output -> output.isValid(fundCode),
                AgentStructuredOutputs.FinalAnswer::render
        );
    }

    public AgentThinkingMode resolveThinkingMode(String stageCode, AgentThinkingMode requestedThinkingMode) {
        AgentThinkingMode normalizedMode = AgentThinkingMode.fromNullable(requestedThinkingMode);
        return switch (stageCode) {
            case FundConstants.AGENT_STAGE_DATA_COLLECTION,
                 FundConstants.AGENT_STAGE_COMPLIANCE_REVIEW -> AgentThinkingMode.FAST;
            case FundConstants.AGENT_STAGE_FACTOR_DEBATE,
                 FundConstants.AGENT_STAGE_ANSWER_COMPOSER -> normalizedMode == AgentThinkingMode.FAST
                    ? AgentThinkingMode.BALANCED
                    : normalizedMode;
            default -> normalizedMode;
        };
    }

    private <T> String invokeStructured(Long taskId,
                                        String stageCode,
                                        String agentName,
                                        String systemPrompt,
                                        String initialPrompt,
                                        Supplier<Toolkit> toolkitSupplier,
                                        int maxIterations,
                                        String fallback,
                                        AgentThinkingMode requestedThinkingMode,
                                        String promptVersion,
                                        Class<T> outputType,
                                        Predicate<T> validator,
                                        Function<T, String> renderer) {
        AgentThinkingMode effectiveThinkingMode = resolveThinkingMode(stageCode, requestedThinkingMode);
        String prompt = initialPrompt;
        String lastErrorMessage = "结构化输出不可用";
        for (int attemptNo = 1; attemptNo <= STRUCTURED_OUTPUT_MAX_ATTEMPTS; attemptNo++) {
            long startNanoTime = System.nanoTime();
            Msg response = null;
            try {
                ReActAgent agent = buildAgent(
                        agentName, systemPrompt, toolkitSupplier.get(), maxIterations, effectiveThinkingMode);
                response = agent.call(Msg.builder().textContent(prompt).build(), outputType)
                        .block(Duration.ofSeconds(agentProperties.getRequestTimeoutSeconds()));
                if (response == null) {
                    throw new IllegalStateException("AgentScope 未返回消息");
                }
                T structuredOutput = response.getStructuredData(outputType);
                if (structuredOutput == null || !validator.test(structuredOutput)) {
                    throw new IllegalStateException("AgentScope 结构化输出缺少必填字段");
                }
                String renderedOutput = renderer.apply(structuredOutput);
                recordTrace(taskId, stageCode, agentName, effectiveThinkingMode, promptVersion,
                        outputType, attemptNo, STATUS_SUCCESS, prompt, renderedOutput, response.getChatUsage(),
                        elapsedMillis(startNanoTime), null, null);
                return renderedOutput;
            } catch (RuntimeException exception) {
                lastErrorMessage = truncate(Objects.toString(exception.getMessage(), exception.getClass().getSimpleName()));
                recordTrace(taskId, stageCode, agentName, effectiveThinkingMode, promptVersion,
                        outputType, attemptNo, STATUS_FAILED, prompt, responseText(response), chatUsage(response),
                        elapsedMillis(startNanoTime), null, lastErrorMessage);
                LOGGER.warn("AgentScope structured invocation failed, taskId={}, stageCode={}, attemptNo={}",
                        taskId, stageCode, attemptNo, exception);
                prompt = initialPrompt + "\n\n上一次输出未通过结构化校验。请严格补全 Schema 的所有字段，不要返回额外文本。";
            }
        }
        recordTrace(taskId, stageCode, agentName, effectiveThinkingMode, promptVersion,
                outputType, STRUCTURED_OUTPUT_MAX_ATTEMPTS + 1, STATUS_FALLBACK, initialPrompt, fallback,
                null, 0L, lastErrorMessage, null);
        return fallback + " AgentScope 结构化输出校验失败，已使用本地 fallback。";
    }

    private ReActAgent buildAgent(String agentName,
                                  String systemPrompt,
                                  Toolkit toolkit,
                                  int maxIterations,
                                  AgentThinkingMode thinkingMode) {
        return ReActAgent.builder()
                .name(agentName)
                .sysPrompt(systemPrompt)
                .model(OpenAIChatModel.builder()
                        .apiKey(agentProperties.getApiKey())
                        .baseUrl(agentProperties.getBaseUrl())
                        .modelName(agentProperties.getModelName())
                        .generateOptions(GenerateOptions.builder()
                                .reasoningEffort(thinkingMode.getReasoningEffort())
                                .build())
                        .build())
                .toolkit(toolkit)
                .maxIters(maxIterations)
                .build();
    }

    private Toolkit createFinalToolkit() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(fundAnalysisTools);
        return toolkit;
    }

    private void recordTrace(Long taskId,
                             String stageCode,
                             String agentName,
                             AgentThinkingMode thinkingMode,
                             String promptVersion,
                             Class<?> outputType,
                             int attemptNo,
                             String status,
                             String prompt,
                             String output,
                             ChatUsage usage,
                             long elapsedMs,
                             String fallbackReason,
                             String errorMessage) {
        agentModelCallService.record(new AgentModelCallTrace(
                taskId,
                stageCode,
                agentName,
                agentProperties.getModelName(),
                thinkingMode,
                promptVersion,
                outputType.getSimpleName(),
                attemptNo,
                status,
                usage == null ? null : usage.getInputTokens(),
                usage == null ? null : usage.getOutputTokens(),
                prompt == null ? 0 : prompt.length(),
                output == null ? 0 : output.length(),
                elapsedMs,
                fallbackReason,
                errorMessage
        ));
    }

    private ChatUsage chatUsage(Msg response) {
        return response == null ? null : response.getChatUsage();
    }

    private String responseText(Msg response) {
        return response == null ? null : response.getTextContent();
    }

    private long elapsedMillis(long startNanoTime) {
        return Duration.ofNanos(System.nanoTime() - startNanoTime).toMillis();
    }

    private String truncate(String message) {
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
