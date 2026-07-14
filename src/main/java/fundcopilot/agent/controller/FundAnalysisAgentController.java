package fundcopilot.agent.controller;

import fundcopilot.agent.dto.FundAnalysisRequestDTO;
import fundcopilot.agent.service.AgentTaskExecutionService;
import fundcopilot.agent.service.AgentModelCallService;
import fundcopilot.agent.service.FundAnalysisAgentService;
import fundcopilot.agent.service.FundAnalysisWorkflowService;
import fundcopilot.agent.vo.AgentAnalysisResponseVO;
import fundcopilot.agent.vo.AgentModelCallVO;
import fundcopilot.agent.vo.AgentStreamEventVO;
import fundcopilot.agent.vo.FundAgentTaskVO;
import fundcopilot.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
public class FundAnalysisAgentController {
    private final FundAnalysisAgentService fundAnalysisAgentService;
    private final FundAnalysisWorkflowService fundAnalysisWorkflowService;
    private final AgentTaskExecutionService agentTaskExecutionService;
    private final AgentModelCallService agentModelCallService;

    public FundAnalysisAgentController(FundAnalysisAgentService fundAnalysisAgentService,
                                       FundAnalysisWorkflowService fundAnalysisWorkflowService,
                                       AgentTaskExecutionService agentTaskExecutionService,
                                       AgentModelCallService agentModelCallService) {
        this.fundAnalysisAgentService = fundAnalysisAgentService;
        this.fundAnalysisWorkflowService = fundAnalysisWorkflowService;
        this.agentTaskExecutionService = agentTaskExecutionService;
        this.agentModelCallService = agentModelCallService;
    }

    @PostMapping("/fund-analysis")
    public ApiResponse<AgentAnalysisResponseVO> analyze(@Valid @RequestBody FundAnalysisRequestDTO requestDTO) {
        return ApiResponse.ok(fundAnalysisAgentService.analyze(requestDTO));
    }

    @PostMapping(path = "/fund-analysis/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentStreamEventVO>> streamAnalyze(@Valid @RequestBody FundAnalysisRequestDTO requestDTO) {
        return agentTaskExecutionService.createAndStream(requestDTO).map(this::toServerSentEvent);
    }

    @PostMapping("/fund-analysis/tasks")
    public ApiResponse<FundAgentTaskVO> createTask(@Valid @RequestBody FundAnalysisRequestDTO requestDTO) {
        return ApiResponse.ok(agentTaskExecutionService.createAndSubmit(requestDTO));
    }

    @GetMapping("/fund-analysis/tasks/{taskId}")
    public ApiResponse<FundAgentTaskVO> getTask(@PathVariable Long taskId) {
        return ApiResponse.ok(fundAnalysisWorkflowService.getTask(taskId));
    }

    @GetMapping("/fund-analysis/tasks/{taskId}/model-calls")
    public ApiResponse<List<AgentModelCallVO>> listModelCalls(@PathVariable Long taskId) {
        fundAnalysisWorkflowService.getTask(taskId);
        return ApiResponse.ok(agentModelCallService.listByTaskId(taskId));
    }

    @GetMapping(path = "/fund-analysis/tasks/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentStreamEventVO>> streamTask(@PathVariable Long taskId) {
        return agentTaskExecutionService.streamTask(taskId).map(this::toServerSentEvent);
    }

    @PostMapping("/fund-analysis/tasks/{taskId}/resume")
    public ApiResponse<FundAgentTaskVO> resumeTask(@PathVariable Long taskId) {
        return ApiResponse.ok(agentTaskExecutionService.resumeAndSubmit(taskId));
    }

    @PostMapping(path = "/fund-analysis/tasks/{taskId}/resume/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentStreamEventVO>> streamResumeTask(@PathVariable Long taskId) {
        return agentTaskExecutionService.resumeAndStream(taskId).map(this::toServerSentEvent);
    }

    @PostMapping("/fund-analysis/tasks/{taskId}/cancel")
    public ApiResponse<FundAgentTaskVO> cancelTask(@PathVariable Long taskId) {
        return ApiResponse.ok(agentTaskExecutionService.cancelTask(taskId));
    }

    @PostMapping("/fund-analysis/tasks/{taskId}/stages/{stageCode}/rerun")
    public ApiResponse<FundAgentTaskVO> rerunStage(@PathVariable Long taskId,
                                                   @PathVariable String stageCode) {
        return ApiResponse.ok(agentTaskExecutionService.rerunStage(taskId, stageCode));
    }

    @GetMapping(path = "/fund-analysis/tasks/{taskId}/report", produces = "text/markdown;charset=UTF-8")
    public String exportTaskReport(@PathVariable Long taskId) {
        return fundAnalysisWorkflowService.exportTaskReport(taskId);
    }

    @GetMapping("/fund-analysis/tasks")
    public ApiResponse<List<FundAgentTaskVO>> listTasks(@RequestParam(required = false) String fundCode) {
        return ApiResponse.ok(fundAnalysisWorkflowService.listTasks(fundCode));
    }

    private ServerSentEvent<AgentStreamEventVO> toServerSentEvent(AgentStreamEventVO eventVO) {
        return ServerSentEvent.<AgentStreamEventVO>builder()
                .event(eventVO.type())
                .data(eventVO)
                .build();
    }
}
