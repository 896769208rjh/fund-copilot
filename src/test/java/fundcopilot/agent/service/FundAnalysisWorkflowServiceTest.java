package fundcopilot.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fundcopilot.agent.dto.FundAnalysisRequestDTO;
import fundcopilot.agent.entity.AgentReportSectionDO;
import fundcopilot.agent.entity.AgentTaskDO;
import fundcopilot.agent.entity.AgentTaskStageDO;
import fundcopilot.agent.mapper.AgentReportSectionMapper;
import fundcopilot.agent.mapper.AgentTaskMapper;
import fundcopilot.agent.mapper.AgentTaskStageMapper;
import fundcopilot.agent.model.AgentThinkingMode;
import fundcopilot.agent.vo.AgentStreamEventVO;
import fundcopilot.agent.vo.FundAgentTaskVO;
import fundcopilot.fund.constant.FundConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "fund-copilot.agent.enable-llm=false")
class FundAnalysisWorkflowServiceTest {
    @Autowired
    private FundAnalysisWorkflowService fundAnalysisWorkflowService;
    @Autowired
    private AgentTaskMapper agentTaskMapper;
    @Autowired
    private AgentTaskStageMapper agentTaskStageMapper;
    @Autowired
    private AgentReportSectionMapper agentReportSectionMapper;

    @Test
    void createTaskShouldPersistWorkflowStagesAndSections() {
        FundAnalysisRequestDTO requestDTO = new FundAnalysisRequestDTO(
                "000001",
                "分析一下这只基金的历史表现",
                Boolean.TRUE,
                Boolean.TRUE
        );

        FundAgentTaskVO taskVO = fundAnalysisWorkflowService.createTask(requestDTO);

        assertThat(taskVO.status()).isEqualTo(FundConstants.AGENT_STATUS_SUCCESS);
        assertThat(taskVO.analysis()).isNotNull();
        assertThat(taskVO.finalAnswer()).contains("公开数据");
        assertThat(taskVO.stages())
                .extracting("stageCode")
                .containsExactly(
                        FundConstants.AGENT_STAGE_DATA_COLLECTION,
                        FundConstants.AGENT_STAGE_PERFORMANCE_ANALYSIS,
                        FundConstants.AGENT_STAGE_RISK_ANALYSIS,
                        FundConstants.AGENT_STAGE_PEER_COMPARISON,
                        FundConstants.AGENT_STAGE_FACTOR_DEBATE,
                        FundConstants.AGENT_STAGE_COMPLIANCE_REVIEW,
                        FundConstants.AGENT_STAGE_ANSWER_COMPOSER
                );
        assertThat(taskVO.sections()).hasSizeGreaterThanOrEqualTo(7);
    }

    @Test
    void createTaskShouldMarkRestrictedQuestion() {
        FundAnalysisRequestDTO requestDTO = new FundAnalysisRequestDTO(
                "000001",
                "这只基金适合买入吗",
                Boolean.TRUE,
                Boolean.TRUE
        );

        FundAgentTaskVO taskVO = fundAnalysisWorkflowService.createTask(requestDTO);

        assertThat(taskVO.restricted()).isTrue();
        assertThat(taskVO.finalAnswer()).contains("买卖建议");
        assertThat(taskVO.finalAnswer()).contains("不构成任何投资建议");
    }

    @Test
    void replayTaskEventsShouldContainTaskStagesSectionsAndDone() {
        FundAnalysisRequestDTO requestDTO = new FundAnalysisRequestDTO(
                "000001",
                "分析一下这只基金",
                Boolean.TRUE,
                Boolean.TRUE
        );
        FundAgentTaskVO taskVO = fundAnalysisWorkflowService.createTask(requestDTO);

        List<AgentStreamEventVO> events = fundAnalysisWorkflowService.replayTaskEvents(taskVO.taskId());

        assertThat(events)
                .extracting(AgentStreamEventVO::type)
                .contains(
                        FundConstants.SSE_TASK_CREATED,
                        FundConstants.SSE_STAGE_DONE,
                        FundConstants.SSE_SECTION,
                        FundConstants.SSE_TOKEN,
                        FundConstants.SSE_DONE
                );
    }

