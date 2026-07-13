package fundcopilot.agent.service;

import fundcopilot.agent.config.AgentTaskExecutorConfig;
import fundcopilot.agent.dto.FundAnalysisRequestDTO;
import fundcopilot.agent.vo.AgentStreamEventVO;
import fundcopilot.agent.vo.FundAgentTaskVO;
import fundcopilot.fund.constant.FundConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Service
public class AgentTaskExecutionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentTaskExecutionService.class);

    private final FundAnalysisWorkflowService workflowService;
    private final AgentTaskEventService eventService;
    private final Executor taskExecutor;
    private final Set<Long> runningTaskIds = ConcurrentHashMap.newKeySet();

    public AgentTaskExecutionService(FundAnalysisWorkflowService workflowService,
                                     AgentTaskEventService eventService,
                                     @Qualifier(AgentTaskExecutorConfig.FUND_AGENT_TASK_EXECUTOR) Executor taskExecutor) {
        this.workflowService = workflowService;
        this.eventService = eventService;
        this.taskExecutor = taskExecutor;
    }

    public FundAgentTaskVO createAndSubmit(FundAnalysisRequestDTO requestDTO) {
        FundAgentTaskVO taskVO = workflowService.initializeTask(requestDTO);
        eventService.openStream(taskVO.taskId());
        submitInitializedTask(taskVO);
        return taskVO;
    }

    public Flux<AgentStreamEventVO> createAndStream(FundAnalysisRequestDTO requestDTO) {
        FundAgentTaskVO taskVO = workflowService.initializeTask(requestDTO);
        Flux<AgentStreamEventVO> eventFlux = eventService.openStream(taskVO.taskId());
        submitInitializedTask(taskVO);
        return eventFlux;
    }

    public FundAgentTaskVO resumeAndSubmit(Long taskId) {
        FundAgentTaskVO taskVO = workflowService.getTask(taskId);
        if (FundConstants.AGENT_STATUS_SUCCESS.equals(taskVO.status())) {
            return taskVO;
        }
        eventService.openStream(taskId);
        submit(taskId, ExecutionMode.RESUME);
        return taskVO;
    }

    public Flux<AgentStreamEventVO> resumeAndStream(Long taskId) {
        FundAgentTaskVO taskVO = workflowService.getTask(taskId);
        if (FundConstants.AGENT_STATUS_SUCCESS.equals(taskVO.status())) {
            return Flux.fromIterable(workflowService.replayTaskEvents(taskId));
        }
        Flux<AgentStreamEventVO> eventFlux = eventService.openStream(taskId);
        submit(taskId, ExecutionMode.RESUME);
        return eventFlux;
    }

    public Flux<AgentStreamEventVO> streamTask(Long taskId) {
        return eventService.findActiveStream(taskId).orElseGet(() -> {
            List<AgentStreamEventVO> persistedEvents = eventService.replayEvents(taskId);
            return persistedEvents.isEmpty()
                    ? Flux.fromIterable(workflowService.replayTaskEvents(taskId))
                    : Flux.fromIterable(persistedEvents);
        });
    }

    public FundAgentTaskVO cancelTask(Long taskId) {
        FundAgentTaskVO taskVO = workflowService.cancelTask(taskId);
        if (!FundConstants.AGENT_STATUS_CANCELLED.equals(taskVO.status())) {
            return taskVO;
        }
        eventService.publish(taskId, new AgentStreamEventVO(FundConstants.SSE_TASK_CANCELLED, taskVO));
        if (!runningTaskIds.contains(taskId)) {
            eventService.publish(taskId, new AgentStreamEventVO(FundConstants.SSE_DONE, taskVO));
            eventService.complete(taskId);
        }
        return taskVO;
    }

    public FundAgentTaskVO rerunStage(Long taskId, String stageCode) {
        FundAgentTaskVO taskVO = workflowService.prepareStageRerun(taskId, stageCode);
        eventService.openStream(taskId);
        eventService.publish(taskId, new AgentStreamEventVO(FundConstants.SSE_TASK_RERUN_STARTED, taskVO));
        submit(taskId, ExecutionMode.RESUME);
        return taskVO;
    }

    public void recoverUnfinishedTasks() {
        for (FundAgentTaskVO taskVO : workflowService.listRecoverableTasks()) {
            eventService.openStream(taskVO.taskId());
            ExecutionMode executionMode = FundConstants.AGENT_TASK_STATUS_PENDING.equals(taskVO.status())
                    ? ExecutionMode.CREATE
                    : ExecutionMode.RECOVER;
            submit(taskVO.taskId(), executionMode);
        }
    }

    private void submitInitializedTask(FundAgentTaskVO taskVO) {
        ExecutionMode executionMode = FundConstants.AGENT_TASK_STATUS_RUNNING.equals(taskVO.status())
                ? ExecutionMode.RECOVER
                : ExecutionMode.CREATE;
        submit(taskVO.taskId(), executionMode);
    }

    private void submit(Long taskId, ExecutionMode executionMode) {
        if (!runningTaskIds.add(taskId)) {
            return;
        }
        try {
            taskExecutor.execute(() -> execute(taskId, executionMode));
        } catch (RejectedExecutionException exception) {
            runningTaskIds.remove(taskId);
            handleExecutionFailure(taskId, "Agent 任务队列已满，请稍后重试", exception);
        }
    }

    private void execute(Long taskId, ExecutionMode executionMode) {
        try {
            switch (executionMode) {
                case CREATE -> workflowService.executeTask(taskId, event -> eventService.publish(taskId, event));
                case RESUME -> workflowService.resumeTask(taskId, event -> eventService.publish(taskId, event));
                case RECOVER -> workflowService.recoverTask(taskId, event -> eventService.publish(taskId, event));
            }
        } catch (RuntimeException exception) {
            handleExecutionFailure(taskId, "Agent 任务执行失败", exception);
        } finally {
            runningTaskIds.remove(taskId);
            eventService.complete(taskId);
        }
    }

    private void handleExecutionFailure(Long taskId, String message, RuntimeException exception) {
        LOGGER.error("Agent task execution failed, taskId={}", taskId, exception);
        FundAgentTaskVO failedTask = workflowService.failTask(taskId, message);
        List<AgentStreamEventVO> events = List.of(
                new AgentStreamEventVO(FundConstants.SSE_ERROR, message),
                new AgentStreamEventVO(FundConstants.SSE_DONE, failedTask)
        );
        events.forEach(event -> eventService.publish(taskId, event));
        eventService.complete(taskId);
    }

    private enum ExecutionMode {
        CREATE,
        RESUME,
        RECOVER
    }
}
