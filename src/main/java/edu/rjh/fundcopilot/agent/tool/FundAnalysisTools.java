package edu.rjh.fundcopilot.agent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.rjh.fundcopilot.compliance.ComplianceService;
import edu.rjh.fundcopilot.fund.service.FundQueryService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class FundAnalysisTools {
    private final FundQueryService fundQueryService;
    private final ComplianceService complianceService;
    private final ObjectMapper objectMapper;

    public FundAnalysisTools(FundQueryService fundQueryService,
                             ComplianceService complianceService,
                             ObjectMapper objectMapper) {
        this.fundQueryService = fundQueryService;
        this.complianceService = complianceService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "get_fund_analysis", description = "Get public fund profile, NAV metrics, historical performance, and risk points.")
    public String getFundAnalysis(@ToolParam(name = "fundCode", description = "Mutual fund code, for example 000001.") String fundCode) {
        try {
            return objectMapper.writeValueAsString(fundQueryService.analyze(fundCode));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("基金分析结果序列化失败", exception);
        }
    }

    @Tool(name = "check_compliance", description = "Check whether the question asks for buy/sell advice or return promises.")
    public String checkCompliance(@ToolParam(name = "question", description = "User question.") String question) {
        try {
            return objectMapper.writeValueAsString(complianceService.check(question));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("合规检查结果序列化失败", exception);
        }
    }
}