    @Test
    void exportTaskReportShouldRenderMarkdown() {
        FundAnalysisRequestDTO requestDTO = new FundAnalysisRequestDTO(
                "000001",
                "生成一份基金分析报告",
                Boolean.TRUE,
                Boolean.TRUE
        );
        FundAgentTaskVO taskVO = fundAnalysisWorkflowService.createTask(requestDTO);

        String markdown = fundAnalysisWorkflowService.exportTaskReport(taskVO.taskId());

        assertThat(markdown).contains("# Fund Copilot 基金分析报告");
        assertThat(markdown).contains("## 执行阶段");
        assertThat(markdown).contains("## 结构化报告");
        assertThat(markdown).contains("## 最终回答");
    }

    @Test
    void createTaskShouldPersistStageAuditAndStateSnapshot() {
        FundAnalysisRequestDTO requestDTO = new FundAnalysisRequestDTO(
                "000001",
                "分析一下这只基金的风险",
                Boolean.TRUE,
                Boolean.TRUE
        );

        FundAgentTaskVO taskVO = fundAnalysisWorkflowService.createTask(requestDTO);

        assertThat(taskVO.sections())
                .anySatisfy(section -> assertThat(section.content()).contains("分析模式：本地确定性分析"));
        assertThat(taskVO.sections())
                .anySatisfy(section -> assertThat(section.content()).contains("下行波动率", "样本边界"));
        assertThat(taskVO.stages())
                .allSatisfy(stage -> {
                    assertThat(stage.stageInput()).isNotBlank();
                    assertThat(stage.stageOutput()).isNotBlank();
                });
        assertThat(agentTaskMapper.selectById(taskVO.taskId()).getStateSnapshot()).isNotBlank();
    }

    @Test
    void createTaskShouldEmitParallelGraphWave() {
        FundAnalysisRequestDTO requestDTO = new FundAnalysisRequestDTO(
                "000001",
                "验证状态图并行分支",
                Boolean.TRUE,
                Boolean.TRUE
        );
        List<AgentStreamEventVO> events = new CopyOnWriteArrayList<>();

        FundAgentTaskVO taskVO = fundAnalysisWorkflowService.createTask(requestDTO, events::add);

        assertThat(taskVO.status()).isEqualTo(FundConstants.AGENT_STATUS_SUCCESS);
        assertThat(events)
                .filteredOn(event -> FundConstants.SSE_PROGRESS.equals(event.type()))
                .extracting(event -> String.valueOf(event.payload()))
                .anySatisfy(message -> assertThat(message).contains("并行", "业绩分析", "风险分析", "同池对比"));
    }

