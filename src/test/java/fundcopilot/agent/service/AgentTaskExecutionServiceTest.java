package fundcopilot.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fundcopilot.agent.dto.FundAnalysisRequestDTO;
import fundcopilot.agent.entity.AgentTaskEventDO;
import fundcopilot.agent.mapper.AgentTaskEventMapper;
import fundcopilot.agent.vo.AgentStreamEventVO;
import fundcopilot.agent.vo.FundAgentTaskVO;
import fundcopilot.fund.constant.FundConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AgentTaskExecutionServiceTest {
    @Autowired
    private AgentTaskExecutionService agentTaskExecutionService;
    @Autowired
    private FundAnalysisWorkflowService fundAnalysisWorkflowService;
    @Autowired
    private AgentTaskEventMapper agentTaskEventMapper;

    @Test
    void createAndStreamShouldPublishCompleteWorkflowEvents() {
        FundAnalysisRequestDTO requestDTO = new FundAnalysisRequestDTO(
                "000001",
                "分析这只基金的历史表现和风险",
                Boolean.TRUE,
                Boolean.TRUE
        );

        List<AgentStreamEventVO> events = agentTaskExecutionService.createAndStream(requestDTO)
                .collectList()
                .block(Duration.ofSeconds(15));

        assertThat(events).isNotNull();
        assertThat(events)
                .extracting(AgentStreamEventVO::type)
                .contains(
                        FundConstants.SSE_TASK_CREATED,
                        FundConstants.SSE_STAGE_STARTED,
                        FundConstants.SSE_STAGE_DONE,
                        FundConstants.SSE_SECTION,
                        FundConstants.SSE_DONE
                );
        AgentStreamEventVO doneEvent = events.stream()
                .filter(event -> FundConstants.SSE_DONE.equals(event.type()))
                .findFirst()
                .orElseThrow();
        assertThat(doneEvent.payload()).isInstanceOf(FundAgentTaskVO.class);
        FundAgentTaskVO completedTask = (FundAgentTaskVO) doneEvent.payload();
        assertThat(completedTask.status()).isEqualTo(FundConstants.AGENT_STATUS_SUCCESS);
        assertThat(completedTask.stages()).hasSize(7);
        assertThat(agentTaskEventMapper.selectList(new LambdaQueryWrapper<AgentTaskEventDO>()
                .eq(AgentTaskEventDO::getTaskId, completedTask.taskId()))).isNotEmpty();
        List<AgentStreamEventVO> replayedEvents = agentTaskExecutionService.streamTask(completedTask.taskId())
                .collectList()
                .block(Duration.ofSeconds(5));
        assertThat(replayedEvents)
                .extracting(AgentStreamEventVO::type)
                .contains(FundConstants.SSE_TASK_CREATED, FundConstants.SSE_DONE);
    }

    @Test
    void recoverUnfinishedTasksShouldExecutePendingTask() {
        FundAnalysisRequestDTO requestDTO = new FundAnalysisRequestDTO(
                "000001",
                "验证应用启动恢复任务",
                Boolean.TRUE,
                Boolean.TRUE
        );
        FundAgentTaskVO pendingTask = fundAnalysisWorkflowService.initializeTask(requestDTO);

        agentTaskExecutionService.recoverUnfinishedTasks();
        List<AgentStreamEventVO> events = agentTaskExecutionService.streamTask(pendingTask.taskId())
                .collectList()
                .block(Duration.ofSeconds(15));

        assertThat(events).isNotNull();
        assertThat(fundAnalysisWorkflowService.getTask(pendingTask.taskId()).status())
                .isEqualTo(FundConstants.AGENT_STATUS_SUCCESS);
    }
}
