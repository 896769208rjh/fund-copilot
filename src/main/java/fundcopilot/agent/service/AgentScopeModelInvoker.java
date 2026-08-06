package fundcopilot.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fundcopilot.agent.AgentProperties;
import fundcopilot.agent.contract.AnswerComposerAgentContract;
import fundcopilot.agent.contract.DataCollectionAgentContract;
import fundcopilot.agent.contract.FactorDebateAgentContract;
import fundcopilot.agent.contract.PeerComparisonAgentContract;
import fundcopilot.agent.contract.PerformanceAgentContract;
import fundcopilot.agent.contract.RiskAgentContract;
import fundcopilot.agent.model.AgentModelCallTrace;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Service
public class AgentScopeModelInvoker {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentScopeModelInvoker.class);
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_FALLBACK = "FALLBACK";
    private static final int STRUCTURED_OUTPUT_MAX_ATTEMPTS = 2;
    private static final int MAX_ERROR_LENGTH = 1000;

    private static final String DATA_PROMPT_VERSION = "fund-data-quality-v1";
    private static final String PERFORMANCE_PROMPT_VERSION = "fund-performance-v1";
    private static final String RISK_PROMPT_VERSION = "fund-risk-v1";
    private static final String PEER_PROMPT_VERSION = "fund-peer-comparison-v1";
    private static final String FACTOR_PROMPT_VERSION = "fund-factor-debate-v1";
    private static final String ANSWER_PROMPT_VERSION = "fund-answer-v2";

    private static final String DATA_SYSTEM_PROMPT = """
            你是 Fund Copilot 的基金数据质量 Agent。
            只检查输入中的数据来源、样本数量、时效性和缺失项，不得修改或补造任何事实。
            禁止给出买卖建议、收益承诺或未来涨跌预测。
            输出必须包含 qualityStatus、summary、sourceFindings、dataWarnings 和 riskBoundary。
            """;
    private static final String PERFORMANCE_SYSTEM_PROMPT = """
            你是 Fund Copilot 的基金业绩分析 Agent。
            只解释输入中的历史收益和稳定性指标，不得重新计算、修改或补造数值。
            禁止使用历史数据预测未来表现，禁止给出买卖建议。
            输出必须包含 trendSummary、returnObservations、stabilityObservations 和 dataLimitations。
            """;
    private static final String RISK_SYSTEM_PROMPT = """
            你是 Fund Copilot 的基金风险分析 Agent。
            只基于输入中的风险等级、回撤、波动和已知风险做解释，不得弱化风险或补造指标。
            禁止给出仓位、买卖、收益承诺或未来涨跌预测。
            输出必须包含 riskSummary、primaryRisks、drawdownObservations、volatilityObservations 和 dataLimitations。
            """;
    private static final String PEER_SYSTEM_PROMPT = """
            你是 Fund Copilot 的同类基金比较 Agent。
            只说明输入基金之间的客观指标差异和可比性限制，不得生成购买排序或推荐结论。
            不得修改输入数值，也不得把不完整样本描述为完整区间表现。
            输出必须包含 comparisonSummary、comparability、metricDifferences、comparisonWarnings 和 boundary。
            """;
    private static final String FACTOR_SYSTEM_PROMPT = """
            你是 Fund Copilot 的优势风险讨论 Agent。
            必须同时审视输入中的正向因素和风险因素，结论保持平衡并明确未决问题。
            只允许引用输入事实，禁止给出买卖、仓位、收益承诺或未来涨跌预测。
            输出必须包含 positiveArguments、counterArguments、unresolvedIssues 和 balancedConclusion。
            """;
    private static final String ANSWER_SYSTEM_PROMPT = """
            你是 Fund Copilot 的基金分析最终回答 Agent。
            只能基于输入的冻结工作流报告、合规结果以及工具返回的公开数据回答。
            不得修改工作流中的数值，不得补造信息来源。
            禁止给出买入、卖出、加仓、减仓、仓位建议、收益承诺或未来涨跌预测。
            输出必须包含 fundCode、dataDate、summary、historicalPerformance、riskPoints、sourceSections、
            suitabilityBoundary 和 disclaimer。
            """;

    private final AgentProperties agentProperties;
    private final FundAnalysisTools fundAnalysisTools;
    private final AgentModelCallService agentModelCallService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AgentScopeModelInvoker(AgentProperties agentProperties,
                                  FundAnalysisTools fundAnalysisTools,
                                  AgentModelCallService agentModelCallService,
                                  ObjectMapper objectMapper) {
        this.agentProperties = agentProperties;
        this.fundAnalysisTools = fundAnalysisTools;
        this.agentModelCallService = agentModelCallService;
        this.objectMapper = objectMapper;
    }

    AgentScopeModelInvoker(AgentProperties agentProperties,
                           FundAnalysisTools fundAnalysisTools,
                           AgentModelCallService agentModelCallService) {
        this(agentProperties, fundAnalysisTools, agentModelCallService,
                new ObjectMapper().findAndRegisterModules());
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

    public DataCollectionAgentContract.Output assessDataQuality(
            Long taskId,
            String agentName,
            DataCollectionAgentContract.Input input,
            DataCollectionAgentContract.Output fallback,
            AgentThinkingMode thinkingMode) {
        return invokeStage(taskId, FundConstants.AGENT_STAGE_DATA_COLLECTION, agentName,
                DATA_SYSTEM_PROMPT, DATA_PROMPT_VERSION, input, fallback,
                DataCollectionAgentContract.Output.class,
                DataCollectionAgentContract.Output::isValid, thinkingMode);
    }

    public PerformanceAgentContract.Output analyzePerformance(
            Long taskId,
            String agentName,
            PerformanceAgentContract.Input input,
            PerformanceAgentContract.Output fallback,
            AgentThinkingMode thinkingMode) {
        return invokeStage(taskId, FundConstants.AGENT_STAGE_PERFORMANCE_ANALYSIS, agentName,
                PERFORMANCE_SYSTEM_PROMPT, PERFORMANCE_PROMPT_VERSION, input, fallback,
                PerformanceAgentContract.Output.class,
                PerformanceAgentContract.Output::isValid, thinkingMode);
    }

    public RiskAgentContract.Output analyzeRisk(
            Long taskId,
            String agentName,
            RiskAgentContract.Input input,
            RiskAgentContract.Output fallback,
            AgentThinkingMode thinkingMode) {
        return invokeStage(taskId, FundConstants.AGENT_STAGE_RISK_ANALYSIS, agentName,
                RISK_SYSTEM_PROMPT, RISK_PROMPT_VERSION, input, fallback,
                RiskAgentContract.Output.class,
                RiskAgentContract.Output::isValid, thinkingMode);
    }

    public PeerComparisonAgentContract.Output comparePeers(
            Long taskId,
            String agentName,
            PeerComparisonAgentContract.Input input,
            PeerComparisonAgentContract.Output fallback,
            AgentThinkingMode thinkingMode) {
        return invokeStage(taskId, FundConstants.AGENT_STAGE_PEER_COMPARISON, agentName,
                PEER_SYSTEM_PROMPT, PEER_PROMPT_VERSION, input, fallback,
                PeerComparisonAgentContract.Output.class,
                PeerComparisonAgentContract.Output::isValid, thinkingMode);
    }

    public FactorDebateAgentContract.Output discussFactors(
            Long taskId,
            String agentName,
            FactorDebateAgentContract.Input input,
            FactorDebateAgentContract.Output fallback,
            AgentThinkingMode thinkingMode) {
        return invokeStage(taskId, FundConstants.AGENT_STAGE_FACTOR_DEBATE, agentName,
                FACTOR_SYSTEM_PROMPT, FACTOR_PROMPT_VERSION, input, fallback,
                FactorDebateAgentContract.Output.class,
                FactorDebateAgentContract.Output::isValid, thinkingMode);
    }

    public AnswerComposerAgentContract.Output composeAnswer(
            Long taskId,
            String agentName,
            AnswerComposerAgentContract.Input input,
            AnswerComposerAgentContract.Output fallback,
            AgentThinkingMode thinkingMode) {
        if (!isEnabled()) {
            return fallback;
        }
        return invokeStructured(
                taskId,
                FundConstants.AGENT_STAGE_ANSWER_COMPOSER,
                agentName,
                ANSWER_SYSTEM_PROMPT,
                buildInputPrompt(input),
                this::createFinalToolkit,
                agentProperties.getFinalMaxIterations(),
                fallback,
                thinkingMode,
                ANSWER_PROMPT_VERSION,
                AnswerComposerAgentContract.Output.class,
                output -> output.isValid(input.fundCode())
                        && input.dataDate().equals(output.dataDate())
                        && input.sourceSections().containsAll(output.sourceSections())
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

    private <I, O> O invokeStage(Long taskId,
                                 String stageCode,
                                 String agentName,
                                 String systemPrompt,
                                 String promptVersion,
                                 I input,
                                 O fallback,
                                 Class<O> outputType,
                                 Predicate<O> validator,
                                 AgentThinkingMode thinkingMode) {
        if (!isEnabled()) {
            return fallback;
        }
        return invokeStructured(
                taskId,
                stageCode,
                agentName,
                systemPrompt,
                buildInputPrompt(input),
                Toolkit::new,
                agentProperties.getStageMaxIterations(),
                fallback,
                thinkingMode,
                promptVersion,
                outputType,
                validator
        );
    }

    private <T> T invokeStructured(Long taskId,
                                   String stageCode,
                                   String agentName,
                                   String systemPrompt,
                                   String initialPrompt,
                                   Supplier<Toolkit> toolkitSupplier,
                                   int maxIterations,
                                   T fallback,
                                   AgentThinkingMode requestedThinkingMode,
                                   String promptVersion,
                                   Class<T> outputType,
                                   Predicate<T> validator) {
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
                String serializedOutput = writeJson(structuredOutput);
                recordTrace(taskId, stageCode, agentName, effectiveThinkingMode, promptVersion,
                        outputType, attemptNo, STATUS_SUCCESS, prompt, serializedOutput, response.getChatUsage(),
                        elapsedMillis(startNanoTime), null, null);
                return structuredOutput;
            } catch (RuntimeException exception) {
                lastErrorMessage = truncate(Objects.toString(exception.getMessage(), exception.getClass().getSimpleName()));
                recordTrace(taskId, stageCode, agentName, effectiveThinkingMode, promptVersion,
                        outputType, attemptNo, STATUS_FAILED, prompt, responseText(response), chatUsage(response),
                        elapsedMillis(startNanoTime), null, lastErrorMessage);
                LOGGER.warn("AgentScope structured invocation failed, taskId={}, stageCode={}, attemptNo={}",
                        taskId, stageCode, attemptNo, exception);
                prompt = initialPrompt
                        + "\n\n上一次输出未通过结构化校验。请严格补全 Schema 的所有字段，不要返回额外文本。";
            }
        }
        recordTrace(taskId, stageCode, agentName, effectiveThinkingMode, promptVersion,
                outputType, STRUCTURED_OUTPUT_MAX_ATTEMPTS + 1, STATUS_FALLBACK, initialPrompt, writeJson(fallback),
                null, 0L, lastErrorMessage, null);
        return fallback;
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

    private String buildInputPrompt(Object input) {
        return "请严格依据以下 JSON 输入完成本阶段任务，不得添加输入中不存在的事实：\n" + writeJson(input);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Agent 结构化数据序列化失败", exception);
        }
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
                outputType.getCanonicalName(),
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