    @Test
    void resumeTaskShouldContinueFromNextStageAndKeepAuditTrail() {
        FundAnalysisRequestDTO requestDTO = new FundAnalysisRequestDTO(
                "000001",
                "恢复后继续分析这只基金",
                Boolean.TRUE,
                Boolean.TRUE
        );
        FundAgentTaskVO createdTaskVO = fundAnalysisWorkflowService.createTask(requestDTO);
        Long taskId = createdTaskVO.taskId();

        AgentTaskDO taskDO = agentTaskMapper.selectById(taskId);
        taskDO.setStatus(FundConstants.AGENT_STATUS_FAILED);
        taskDO.setErrorMessage("模拟同池对比前中断");
        taskDO.setNextStageCode(FundConstants.AGENT_STAGE_PEER_COMPARISON);
        taskDO.setFinalAnswer(null);
        taskDO.setCompletedAt(null);
        agentTaskMapper.updateById(taskDO);

        List<String> pendingStageCodes = List.of(
                FundConstants.AGENT_STAGE_PEER_COMPARISON,
                FundConstants.AGENT_STAGE_FACTOR_DEBATE,
                FundConstants.AGENT_STAGE_COMPLIANCE_REVIEW,
                FundConstants.AGENT_STAGE_ANSWER_COMPOSER
        );
        agentTaskStageMapper.delete(new LambdaQueryWrapper<AgentTaskStageDO>()
                .eq(AgentTaskStageDO::getTaskId, taskId)
                .in(AgentTaskStageDO::getStageCode, pendingStageCodes));
        agentReportSectionMapper.delete(new LambdaQueryWrapper<AgentReportSectionDO>()
                .eq(AgentReportSectionDO::getTaskId, taskId)
                .in(AgentReportSectionDO::getStageCode, pendingStageCodes));

        FundAgentTaskVO resumedTaskVO = fundAnalysisWorkflowService.resumeTask(taskId);

        assertThat(resumedTaskVO.status()).isEqualTo(FundConstants.AGENT_STATUS_SUCCESS);
        assertThat(resumedTaskVO.retryCount()).isEqualTo(1);
        assertThat(resumedTaskVO.nextStageCode()).isNull();
        assertThat(resumedTaskVO.finalAnswer()).contains("公开数据");
        assertThat(resumedTaskVO.stages())
                .extracting("stageCode")
                .containsExactly(
                        FundConstants.AGENT_STAGE_DATA_COLLECTION,
                        FundConstants.AGENT_STAGE_PERFORMANCE_ANALYSIS,
                        FundConstants.AGENT_STAGE_RISK_ANALYSIS,
                        FundConstants.AGENT_STAGE_PEER_COMPARISON,
                        FundConstants.AGENT_STAGE_FACTOR_DEBATE,
                        FundConstants.AGENT_STAGE_COMPLIANCE_REVIEW,
                        FundConstants.AGENT_STAGE_ANSWER_COMPOSER
                );
        assertThat(resumedTaskVO.stages())
                .allSatisfy(stage -> assertThat(stage.stageOutput()).isNotBlank());
    }

    @Test
    void initializeTaskShouldReuseActiveRequest() {
        FundAnalysisRequestDTO requestDTO = new FundAnalysisRequestDTO(
                "000001",
                "验证重复创建任务",
                Boolean.TRUE,
                Boolean.TRUE
        );

        FundAgentTaskVO firstTask = fundAnalysisWorkflowService.initializeTask(requestDTO);
        FundAgentTaskVO secondTask = fundAnalysisWorkflowService.initializeTask(requestDTO);

        assertThat(secondTask.taskId()).isEqualTo(firstTask.taskId());
        assertThat(secondTask.status()).isEqualTo(FundConstants.AGENT_TASK_STATUS_PENDING);
        fundAnalysisWorkflowService.cancelTask(firstTask.taskId());
    }

    @Test
    void createTaskShouldPersistSelectedThinkingMode() {
        FundAnalysisRequestDTO requestDTO = new FundAnalysisRequestDTO(
                "000001",
                "验证仔细思考模式",
                Boolean.TRUE,
                Boolean.TRUE,
                AgentThinkingMode.DEEP
        );

        FundAgentTaskVO taskVO = fundAnalysisWorkflowService.createTask(requestDTO);
        AgentTaskDO taskDO = agentTaskMapper.selectById(taskVO.taskId());

        assertThat(taskVO.thinkingMode()).isEqualTo(AgentThinkingMode.DEEP);
        assertThat(taskDO.getThinkingMode()).isEqualTo(AgentThinkingMode.DEEP.name());
        assertThat(taskDO.getStateSnapshot()).contains("\"thinkingMode\":\"DEEP\"");
    }

