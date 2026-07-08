package fundcopilot.agent.controller;

import fundcopilot.agent.dto.FundAnalysisRequestDTO;
import fundcopilot.agent.service.FundAnalysisAgentService;
import fundcopilot.agent.vo.AgentAnalysisResponseVO;
import fundcopilot.agent.vo.AgentStreamEventVO;
import fundcopilot.common.ApiResponse;
import fundcopilot.fund.constant.FundConstants;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/agents")
public class FundAnalysisAgentController {
    private final FundAnalysisAgentService fundAnalysisAgentService;

    public FundAnalysisAgentController(FundAnalysisAgentService fundAnalysisAgentService) {
        this.fundAnalysisAgentService = fundAnalysisAgentService;
    }

    @PostMapping("/fund-analysis")
    public ApiResponse<AgentAnalysisResponseVO> analyze(@Valid @RequestBody FundAnalysisRequestDTO requestDTO) {
        return ApiResponse.ok(fundAnalysisAgentService.analyze(requestDTO));
    }

    @PostMapping(path = "/fund-analysis/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentStreamEventVO>> streamAnalyze(@Valid @RequestBody FundAnalysisRequestDTO requestDTO) {
        return Flux.defer(() -> {
                    AgentAnalysisResponseVO responseVO = fundAnalysisAgentService.analyze(requestDTO);
                    List<AgentStreamEventVO> events = new ArrayList<>();
                    events.add(new AgentStreamEventVO(FundConstants.SSE_PROGRESS, "开始基金分析"));
                    responseVO.steps().forEach(step -> events.add(new AgentStreamEventVO(FundConstants.SSE_AGENT_STEP, step)));
                    events.add(new AgentStreamEventVO(FundConstants.SSE_CARD, responseVO.analysis()));
                    events.add(new AgentStreamEventVO(FundConstants.SSE_TOKEN, responseVO.answer()));
                    events.add(new AgentStreamEventVO(FundConstants.SSE_DONE, responseVO.generatedAt()));
                    return Flux.fromIterable(events);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(event -> ServerSentEvent.<AgentStreamEventVO>builder()
                        .event(event.type())
                        .data(event)
                        .build());
    }
}
