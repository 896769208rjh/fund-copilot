package fundcopilot.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fundcopilot.agent.AgentProperties;
import fundcopilot.agent.dto.FundAnalysisRequestDTO;
import fundcopilot.agent.entity.AgentMemoryEntryDO;
import fundcopilot.agent.entity.AgentReportSectionDO;
import fundcopilot.agent.entity.AgentRunLogDO;
import fundcopilot.agent.entity.AgentTaskDO;
import fundcopilot.agent.entity.AgentTaskStageDO;
import fundcopilot.agent.exception.AgentTaskCancelledException;
import fundcopilot.agent.exception.AgentTaskTimeoutException;
import fundcopilot.agent.mapper.AgentMemoryEntryMapper;
import fundcopilot.agent.mapper.AgentReportSectionMapper;
import fundcopilot.agent.mapper.AgentRunLogMapper;
import fundcopilot.agent.mapper.AgentTaskMapper;
import fundcopilot.agent.mapper.AgentTaskStageMapper;
import fundcopilot.agent.model.AgentThinkingMode;
import fundcopilot.agent.vo.AgentStreamEventVO;
import fundcopilot.agent.vo.FundAgentReportSectionVO;
import fundcopilot.agent.vo.FundAgentStageVO;
import fundcopilot.agent.vo.FundAgentTaskVO;
import fundcopilot.agent.workflow.FundStageReport;
import fundcopilot.agent.workflow.FundStageResult;
import fundcopilot.agent.workflow.FundWorkflowContext;
import fundcopilot.agent.workflow.FundWorkflowGraph;
import fundcopilot.agent.workflow.FundWorkflowStage;
import fundcopilot.agent.workflow.FundWorkflowStageFactory;
import fundcopilot.compliance.ComplianceService;
import fundcopilot.fund.constant.FundConstants;
import fundcopilot.fund.service.FundQueryService;
import fundcopilot.fund.vo.FundAnalysisResultVO;
import fundcopilot.fund.vo.FundMetricVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class FundAnalysisWorkflowService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FundAnalysisWorkflowService.class);
    private static final DateTimeFormatter TASK_NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String DEFAULT_QUESTION = "请分析这只基金的历史表现、风险点和注意事项。";
    private static final int TASK_LIST_LIMIT = 20;
    private static final int SAME_FUND_MEMORY_LIMIT = 3;
    private static final int CROSS_FUND_MEMORY_LIMIT = 2;

    private final FundQueryService fundQueryService;
    private final ComplianceService complianceService;
    private final AgentTaskMapper agentTaskMapper;
    private final AgentTaskStageMapper agentTaskStageMapper;
    private final AgentReportSectionMapper agentReportSectionMapper;
    private final AgentMemoryEntryMapper agentMemoryEntryMapper;
    private final AgentRunLogMapper agentRunLogMapper;
    private final ObjectMapper objectMapper;
    private final AgentProperties agentProperties;
    private final FundWorkflowGraph workflowGraph;

    public FundAnalysisWorkflowService(FundQueryService fundQueryService,
                                       ComplianceService complianceService,
                                       AgentTaskMapper agentTaskMapper,
                                       AgentTaskStageMapper agentTaskStageMapper,
                                       AgentReportSectionMapper agentReportSectionMapper,
                                       AgentMemoryEntryMapper agentMemoryEntryMapper,
                                       AgentRunLogMapper agentRunLogMapper,
                                       ObjectMapper objectMapper,
                                       AgentProperties agentProperties,
                                       FundWorkflowStageFactory fundWorkflowStageFactory) {
        this.fundQueryService = fundQueryService;
        this.complianceService = complianceService;
        this.agentTaskMapper = agentTaskMapper;
        this.agentTaskStageMapper = agentTaskStageMapper;
        this.agentReportSectionMapper = agentReportSectionMapper;
        this.agentMemoryEntryMapper = agentMemoryEntryMapper;
        this.agentRunLogMapper = agentRunLogMapper;
        this.objectMapper = objectMapper;
        this.agentProperties = agentProperties;
        this.workflowGraph = fundWorkflowStageFactory.createGraph();
    }

    public FundAgentTaskVO createTask(FundAnalysisRequestDTO requestDTO) {
        return createTask(requestDTO, null);
    }

    public FundAgentTaskVO createTask(FundAnalysisRequestDTO requestDTO, Consumer<AgentStreamEventVO> eventConsumer) {
        FundAgentTaskVO initializedTask = initializeNewTask(requestDTO);
        return executeTask(initializedTask.taskId(), eventConsumer);
    }

    public synchronized FundAgentTaskVO initializeTask(FundAnalysisRequestDTO requestDTO) {
        String fundCode = requestDTO.fundCode().trim();
        String question = normalizeQuestion(requestDTO.question());
        String requestKey = buildRequestKey(requestDTO, fundCode, question);
        AgentTaskDO activeTask = findActiveTask(requestKey);
        if (activeTask != null) {
            return toTaskVO(activeTask, true);
        }
        return initializeNewTask(fundCode, question, requestKey, requestDTO.normalizedThinkingMode());
    }

    private FundAgentTaskVO initializeNewTask(FundAnalysisRequestDTO requestDTO) {
        String fundCode = requestDTO.fundCode().trim();
        String question = normalizeQuestion(requestDTO.question());
        return initializeNewTask(fundCode, question, buildRequestKey(requestDTO, fundCode, question),
                requestDTO.normalizedThinkingMode());
    }

    private FundAgentTaskVO initializeNewTask(String fundCode,
                                              String question,
                                              String requestKey,
                                              AgentThinkingMode thinkingMode) {
        AgentTaskDO taskDO = createTaskDO(fundCode, question, requestKey, thinkingMode);
        agentTaskMapper.insert(taskDO);
        persistTaskSnapshot(taskDO, createInitialState(taskDO));
        return buildTaskVO(taskDO, List.of(), List.of(), null);
    }

    public FundAgentTaskVO executeTask(Long taskId, Consumer<AgentStreamEventVO> eventConsumer) {
        long startNanoTime = System.nanoTime();
        AgentTaskDO taskDO = findTask(taskId);
        FundAgentTaskVO terminalTask = emitTerminalTaskIfNecessary(taskDO, eventConsumer);
        if (terminalTask != null) {
            return terminalTask;
        }

        LocalDateTime now = LocalDateTime.now();
        taskDO.setStatus(FundConstants.AGENT_TASK_STATUS_RUNNING);
        if (taskDO.getStartedAt() == null) {
            taskDO.setStartedAt(now);
        }
        taskDO.setCompletedAt(null);
        taskDO.setElapsedMs(null);
        taskDO.setErrorMessage(null);
        taskDO.setUpdatedAt(now);
        int updatedRows = agentTaskMapper.update(null, new LambdaUpdateWrapper<AgentTaskDO>()
                .eq(AgentTaskDO::getId, taskId)
                .eq(AgentTaskDO::getStatus, FundConstants.AGENT_TASK_STATUS_PENDING)
                .set(AgentTaskDO::getStatus, FundConstants.AGENT_TASK_STATUS_RUNNING)
                .set(AgentTaskDO::getStartedAt, taskDO.getStartedAt())
                .set(AgentTaskDO::getCompletedAt, null)
                .set(AgentTaskDO::getElapsedMs, null)
                .set(AgentTaskDO::getErrorMessage, null)
                .set(AgentTaskDO::getUpdatedAt, now));
        if (updatedRows == 0) {
            FundAgentTaskVO changedTask = emitTerminalTaskIfNecessary(findTask(taskId), eventConsumer);
            if (changedTask != null) {
                return changedTask;
            }
            throw new IllegalStateException("任务已被其他执行器处理: " + taskId);
        }

        FundAgentState state = restoreState(taskDO);
        emit(eventConsumer, FundConstants.SSE_TASK_CREATED, getTask(taskId));
        emit(eventConsumer, FundConstants.SSE_PROGRESS, "分析任务已创建：" + taskDO.getTaskNo());
        return executeWorkflow(taskDO, state, startNanoTime, false, eventConsumer);
    }

    public FundAgentTaskVO resumeTask(Long taskId) {
        return resumeTask(taskId, null);
    }

    public FundAgentTaskVO resumeTask(Long taskId, Consumer<AgentStreamEventVO> eventConsumer) {
        AgentTaskDO taskDO = findTask(taskId);
        if (FundConstants.AGENT_STATUS_SUCCESS.equals(taskDO.getStatus())) {
            FundAgentTaskVO taskVO = getTask(taskId);
            emit(eventConsumer, FundConstants.SSE_DONE, taskVO);
            return taskVO;
        }

        long startNanoTime = System.nanoTime();
        taskDO.setStatus(FundConstants.AGENT_TASK_STATUS_RUNNING);
        taskDO.setRetryCount(Objects.requireNonNullElse(taskDO.getRetryCount(), 0) + 1);
        taskDO.setErrorMessage(null);
        taskDO.setDeadlineAt(createDeadline());
        taskDO.setStartedAt(LocalDateTime.now());
        taskDO.setCompletedAt(null);
        taskDO.setElapsedMs(null);
        taskDO.setUpdatedAt(LocalDateTime.now());
        agentTaskMapper.updateById(taskDO);

        FundAgentState state = restoreState(taskDO);
        emit(eventConsumer, FundConstants.SSE_TASK_CREATED, getTask(taskId));
        emit(eventConsumer, FundConstants.SSE_PROGRESS, "正在从阶段 " + Objects.toString(taskDO.getNextStageCode(), "起点") + " 恢复任务");
        return executeWorkflow(taskDO, state, startNanoTime, true, eventConsumer);
    }

    public FundAgentTaskVO recoverTask(Long taskId, Consumer<AgentStreamEventVO> eventConsumer) {
        AgentTaskDO taskDO = findTask(taskId);
        FundAgentTaskVO terminalTask = emitTerminalTaskIfNecessary(taskDO, eventConsumer);
        if (terminalTask != null) {
            return terminalTask;
        }
        taskDO.setStatus(FundConstants.AGENT_TASK_STATUS_RUNNING);
        taskDO.setErrorMessage(null);
        taskDO.setCompletedAt(null);
        taskDO.setUpdatedAt(LocalDateTime.now());
        int updatedRows = agentTaskMapper.update(null, new LambdaUpdateWrapper<AgentTaskDO>()
                .eq(AgentTaskDO::getId, taskId)
                .eq(AgentTaskDO::getStatus, FundConstants.AGENT_TASK_STATUS_RUNNING)
                .set(AgentTaskDO::getErrorMessage, null)
                .set(AgentTaskDO::getCompletedAt, null)
                .set(AgentTaskDO::getUpdatedAt, taskDO.getUpdatedAt()));
        if (updatedRows == 0) {
            FundAgentTaskVO changedTask = emitTerminalTaskIfNecessary(findTask(taskId), eventConsumer);
            if (changedTask != null) {
                return changedTask;
            }
            throw new IllegalStateException("任务恢复状态已发生变化: " + taskId);
        }

        FundAgentState state = restoreState(taskDO);
        emit(eventConsumer, FundConstants.SSE_TASK_CREATED, getTask(taskId));
        emit(eventConsumer, FundConstants.SSE_PROGRESS,
                "应用重启后正在从阶段 " + Objects.toString(taskDO.getNextStageCode(), "起点") + " 恢复任务");
        return executeWorkflow(taskDO, state, System.nanoTime(), true, eventConsumer);
    }

    public FundAgentTaskVO cancelTask(Long taskId) {
        AgentTaskDO taskDO = findTask(taskId);
        if (!isActiveStatus(taskDO.getStatus())) {
            return getTask(taskId);
        }
        LocalDateTime now = LocalDateTime.now();
        taskDO.setStatus(FundConstants.AGENT_STATUS_CANCELLED);
        taskDO.setErrorMessage("任务已由用户取消");
        taskDO.setCompletedAt(now);
        taskDO.setElapsedMs(calculateElapsedMillis(taskDO, now));
        taskDO.setUpdatedAt(now);
        agentTaskMapper.updateById(taskDO);
        return getTask(taskId);
    }

    public FundAgentTaskVO prepareStageRerun(Long taskId, String stageCode) {
        AgentTaskDO taskDO = findTask(taskId);
        if (isActiveStatus(taskDO.getStatus())) {
            throw new IllegalStateException("运行中的任务不能发起阶段重跑，请先取消任务");
        }
        FundWorkflowStage targetStage = workflowGraph.findStage(stageCode)
                .orElseThrow(() -> new IllegalArgumentException("未知工作流阶段: " + stageCode));
        List<String> rerunStageCodes = workflowGraph.orderedStages()
                .stream()
                .filter(stage -> stage.sortOrder() >= targetStage.sortOrder())
                .map(FundWorkflowStage::stageCode)
                .toList();
        agentTaskStageMapper.delete(new LambdaQueryWrapper<AgentTaskStageDO>()
                .eq(AgentTaskStageDO::getTaskId, taskId)
                .in(AgentTaskStageDO::getStageCode, rerunStageCodes));
        agentReportSectionMapper.delete(new LambdaQueryWrapper<AgentReportSectionDO>()
                .eq(AgentReportSectionDO::getTaskId, taskId)
                .in(AgentReportSectionDO::getStageCode, rerunStageCodes));

        taskDO.setStatus(FundConstants.AGENT_TASK_STATUS_PENDING);
        taskDO.setRestricted(Boolean.FALSE);
        taskDO.setFinalAnswer(null);
        taskDO.setErrorMessage(null);
        taskDO.setStateSnapshot(buildRerunStateSnapshot(taskDO, rerunStageCodes));
        taskDO.setNextStageCode(stageCode);
        taskDO.setDeadlineAt(createDeadline());
        taskDO.setStartedAt(null);
        taskDO.setCompletedAt(null);
        taskDO.setElapsedMs(null);
        taskDO.setUpdatedAt(LocalDateTime.now());
        agentTaskMapper.updateById(taskDO);
        return getTask(taskId);
    }

    public List<FundAgentTaskVO> listRecoverableTasks() {
        return agentTaskMapper.selectList(new LambdaQueryWrapper<AgentTaskDO>()
                        .in(AgentTaskDO::getStatus,
                                FundConstants.AGENT_TASK_STATUS_PENDING,
                                FundConstants.AGENT_TASK_STATUS_RUNNING)
                        .orderByAsc(AgentTaskDO::getCreatedAt))
                .stream()
                .map(taskDO -> toTaskVO(taskDO, false))
                .toList();
    }

    public FundAgentTaskVO getTask(Long taskId) {
        return toTaskVO(findTask(taskId), true);
    }

    public FundAgentTaskVO failTask(Long taskId, String errorMessage) {
        AgentTaskDO taskDO = findTask(taskId);
        LocalDateTime now = LocalDateTime.now();
        taskDO.setStatus(FundConstants.AGENT_STATUS_FAILED);
        taskDO.setErrorMessage(errorMessage);
        taskDO.setCompletedAt(now);
        taskDO.setUpdatedAt(now);
        if (taskDO.getStartedAt() == null) {
            taskDO.setStartedAt(now);
        }
        taskDO.setElapsedMs(Duration.between(taskDO.getStartedAt(), now).toMillis());
        agentTaskMapper.updateById(taskDO);
        return getTask(taskId);
    }

    public String exportTaskReport(Long taskId) {
        return Objects.toString(getTask(taskId).reportMarkdown(), "");
    }

    public List<FundAgentTaskVO> listTasks(String fundCode) {
        LambdaQueryWrapper<AgentTaskDO> wrapper = new LambdaQueryWrapper<>();
        if (fundCode != null && !fundCode.isBlank()) {
            wrapper.eq(AgentTaskDO::getFundCode, fundCode.trim());
        }
        wrapper.orderByDesc(AgentTaskDO::getCreatedAt).last("limit " + TASK_LIST_LIMIT);
        return agentTaskMapper.selectList(wrapper)
                .stream()
                .map(taskDO -> toTaskVO(taskDO, false))
                .toList();
    }

    public List<AgentStreamEventVO> replayTaskEvents(Long taskId) {
        FundAgentTaskVO taskVO = getTask(taskId);
        List<AgentStreamEventVO> events = new ArrayList<>();
        events.add(new AgentStreamEventVO(FundConstants.SSE_TASK_CREATED, taskVO));
        events.add(new AgentStreamEventVO(FundConstants.SSE_PROGRESS, "正在回放历史分析任务"));
        for (FundAgentStageVO stageVO : taskVO.stages()) {
            events.add(new AgentStreamEventVO(FundConstants.SSE_STAGE_STARTED, stageVO));
            events.add(new AgentStreamEventVO(FundConstants.SSE_AGENT_STEP, toAgentStep(stageVO)));
            events.add(new AgentStreamEventVO(FundConstants.SSE_STAGE_DONE, stageVO));
        }
        for (FundAgentReportSectionVO sectionVO : taskVO.sections()) {
            events.add(new AgentStreamEventVO(FundConstants.SSE_SECTION, sectionVO));
        }
        if (Boolean.TRUE.equals(taskVO.restricted())) {
            events.add(new AgentStreamEventVO(FundConstants.SSE_COMPLIANCE_BLOCKED, "该问题已按合规规则转为事实分析和风险提示。"));
        }
        if (taskVO.analysis() != null) {
            events.add(new AgentStreamEventVO(FundConstants.SSE_CARD, taskVO.analysis()));
        }
        events.add(new AgentStreamEventVO(FundConstants.SSE_TOKEN, Objects.toString(taskVO.finalAnswer(), "")));
        if (FundConstants.AGENT_STATUS_FAILED.equals(taskVO.status())) {
            events.add(new AgentStreamEventVO(FundConstants.SSE_ERROR, taskVO.errorMessage()));
        }
        if (FundConstants.AGENT_STATUS_CANCELLED.equals(taskVO.status())) {
            events.add(new AgentStreamEventVO(FundConstants.SSE_TASK_CANCELLED, taskVO));
        }
        if (FundConstants.AGENT_STATUS_TIMEOUT.equals(taskVO.status())) {
            events.add(new AgentStreamEventVO(FundConstants.SSE_TASK_TIMEOUT, taskVO));
        }
        events.add(new AgentStreamEventVO(FundConstants.SSE_DONE, taskVO));
        return events;
    }

    private FundAgentTaskVO executeWorkflow(AgentTaskDO taskDO,
                                            FundAgentState state,
                                            long startNanoTime,
                                            boolean skipSuccessfulStages,
                                            Consumer<AgentStreamEventVO> eventConsumer) {
        try {
            FundWorkflowStage stage = resolveStartStage(taskDO, skipSuccessfulStages);
            while (stage != null) {
                ensureTaskCanContinue(taskDO);
                taskDO.setNextStageCode(stage.stageCode());
                taskDO.setUpdatedAt(LocalDateTime.now());
                int updatedRows = agentTaskMapper.update(null, new LambdaUpdateWrapper<AgentTaskDO>()
                        .eq(AgentTaskDO::getId, taskDO.getId())
                        .eq(AgentTaskDO::getStatus, FundConstants.AGENT_TASK_STATUS_RUNNING)
                        .set(AgentTaskDO::getNextStageCode, stage.stageCode())
                        .set(AgentTaskDO::getUpdatedAt, taskDO.getUpdatedAt()));
                if (updatedRows == 0) {
                    ensureTaskCanContinue(taskDO);
                    throw new IllegalStateException("任务状态已发生变化，无法开始下一阶段");
                }

                runStage(taskDO, state, stage, eventConsumer);
                ensureTaskCanContinue(taskDO);
                stage = workflowGraph.nextStage(
                                stage,
                                state,
                                skipSuccessfulStages ? this::isStageSuccess : (taskId, stageCode) -> false)
                        .orElse(null);
                taskDO.setNextStageCode(stage == null ? null : stage.stageCode());
                persistTaskSnapshot(taskDO, state);
            }

            ensureTaskCanContinue(taskDO);
            completeTask(taskDO, state, startNanoTime, FundConstants.AGENT_STATUS_SUCCESS, null);
            saveMemory(state);
            saveRunLog(taskDO, state.getStages(), FundConstants.AGENT_STATUS_SUCCESS, elapsedMillis(startNanoTime), null);
            FundAgentTaskVO taskVO = getTask(taskDO.getId());
            emit(eventConsumer, FundConstants.SSE_CARD, state.getAnalysis());
            emit(eventConsumer, FundConstants.SSE_TOKEN, state.getFinalAnswer());
            emit(eventConsumer, FundConstants.SSE_DONE, taskVO);
            return taskVO;
        } catch (AgentTaskCancelledException exception) {
            return completeControlledTask(taskDO, state, startNanoTime,
                    FundConstants.AGENT_STATUS_CANCELLED, FundConstants.SSE_TASK_CANCELLED,
                    exception.getMessage(), eventConsumer);
        } catch (AgentTaskTimeoutException exception) {
            return completeControlledTask(taskDO, state, startNanoTime,
                    FundConstants.AGENT_STATUS_TIMEOUT, FundConstants.SSE_TASK_TIMEOUT,
                    exception.getMessage(), eventConsumer);
        } catch (Exception exception) {
            LOGGER.error("Fund analysis workflow failed, taskId={}, fundCode={}", taskDO.getId(), taskDO.getFundCode(), exception);
            completeTask(taskDO, state, startNanoTime, FundConstants.AGENT_STATUS_FAILED, exception.getMessage());
            saveRunLog(taskDO, state.getStages(), FundConstants.AGENT_STATUS_FAILED, elapsedMillis(startNanoTime), exception.getMessage());
            FundAgentTaskVO taskVO = getTask(taskDO.getId());
            emit(eventConsumer, FundConstants.SSE_ERROR, exception.getMessage());
            emit(eventConsumer, FundConstants.SSE_DONE, taskVO);
            return taskVO;
        }
    }

    private FundAgentTaskVO completeControlledTask(AgentTaskDO taskDO,
                                                    FundAgentState state,
                                                    long startNanoTime,
                                                    String status,
                                                    String eventType,
                                                    String message,
                                                    Consumer<AgentStreamEventVO> eventConsumer) {
        LOGGER.info("Fund analysis workflow stopped, taskId={}, status={}", taskDO.getId(), status);
        completeTask(taskDO, state, startNanoTime, status, message);
        saveRunLog(taskDO, state.getStages(), status, elapsedMillis(startNanoTime), message);
        FundAgentTaskVO taskVO = getTask(taskDO.getId());
        emit(eventConsumer, eventType, taskVO);
        emit(eventConsumer, FundConstants.SSE_DONE, taskVO);
        return taskVO;
    }

    private void runStage(AgentTaskDO taskDO,
                          FundAgentState state,
                          FundWorkflowStage stage,
                          Consumer<AgentStreamEventVO> eventConsumer) {
        long startNanoTime = System.nanoTime();
        AgentTaskStageDO stageDO = beginStage(taskDO.getId(), stage);
        stageDO.setStageInput(buildStageInput(stage, state));
        agentTaskStageMapper.updateById(stageDO);
        deleteStageSections(taskDO.getId(), stage.stageCode());
        state.removeSectionsByStage(stage.stageCode());

        FundAgentStageVO startedStageVO = toStageVO(stageDO);
        state.addStage(startedStageVO);
        emit(eventConsumer, FundConstants.SSE_STAGE_STARTED, startedStageVO);
        emit(eventConsumer, FundConstants.SSE_AGENT_STEP, toAgentStep(startedStageVO));

        try {
            FundStageResult result = stage.execute(new FundWorkflowContext(state));
            validateStageResult(stage, result);
            for (FundStageReport report : result.reports()) {
                state.putStructuredReport(stage.stageCode(), report.structuredData());
                addSection(state, eventConsumer, stage.stageCode(), report);
            }
            for (AgentStreamEventVO eventVO : result.events()) {
                emit(eventConsumer, eventVO.type(), eventVO.payload());
            }
            syncTaskFromState(taskDO, state);
            completeStage(stageDO, FundConstants.AGENT_STATUS_SUCCESS, result.summary(), elapsedMillis(startNanoTime), null,
                    buildStageOutput(result));
            FundAgentStageVO completedStageVO = toStageVO(stageDO);
            state.addStage(completedStageVO);
            emit(eventConsumer, FundConstants.SSE_STAGE_DONE, completedStageVO);
            emit(eventConsumer, FundConstants.SSE_AGENT_STEP, toAgentStep(completedStageVO));
        } catch (Exception exception) {
            completeStage(stageDO, FundConstants.AGENT_STATUS_FAILED, "阶段执行失败", elapsedMillis(startNanoTime), exception.getMessage(),
                    "ERROR: " + exception.getMessage());
            FundAgentStageVO failedStageVO = toStageVO(stageDO);
            state.addStage(failedStageVO);
            emit(eventConsumer, FundConstants.SSE_STAGE_DONE, failedStageVO);
            emit(eventConsumer, FundConstants.SSE_AGENT_STEP, toAgentStep(failedStageVO));
            throw exception;
        }
    }

    private FundAgentState createInitialState(AgentTaskDO taskDO) {
        FundAgentState state = new FundAgentState(
                taskDO.getId(),
                taskDO.getTaskNo(),
                taskDO.getFundCode(),
                taskDO.getQuestion(),
                AgentThinkingMode.fromValue(taskDO.getThinkingMode())
        );
        state.setPastContext(loadMemoryContext(taskDO.getFundCode()));
        return state;
    }

    private FundAgentState restoreState(AgentTaskDO taskDO) {
        FundAgentState state = restoreStateSnapshot(taskDO);
        if (state.getAnalysis() != null) {
            state.setRiskFactors(state.getAnalysis().risks());
        }
        state.addStages(loadStages(taskDO.getId()));
        state.addSections(loadSections(taskDO.getId()));
        return state;
    }

    private AgentTaskStageDO beginStage(Long taskId, FundWorkflowStage stage) {
        AgentTaskStageDO stageDO = agentTaskStageMapper.selectOne(new LambdaQueryWrapper<AgentTaskStageDO>()
                .eq(AgentTaskStageDO::getTaskId, taskId)
                .eq(AgentTaskStageDO::getStageCode, stage.stageCode()));
        LocalDateTime now = LocalDateTime.now();
        if (stageDO == null) {
            stageDO = new AgentTaskStageDO();
            stageDO.setTaskId(taskId);
            stageDO.setStageCode(stage.stageCode());
            stageDO.setStageName(stage.stageName());
            stageDO.setSortOrder(stage.sortOrder());
            stageDO.setCreatedAt(now);
            stageDO.setStartedAt(now);
            stageDO.setStatus(FundConstants.AGENT_STAGE_STATUS_RUNNING);
            stageDO.setStageOutput(null);
            stageDO.setUpdatedAt(now);
            agentTaskStageMapper.insert(stageDO);
            return stageDO;
        }

        stageDO.setStageName(stage.stageName());
        stageDO.setSortOrder(stage.sortOrder());
        stageDO.setStatus(FundConstants.AGENT_STAGE_STATUS_RUNNING);
        stageDO.setSummary(null);
        stageDO.setErrorMessage(null);
        stageDO.setStartedAt(now);
        stageDO.setCompletedAt(null);
        stageDO.setElapsedMs(null);
        stageDO.setStageOutput(null);
        stageDO.setUpdatedAt(now);
        agentTaskStageMapper.updateById(stageDO);
        return stageDO;
    }

    private void completeStage(AgentTaskStageDO stageDO,
                               String status,
                               String summary,
                               long elapsedMs,
                               String errorMessage,
                               String stageOutput) {
        stageDO.setStatus(status);
        stageDO.setSummary(summary);
        stageDO.setElapsedMs(elapsedMs);
        stageDO.setErrorMessage(errorMessage);
        stageDO.setStageOutput(stageOutput);
        stageDO.setCompletedAt(LocalDateTime.now());
        stageDO.setUpdatedAt(LocalDateTime.now());
        agentTaskStageMapper.updateById(stageDO);
    }

    private void addSection(FundAgentState state,
                            Consumer<AgentStreamEventVO> eventConsumer,
                            String stageCode,
                            FundStageReport report) {
        AgentReportSectionDO sectionDO = new AgentReportSectionDO();
        sectionDO.setTaskId(state.getTaskId());
        sectionDO.setStageCode(stageCode);
        sectionDO.setSectionType(report.sectionType());
        sectionDO.setTitle(report.title());
        sectionDO.setContent(report.content());
        sectionDO.setSortOrder(state.nextSectionOrder());
        sectionDO.setCreatedAt(LocalDateTime.now());
        agentReportSectionMapper.insert(sectionDO);
        FundAgentReportSectionVO sectionVO = toSectionVO(sectionDO);
        state.addSection(sectionVO);
        emit(eventConsumer, FundConstants.SSE_SECTION, sectionVO);
    }

    private void deleteStageSections(Long taskId, String stageCode) {
        agentReportSectionMapper.delete(new LambdaQueryWrapper<AgentReportSectionDO>()
                .eq(AgentReportSectionDO::getTaskId, taskId)
                .eq(AgentReportSectionDO::getStageCode, stageCode));
    }

    private boolean isStageSuccess(Long taskId, String stageCode) {
        AgentTaskStageDO stageDO = agentTaskStageMapper.selectOne(new LambdaQueryWrapper<AgentTaskStageDO>()
                .eq(AgentTaskStageDO::getTaskId, taskId)
                .eq(AgentTaskStageDO::getStageCode, stageCode));
        return stageDO != null && FundConstants.AGENT_STATUS_SUCCESS.equals(stageDO.getStatus());
    }

    private FundWorkflowStage resolveStartStage(AgentTaskDO taskDO, boolean skipSuccessfulStages) {
        FundWorkflowStage stage = taskDO.getNextStageCode() == null || taskDO.getNextStageCode().isBlank()
                ? workflowGraph.startStage().orElseThrow(() -> new IllegalStateException("基金分析工作流缺少起始节点"))
                : workflowGraph.findStage(taskDO.getNextStageCode())
                .orElseThrow(() -> new IllegalArgumentException("未知恢复阶段: " + taskDO.getNextStageCode()));
        while (skipSuccessfulStages && isStageSuccess(taskDO.getId(), stage.stageCode())) {
            stage = workflowGraph.nextStage(stage, restoreState(taskDO), this::isStageSuccess).orElse(null);
            if (stage == null) {
                break;
            }
        }
        return stage;
    }

    private FundAgentState restoreStateSnapshot(AgentTaskDO taskDO) {
        if (taskDO.getStateSnapshot() != null && !taskDO.getStateSnapshot().isBlank()) {
            try {
                FundAgentStateSnapshot snapshot = objectMapper.readValue(taskDO.getStateSnapshot(), FundAgentStateSnapshot.class);
                FundAgentState state = FundAgentState.fromSnapshot(snapshot);
                if (state.getPastContext() == null || state.getPastContext().isBlank()) {
                    state.setPastContext(loadMemoryContext(taskDO.getFundCode()));
                }
                return state;
            } catch (Exception exception) {
                LOGGER.warn("Restore state snapshot failed, taskId={}, fallback to persisted task data", taskDO.getId(), exception);
            }
        }

        FundAgentState state = createInitialState(taskDO);
        state.setFinalAnswer(taskDO.getFinalAnswer());
        state.setComplianceResult(complianceService.check(taskDO.getQuestion()));
        state.setAnalysis(tryAnalyze(taskDO.getFundCode()));
        return state;
    }

    private void validateStageResult(FundWorkflowStage stage, FundStageResult result) {
        if (result == null) {
            throw new IllegalStateException(stage.stageName() + " 未返回阶段结果");
        }
        if (result.summary() == null || result.summary().isBlank()) {
            throw new IllegalStateException(stage.stageName() + " 未返回阶段摘要");
        }
        if (result.reports() == null || result.reports().isEmpty()) {
            throw new IllegalStateException(stage.stageName() + " 未返回结构化报告");
        }
        for (FundStageReport report : result.reports()) {
            if (report.title() == null || report.title().isBlank()) {
                throw new IllegalStateException(stage.stageName() + " 存在空报告标题");
            }
            if (report.content() == null || report.content().isBlank()) {
                throw new IllegalStateException(stage.stageName() + " 存在空报告内容");
            }
            if (report.structuredData() == null) {
                throw new IllegalStateException(stage.stageName() + " 存在空结构化数据");
            }
        }
    }

    private String buildStageInput(FundWorkflowStage stage, FundAgentState state) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("agentName", stage.stageName());
        input.put("stageCode", stage.stageCode());
        input.put("fundCode", state.getFundCode());
        input.put("question", state.getQuestion());
        input.put("thinkingMode", state.getThinkingMode());
        input.put("dataQuality", state.getDataQuality());
        input.put("pastContextAvailable", state.getPastContext() != null && !state.getPastContext().isBlank());
        input.put("completedSections", state.getSections().stream().map(FundAgentReportSectionVO::title).toList());
        return writeJson(input);
    }

    private String buildStageOutput(FundStageResult result) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("summary", result.summary());
        output.put("reportTitles", result.reports().stream().map(FundStageReport::title).toList());
        output.put("structuredReports", result.reports().stream().map(FundStageReport::structuredData).toList());
        output.put("eventTypes", result.events().stream().map(AgentStreamEventVO::type).toList());
        return writeJson(output);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Serialize workflow audit value failed", exception);
            return "{}";
        }
    }

    private AgentTaskDO createTaskDO(String fundCode,
                                     String question,
                                     String requestKey,
                                     AgentThinkingMode thinkingMode) {
        LocalDateTime now = LocalDateTime.now();
        AgentTaskDO taskDO = new AgentTaskDO();
        taskDO.setTaskNo("FA-" + TASK_NO_TIME_FORMATTER.format(now) + "-" + UUID.randomUUID().toString().substring(0, 8));
        taskDO.setFundCode(fundCode);
        taskDO.setQuestion(question);
        taskDO.setThinkingMode(AgentThinkingMode.fromNullable(thinkingMode).name());
        taskDO.setRequestKey(requestKey);
        taskDO.setStatus(FundConstants.AGENT_TASK_STATUS_PENDING);
        taskDO.setRestricted(Boolean.FALSE);
        taskDO.setRetryCount(0);
        taskDO.setDisclaimer(ComplianceService.STANDARD_DISCLAIMER);
        taskDO.setDeadlineAt(createDeadline());
        taskDO.setCreatedAt(now);
        taskDO.setUpdatedAt(now);
        return taskDO;
    }

    private void syncTaskFromState(AgentTaskDO taskDO, FundAgentState state) {
        if (state.getComplianceResult() != null) {
            taskDO.setRestricted(state.getComplianceResult().restricted());
        }
        if (state.getFinalAnswer() != null) {
            taskDO.setFinalAnswer(state.getFinalAnswer());
            taskDO.setDisclaimer(ComplianceService.STANDARD_DISCLAIMER);
        }
        persistTaskSnapshot(taskDO, state);
    }

    private void completeTask(AgentTaskDO taskDO,
                              FundAgentState state,
                              long startNanoTime,
                              String status,
                              String errorMessage) {
        taskDO.setStatus(status);
        taskDO.setFinalAnswer(state.getFinalAnswer());
        taskDO.setErrorMessage(errorMessage);
        taskDO.setCompletedAt(LocalDateTime.now());
        taskDO.setElapsedMs(elapsedMillis(startNanoTime));
        taskDO.setUpdatedAt(LocalDateTime.now());
        if (FundConstants.AGENT_STATUS_SUCCESS.equals(status)) {
            taskDO.setNextStageCode(null);
            serializeTaskSnapshot(taskDO, state);
            int updatedRows = agentTaskMapper.update(null, new LambdaUpdateWrapper<AgentTaskDO>()
                    .eq(AgentTaskDO::getId, taskDO.getId())
                    .eq(AgentTaskDO::getStatus, FundConstants.AGENT_TASK_STATUS_RUNNING)
                    .set(AgentTaskDO::getStatus, status)
                    .set(AgentTaskDO::getFinalAnswer, taskDO.getFinalAnswer())
                    .set(AgentTaskDO::getErrorMessage, errorMessage)
                    .set(AgentTaskDO::getCompletedAt, taskDO.getCompletedAt())
                    .set(AgentTaskDO::getElapsedMs, taskDO.getElapsedMs())
                    .set(AgentTaskDO::getNextStageCode, null)
                    .set(AgentTaskDO::getRestricted, taskDO.getRestricted())
                    .set(AgentTaskDO::getDisclaimer, taskDO.getDisclaimer())
                    .set(AgentTaskDO::getStateSnapshot, taskDO.getStateSnapshot())
                    .set(AgentTaskDO::getUpdatedAt, taskDO.getUpdatedAt()));
            if (updatedRows == 0) {
                ensureTaskCanContinue(taskDO);
                throw new IllegalStateException("任务状态已发生变化，无法标记为成功");
            }
            return;
        }
        serializeTaskSnapshot(taskDO, state);
        agentTaskMapper.updateById(taskDO);
    }

    private void persistTaskSnapshot(AgentTaskDO taskDO, FundAgentState state) {
        serializeTaskSnapshot(taskDO, state);
        agentTaskMapper.update(null, new LambdaUpdateWrapper<AgentTaskDO>()
                .eq(AgentTaskDO::getId, taskDO.getId())
                .set(AgentTaskDO::getStateSnapshot, taskDO.getStateSnapshot())
                .set(AgentTaskDO::getNextStageCode, taskDO.getNextStageCode())
                .set(AgentTaskDO::getRestricted, taskDO.getRestricted())
                .set(AgentTaskDO::getFinalAnswer, taskDO.getFinalAnswer())
                .set(AgentTaskDO::getDisclaimer, taskDO.getDisclaimer())
                .set(AgentTaskDO::getUpdatedAt, taskDO.getUpdatedAt()));
    }

    private void serializeTaskSnapshot(AgentTaskDO taskDO, FundAgentState state) {
        try {
            taskDO.setStateSnapshot(objectMapper.writeValueAsString(state.toSnapshot()));
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Serialize fund agent state failed, taskId={}", taskDO.getId(), exception);
        }
        taskDO.setUpdatedAt(LocalDateTime.now());
    }

    private FundAgentTaskVO toTaskVO(AgentTaskDO taskDO, boolean includeAnalysis) {
        List<FundAgentStageVO> stages = loadStages(taskDO.getId());
        List<FundAgentReportSectionVO> sections = loadSections(taskDO.getId());
        FundAnalysisResultVO analysis = includeAnalysis ? tryAnalyze(taskDO.getFundCode()) : null;
        return buildTaskVO(taskDO, stages, sections, analysis);
    }

    private FundAgentTaskVO buildTaskVO(AgentTaskDO taskDO,
                                        List<FundAgentStageVO> stages,
                                        List<FundAgentReportSectionVO> sections,
                                        FundAnalysisResultVO analysis) {
        String reportMarkdown = analysis == null ? null : buildReportMarkdown(taskDO, stages, sections, analysis);
        return new FundAgentTaskVO(
                taskDO.getId(),
                taskDO.getTaskNo(),
                taskDO.getFundCode(),
                taskDO.getQuestion(),
                AgentThinkingMode.fromValue(taskDO.getThinkingMode()),
                taskDO.getStatus(),
                taskDO.getRestricted(),
                taskDO.getFinalAnswer(),
                taskDO.getDisclaimer(),
                taskDO.getErrorMessage(),
                taskDO.getNextStageCode(),
                taskDO.getRetryCount(),
                taskDO.getDeadlineAt(),
                reportMarkdown,
                analysis,
                stages,
                sections,
                taskDO.getStartedAt(),
                taskDO.getCompletedAt(),
                taskDO.getElapsedMs(),
                taskDO.getCreatedAt()
        );
    }

    private AgentTaskDO findTask(Long taskId) {
        AgentTaskDO taskDO = agentTaskMapper.selectById(taskId);
        if (taskDO == null) {
            throw new IllegalArgumentException("分析任务不存在: " + taskId);
        }
        return taskDO;
    }

    private List<FundAgentStageVO> loadStages(Long taskId) {
        return agentTaskStageMapper.selectList(new LambdaQueryWrapper<AgentTaskStageDO>()
                        .eq(AgentTaskStageDO::getTaskId, taskId)
                        .orderByAsc(AgentTaskStageDO::getSortOrder))
                .stream()
                .map(this::toStageVO)
                .toList();
    }

    private List<FundAgentReportSectionVO> loadSections(Long taskId) {
        return agentReportSectionMapper.selectList(new LambdaQueryWrapper<AgentReportSectionDO>()
                        .eq(AgentReportSectionDO::getTaskId, taskId)
                        .orderByAsc(AgentReportSectionDO::getSortOrder))
                .stream()
                .map(this::toSectionVO)
                .toList();
    }

    private FundAnalysisResultVO tryAnalyze(String fundCode) {
        try {
            return fundQueryService.analyze(fundCode);
        } catch (RuntimeException exception) {
            LOGGER.warn("Load analysis for task view failed, fundCode={}", fundCode, exception);
            return null;
        }
    }

    private FundAgentStageVO toStageVO(AgentTaskStageDO stageDO) {
        return new FundAgentStageVO(
                stageDO.getId(),
                stageDO.getStageCode(),
                stageDO.getStageName(),
                stageDO.getStatus(),
                stageDO.getSummary(),
                stageDO.getSortOrder(),
                stageDO.getStartedAt(),
                stageDO.getCompletedAt(),
                stageDO.getElapsedMs(),
                stageDO.getErrorMessage(),
                stageDO.getStageInput(),
                stageDO.getStageOutput()
        );
    }

    private FundAgentReportSectionVO toSectionVO(AgentReportSectionDO sectionDO) {
        return new FundAgentReportSectionVO(
                sectionDO.getId(),
                sectionDO.getStageCode(),
                sectionDO.getSectionType(),
                sectionDO.getTitle(),
                sectionDO.getContent(),
                sectionDO.getSortOrder(),
                sectionDO.getCreatedAt()
        );
    }

    private fundcopilot.agent.vo.AgentStepVO toAgentStep(FundAgentStageVO stageVO) {
        return new fundcopilot.agent.vo.AgentStepVO(
                stageVO.stageName(),
                stageVO.status(),
                Objects.toString(stageVO.summary(), "正在执行")
        );
    }

    private String loadMemoryContext(String fundCode) {
        List<AgentMemoryEntryDO> sameFundEntries = agentMemoryEntryMapper.selectList(new LambdaQueryWrapper<AgentMemoryEntryDO>()
                .eq(AgentMemoryEntryDO::getFundCode, fundCode)
                .orderByDesc(AgentMemoryEntryDO::getCreatedAt)
                .last("limit " + SAME_FUND_MEMORY_LIMIT));
        List<AgentMemoryEntryDO> crossFundEntries = agentMemoryEntryMapper.selectList(new LambdaQueryWrapper<AgentMemoryEntryDO>()
                .ne(AgentMemoryEntryDO::getFundCode, fundCode)
                .orderByDesc(AgentMemoryEntryDO::getCreatedAt)
                .last("limit " + CROSS_FUND_MEMORY_LIMIT));
        List<String> parts = new ArrayList<>();
        if (!sameFundEntries.isEmpty()) {
            parts.add("同基金历史分析：");
            sameFundEntries.stream().map(this::formatMemoryEntry).forEach(parts::add);
        }
        if (!crossFundEntries.isEmpty()) {
            parts.add("其他基金近期经验：");
            crossFundEntries.stream().map(this::formatMemoryEntry).forEach(parts::add);
        }
        return String.join("\n\n", parts);
    }

    private String formatMemoryEntry(AgentMemoryEntryDO memoryEntryDO) {
        return "- " + memoryEntryDO.getFundCode()
                + "：" + Objects.toString(memoryEntryDO.getSummary(), "暂无摘要")
                + "；风险：" + Objects.toString(memoryEntryDO.getRiskSummary(), "暂无风险摘要")
                + "；反思：" + Objects.toString(memoryEntryDO.getReflection(), "暂无反思");
    }

    private void saveMemory(FundAgentState state) {
        if (state.getAnalysis() == null) {
            return;
        }
        AgentMemoryEntryDO memoryEntryDO = new AgentMemoryEntryDO();
        memoryEntryDO.setFundCode(state.getFundCode());
        memoryEntryDO.setTaskId(state.getTaskId());
        memoryEntryDO.setQuestion(state.getQuestion());
        memoryEntryDO.setSummary("近1年收益率 " + formatPercent(state.getAnalysis().metrics().oneYearReturn())
                + "，数据日期 " + Objects.toString(state.getAnalysis().detail().latestNavDate(), "暂无")
                + "，数据质量：" + Objects.toString(state.getDataQuality(), "暂无"));
        memoryEntryDO.setRiskSummary(String.join("；", state.getRiskFactors()));
        memoryEntryDO.setReflection(buildReflection(state));
        memoryEntryDO.setCreatedAt(LocalDateTime.now());
        agentMemoryEntryMapper.insert(memoryEntryDO);
    }

    private String buildReflection(FundAgentState state) {
        if (state.getComplianceResult() != null && state.getComplianceResult().restricted()) {
            return "本次问题触发合规限制，后续同类问题应优先改写为历史表现、风险揭示和适当性提醒。";
        }
        if (state.getPastContext() != null && !state.getPastContext().isBlank()) {
            return "本次分析已参考历史记忆，后续可继续复用数据质量、回撤和波动率的表达口径。";
        }
        return "本次分析已沉淀为基础记忆，后续同基金任务可参考本次风险表达和数据来源说明。";
    }

    private void saveRunLog(AgentTaskDO taskDO,
                            List<FundAgentStageVO> stages,
                            String status,
                            long elapsedMs,
                            String errorMessage) {
        AgentRunLogDO logDO = new AgentRunLogDO();
        logDO.setAgentName(FundConstants.AGENT_NAME_FUND_ANALYSIS);
        logDO.setFundCode(taskDO.getFundCode());
        logDO.setQuestion(taskDO.getQuestion());
        logDO.setStatus(status);
        logDO.setElapsedMs(elapsedMs);
        logDO.setErrorMessage(errorMessage);
        try {
            logDO.setToolTrace(objectMapper.writeValueAsString(stages));
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Serialize agent workflow trace failed", exception);
            logDO.setToolTrace("[]");
        }
        agentRunLogMapper.insert(logDO);
    }

    private String buildReportMarkdown(AgentTaskDO taskDO,
                                       List<FundAgentStageVO> stages,
                                       List<FundAgentReportSectionVO> sections,
                                       FundAnalysisResultVO analysis) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Fund Copilot 基金分析报告\n\n");
        builder.append("- 任务编号：").append(taskDO.getTaskNo()).append('\n');
        builder.append("- 基金：").append(analysis.detail().fundName())
                .append("（").append(taskDO.getFundCode()).append("）\n");
        builder.append("- 问题：").append(Objects.toString(taskDO.getQuestion(), "暂无")).append('\n');
        builder.append("- 思考模式：")
                .append(AgentThinkingMode.fromValue(taskDO.getThinkingMode()).getDisplayName())
                .append('\n');
        builder.append("- 状态：").append(taskDO.getStatus()).append('\n');
        builder.append("- 重试次数：").append(Objects.requireNonNullElse(taskDO.getRetryCount(), 0)).append('\n');
        builder.append("- 数据来源：").append(analysis.dataSource()).append('\n');
        builder.append("- 免责声明：").append(Objects.toString(taskDO.getDisclaimer(), ComplianceService.STANDARD_DISCLAIMER)).append("\n\n");

        builder.append("## 执行阶段\n\n");
        for (FundAgentStageVO stageVO : stages) {
            builder.append("- ").append(stageVO.sortOrder()).append(". ")
                    .append(stageVO.stageName()).append("：")
                    .append(stageVO.status()).append("，")
                    .append(Objects.toString(stageVO.summary(), Objects.toString(stageVO.errorMessage(), "暂无摘要")))
                    .append('\n');
        }

        builder.append("\n## 结构化报告\n\n");
        for (FundAgentReportSectionVO sectionVO : sections) {
            builder.append("### ").append(sectionVO.title()).append('\n')
                    .append(sectionVO.content()).append("\n\n");
        }

        builder.append("## 最终回答\n\n");
        builder.append(Objects.toString(taskDO.getFinalAnswer(), "暂无最终回答")).append("\n\n");
        return builder.toString();
    }

    private AgentTaskDO findActiveTask(String requestKey) {
        return agentTaskMapper.selectOne(new LambdaQueryWrapper<AgentTaskDO>()
                .eq(AgentTaskDO::getRequestKey, requestKey)
                .in(AgentTaskDO::getStatus,
                        FundConstants.AGENT_TASK_STATUS_PENDING,
                        FundConstants.AGENT_TASK_STATUS_RUNNING)
                .orderByDesc(AgentTaskDO::getCreatedAt)
                .last("limit 1"));
    }

    private String buildRerunStateSnapshot(AgentTaskDO taskDO, List<String> rerunStageCodes) {
        FundAgentState state = restoreStateSnapshot(taskDO);
        state.setFinalAnswer(null);
        rerunStageCodes.forEach(state.getStructuredReports()::remove);
        try {
            return objectMapper.writeValueAsString(state.toSnapshot());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("重建阶段状态快照失败", exception);
        }
    }

    private String buildRequestKey(FundAnalysisRequestDTO requestDTO, String fundCode, String question) {
        String source = fundCode + "|" + question + "|"
                + Boolean.TRUE.equals(requestDTO.includeHistory()) + "|"
                + Boolean.TRUE.equals(requestDTO.includeRiskNotice()) + "|"
                + requestDTO.normalizedThinkingMode().name();
        return DigestUtils.md5DigestAsHex(source.getBytes(StandardCharsets.UTF_8));
    }

    private LocalDateTime createDeadline() {
        return LocalDateTime.now().plusSeconds(Math.max(1, agentProperties.getTaskTimeoutSeconds()));
    }

    private void ensureTaskCanContinue(AgentTaskDO taskDO) {
        AgentTaskDO latestTask = findTask(taskDO.getId());
        if (FundConstants.AGENT_STATUS_CANCELLED.equals(latestTask.getStatus())) {
            throw new AgentTaskCancelledException("任务已由用户取消");
        }
        if (FundConstants.AGENT_STATUS_TIMEOUT.equals(latestTask.getStatus())) {
            throw new AgentTaskTimeoutException("任务已超时");
        }
        taskDO.setDeadlineAt(latestTask.getDeadlineAt());
        if (latestTask.getDeadlineAt() != null && LocalDateTime.now().isAfter(latestTask.getDeadlineAt())) {
            throw new AgentTaskTimeoutException("任务执行超时：超过 " + agentProperties.getTaskTimeoutSeconds() + " 秒");
        }
    }

    private FundAgentTaskVO emitTerminalTaskIfNecessary(AgentTaskDO taskDO,
                                                         Consumer<AgentStreamEventVO> eventConsumer) {
        String eventType = switch (taskDO.getStatus()) {
            case FundConstants.AGENT_STATUS_CANCELLED -> FundConstants.SSE_TASK_CANCELLED;
            case FundConstants.AGENT_STATUS_TIMEOUT -> FundConstants.SSE_TASK_TIMEOUT;
            case FundConstants.AGENT_STATUS_SUCCESS -> FundConstants.SSE_DONE;
            default -> null;
        };
        if (eventType == null) {
            return null;
        }
        FundAgentTaskVO taskVO = getTask(taskDO.getId());
        if (!FundConstants.SSE_DONE.equals(eventType)) {
            emit(eventConsumer, eventType, taskVO);
        }
        emit(eventConsumer, FundConstants.SSE_DONE, taskVO);
        return taskVO;
    }

    private boolean isActiveStatus(String status) {
        return FundConstants.AGENT_TASK_STATUS_PENDING.equals(status)
                || FundConstants.AGENT_TASK_STATUS_RUNNING.equals(status);
    }

    private long calculateElapsedMillis(AgentTaskDO taskDO, LocalDateTime completedAt) {
        LocalDateTime startTime = taskDO.getStartedAt() == null ? taskDO.getCreatedAt() : taskDO.getStartedAt();
        return startTime == null ? 0L : Math.max(0L, Duration.between(startTime, completedAt).toMillis());
    }

    private String normalizeQuestion(String question) {
        return question == null || question.isBlank() ? DEFAULT_QUESTION : question.trim();
    }

    private long elapsedMillis(long startNanoTime) {
        return Duration.ofNanos(System.nanoTime() - startNanoTime).toMillis();
    }

    private String formatPercent(java.math.BigDecimal value) {
        return value == null ? "暂无" : value.stripTrailingZeros().toPlainString() + "%";
    }

    private void emit(Consumer<AgentStreamEventVO> eventConsumer, String type, Object payload) {
        if (eventConsumer != null) {
            eventConsumer.accept(new AgentStreamEventVO(type, payload));
        }
    }
}