    @Test
    void initializeTaskShouldNotReuseDifferentThinkingModes() {
        FundAnalysisRequestDTO fastRequest = new FundAnalysisRequestDTO(
                "000001",
                "验证思考模式幂等键",
                Boolean.TRUE,
                Boolean.TRUE,
                AgentThinkingMode.FAST
        );
        FundAnalysisRequestDTO deepRequest = new FundAnalysisRequestDTO(
                "000001",
                "验证思考模式幂等键",
                Boolean.TRUE,
                Boolean.TRUE,
                AgentThinkingMode.DEEP
        );

        FundAgentTaskVO fastTask = fundAnalysisWorkflowService.initializeTask(fastRequest);
        FundAgentTaskVO deepTask = fundAnalysisWorkflowService.initializeTask(deepRequest);

        assertThat(deepTask.taskId()).isNotEqualTo(fastTask.taskId());
        fundAnalysisWorkflowService.cancelTask(fastTask.taskId());
        fundAnalysisWorkflowService.cancelTask(deepTask.taskId());
    }

    @Test
    void cancelledTaskShouldNotStartWorkflow() {
        FundAnalysisRequestDTO requestDTO = new FundAnalysisRequestDTO(
                "000001",
                "验证取消任务",
                Boolean.TRUE,
                Boolean.TRUE
        );
        FundAgentTaskVO initializedTask = fundAnalysisWorkflowService.initializeTask(requestDTO);

        fundAnalysisWorkflowService.cancelTask(initializedTask.taskId());
        FundAgentTaskVO executedTask = fundAnalysisWorkflowService.executeTask(initializedTask.taskId(), null);

        assertThat(executedTask.status()).isEqualTo(FundConstants.AGENT_STATUS_CANCELLED);
        assertThat(executedTask.stages()).isEmpty();
    }

    @Test
    void expiredTaskShouldFinishWithTimeoutStatus() {
        FundAnalysisRequestDTO requestDTO = new FundAnalysisRequestDTO(
                "000001",
                "验证任务超时",
                Boolean.TRUE,
                Boolean.TRUE
        );
        FundAgentTaskVO initializedTask = fundAnalysisWorkflowService.initializeTask(requestDTO);
        AgentTaskDO taskDO = agentTaskMapper.selectById(initializedTask.taskId());
        taskDO.setDeadlineAt(LocalDateTime.now().minusSeconds(1));
        agentTaskMapper.updateById(taskDO);

        FundAgentTaskVO executedTask = fundAnalysisWorkflowService.executeTask(initializedTask.taskId(), null);

        assertThat(executedTask.status()).isEqualTo(FundConstants.AGENT_STATUS_TIMEOUT);
        assertThat(executedTask.errorMessage()).contains("超时");
    }

    @Test
    void prepareStageRerunShouldDeleteTargetAndFollowingStages() {
        FundAnalysisRequestDTO requestDTO = new FundAnalysisRequestDTO(
                "000001",
                "验证从风险阶段重跑",
                Boolean.TRUE,
                Boolean.TRUE
        );
        FundAgentTaskVO completedTask = fundAnalysisWorkflowService.createTask(requestDTO);

        FundAgentTaskVO pendingTask = fundAnalysisWorkflowService.prepareStageRerun(
                completedTask.taskId(), FundConstants.AGENT_STAGE_RISK_ANALYSIS);

        assertThat(pendingTask.status()).isEqualTo(FundConstants.AGENT_TASK_STATUS_PENDING);
        assertThat(pendingTask.nextStageCode()).isEqualTo(FundConstants.AGENT_STAGE_RISK_ANALYSIS);
        assertThat(pendingTask.stages())
                .extracting("stageCode")
                .containsExactly(
                        FundConstants.AGENT_STAGE_DATA_COLLECTION,
                        FundConstants.AGENT_STAGE_PERFORMANCE_ANALYSIS,
                        FundConstants.AGENT_STAGE_PEER_COMPARISON
                );

        FundAgentTaskVO rerunTask = fundAnalysisWorkflowService.resumeTask(completedTask.taskId());
        assertThat(rerunTask.status()).isEqualTo(FundConstants.AGENT_STATUS_SUCCESS);
        assertThat(rerunTask.stages()).hasSize(7);
        assertThat(rerunTask.finalAnswer()).contains("公开数据");
    }
}
