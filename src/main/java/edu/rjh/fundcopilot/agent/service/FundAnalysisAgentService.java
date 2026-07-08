package edu.rjh.fundcopilot.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.rjh.fundcopilot.agent.AgentProperties;
import edu.rjh.fundcopilot.agent.dto.FundAnalysisRequestDTO;
import edu.rjh.fundcopilot.agent.entity.AgentRunLogDO;
import edu.rjh.fundcopilot.agent.mapper.AgentRunLogMapper;
import edu.rjh.fundcopilot.agent.tool.FundAnalysisTools;
import edu.rjh.fundcopilot.agent.vo.AgentAnalysisResponseVO;
import edu.rjh.fundcopilot.agent.vo.AgentStepVO;
import edu.rjh.fundcopilot.compliance.ComplianceService;
import edu.rjh.fundcopilot.compliance.ComplianceService.ComplianceResult;
import edu.rjh.fundcopilot.fund.constant.FundConstants;
import edu.rjh.fundcopilot.fund.service.FundQueryService;
import edu.rjh.fundcopilot.fund.vo.FundAnalysisResultVO;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class FundAnalysisAgentService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FundAnalysisAgentService.class);
    private static final String DEFAULT_QUESTION = "请分析这只基金的历史表现、风险点和注意事项。";
    private static final String STEP_SUCCESS = "SUCCESS";
    private static final String STEP_SKIPPED = "SKIPPED";

    private final FundQueryService fundQueryService;
    private final ComplianceService complianceService;
    private final AgentRunLogMapper agentRunLogMapper;
    private final FundAnalysisTools fundAnalysisTools;
    private final ObjectMapper objectMapper;
    private final AgentProperties agentProperties;
    private final String dashScopeApiKey;

    public FundAnalysisAgentService(FundQueryService fundQueryService,
                                    ComplianceService complianceService,
                                    AgentRunLogMapper agentRunLogMapper,
                                    FundAnalysisTools fundAnalysisTools,
                                    ObjectMapper objectMapper,
                                    AgentProperties agentProperties,
                                    @Value("${spring.ai.dashscope.api-key:}") String dashScopeApiKey) {
        this.fundQueryService = fundQueryService;
        this.complianceService = complianceService;
        this.agentRunLogMapper = agentRunLogMapper;
        this.fundAnalysisTools = fundAnalysisTools;
        this.objectMapper = objectMapper;
        this.agentProperties = agentProperties;
        this.dashScopeApiKey = dashScopeApiKey;
    }

    public AgentAnalysisResponseVO analyze(FundAnalysisRequestDTO requestDTO) {
        long start = System.nanoTime();
        List<AgentStepVO> steps = new ArrayList<>();
        String question = normalizeQuestion(requestDTO.question());

        try {
            steps.add(new AgentStepVO("ComplianceCheckTool", STEP_SUCCESS, "检查问题是否包含买卖建议或收益承诺"));
            ComplianceResult complianceResult = complianceService.check(question);

            steps.add(new AgentStepVO("FundMetricTool", STEP_SUCCESS, "读取基金基础信息、净值和指标"));
            FundAnalysisResultVO analysisResultVO = fundQueryService.analyze(requestDTO.fundCode());

            String answer;
            if (agentProperties.isEnableLlm() && dashScopeApiKey != null && !dashScopeApiKey.isBlank()) {
                steps.add(new AgentStepVO("AgentScopeReAct", STEP_SUCCESS, "启用 AgentScope 调用基金分析工具"));
                answer = invokeAgentScope(requestDTO.fundCode(), question, complianceResult);
            } else {
                steps.add(new AgentStepVO("AgentScopeReAct", STEP_SKIPPED, "未配置 LLM，使用本地可解释分析"));
                answer = buildDeterministicAnswer(analysisResultVO, complianceResult);
            }

            AgentAnalysisResponseVO responseVO = new AgentAnalysisResponseVO(
                    FundConstants.AGENT_NAME_FUND_ANALYSIS,
                    requestDTO.fundCode(),
                    answer,
                    analysisResultVO,
                    steps,
                    ComplianceService.STANDARD_DISCLAIMER,
                    LocalDateTime.now()
            );
            saveRunLog(requestDTO, steps, FundConstants.AGENT_STATUS_SUCCESS, elapsedMillis(start), null);
            return responseVO;
        } catch (Exception exception) {
            LOGGER.error("Fund analysis agent failed, fundCode={}", requestDTO.fundCode(), exception);
            saveRunLog(requestDTO, steps, FundConstants.AGENT_STATUS_FAILED, elapsedMillis(start), exception.getMessage());
            throw exception;
        }
    }

    private String invokeAgentScope(String fundCode, String question, ComplianceResult complianceResult) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(fundAnalysisTools);

        String sysPrompt = """
                你是 Fund Copilot 的基金分析 Agent。你只能基于工具返回的公开基金数据、历史净值和合规检查结果回答。
                不允许给出买入、卖出、加仓、减仓或收益承诺。涉及投资决策时，必须转为风险、费用、历史表现和适当性解释。
                回答必须包含数据日期、风险点和免责声明。
                """;

        ReActAgent agent = ReActAgent.builder()
                .name(FundConstants.AGENT_NAME_FUND_ANALYSIS)
                .sysPrompt(sysPrompt)
                .model(DashScopeChatModel.builder()
                        .apiKey(dashScopeApiKey)
                        .modelName(agentProperties.getModelName())
                        .build())
                .toolkit(toolkit)
                .maxIters(6)
                .build();

        String prompt = "基金代码：" + fundCode + "\n用户问题：" + question + "\n合规检查：" + complianceResult.message();
        Msg response = agent.call(Msg.builder().textContent(prompt).build()).block();
        if (response == null || response.getTextContent() == null || response.getTextContent().isBlank()) {
            return "AgentScope 未返回有效文本，请稍后重试。" + ComplianceService.STANDARD_DISCLAIMER;
        }
        return response.getTextContent();
    }

    private String buildDeterministicAnswer(FundAnalysisResultVO analysisResultVO, ComplianceResult complianceResult) {
        StringBuilder builder = new StringBuilder();
        builder.append("已基于公开数据完成基金分析。");
        if (complianceResult.restricted()) {
            builder.append("你的问题涉及买卖建议，我将只提供事实分析和风险提示。");
        }
        builder.append("\n\n基金：")
                .append(analysisResultVO.detail().fundName())
                .append("（")
                .append(analysisResultVO.detail().fundCode())
                .append("）");
        builder.append("\n最新净值：")
                .append(analysisResultVO.detail().latestNav())
                .append("，日期：")
                .append(analysisResultVO.detail().latestNavDate());
        builder.append("\n近一年收益率：")
                .append(formatPercent(analysisResultVO.metrics().oneYearReturn()))
                .append("，最大回撤：")
                .append(formatPercent(analysisResultVO.metrics().maxDrawdown()))
                .append("，年化波动率：")
                .append(formatPercent(analysisResultVO.metrics().volatility()));
        builder.append("\n主要风险：")
                .append(String.join("；", analysisResultVO.risks()));
        builder.append("\n\n")
                .append(ComplianceService.STANDARD_DISCLAIMER);
        return builder.toString();
    }

    private void saveRunLog(FundAnalysisRequestDTO requestDTO,
                            List<AgentStepVO> steps,
                            String status,
                            long elapsedMs,
                            String errorMessage) {
        AgentRunLogDO logDO = new AgentRunLogDO();
        logDO.setAgentName(FundConstants.AGENT_NAME_FUND_ANALYSIS);
        logDO.setFundCode(requestDTO.fundCode());
        logDO.setQuestion(requestDTO.question());
        logDO.setStatus(status);
        logDO.setElapsedMs(elapsedMs);
        logDO.setErrorMessage(errorMessage);
        try {
            logDO.setToolTrace(objectMapper.writeValueAsString(steps));
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Serialize agent tool trace failed", exception);
            logDO.setToolTrace("[]");
        }
        agentRunLogMapper.insert(logDO);
    }

    private String normalizeQuestion(String question) {
        return question == null || question.isBlank() ? DEFAULT_QUESTION : question.trim();
    }

    private long elapsedMillis(long startNanoTime) {
        return Duration.ofNanos(System.nanoTime() - startNanoTime).toMillis();
    }

    private String formatPercent(BigDecimal value) {
        return value == null ? "暂无" : value.stripTrailingZeros().toPlainString() + "%";
    }
}
