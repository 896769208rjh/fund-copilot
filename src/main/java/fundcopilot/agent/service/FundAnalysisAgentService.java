package fundcopilot.agent.service;

import fundcopilot.agent.dto.FundAnalysisRequestDTO;
import fundcopilot.agent.vo.AgentAnalysisResponseVO;
import fundcopilot.agent.vo.AgentStepVO;
import fundcopilot.agent.vo.FundAgentTaskVO;
import fundcopilot.fund.constant.FundConstants;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class FundAnalysisAgentService {
    private final FundAnalysisWorkflowService fundAnalysisWorkflowService;

    public FundAnalysisAgentService(FundAnalysisWorkflowService fundAnalysisWorkflowService) {
        this.fundAnalysisWorkflowService = fundAnalysisWorkflowService;
    }

    public AgentAnalysisResponseVO analyze(FundAnalysisRequestDTO requestDTO) {
        FundAgentTaskVO taskVO = fundAnalysisWorkflowService.createTask(requestDTO);
        List<AgentStepVO> steps = taskVO.stages()
                .stream()
                .map(stage -> new AgentStepVO(stage.stageName(), stage.status(), Objects.toString(stage.summary(), "已执行")))
                .toList();
        LocalDateTime generatedAt = taskVO.completedAt() == null ? LocalDateTime.now() : taskVO.completedAt();
        return new AgentAnalysisResponseVO(
                FundConstants.AGENT_NAME_FUND_ANALYSIS,
                taskVO.fundCode(),
                Objects.toString(taskVO.finalAnswer(), ""),
                taskVO.analysis(),
                steps,
                taskVO.disclaimer(),
                generatedAt
        );
    }
}
