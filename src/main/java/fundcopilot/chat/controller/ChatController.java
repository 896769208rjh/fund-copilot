package fundcopilot.chat.controller;

import fundcopilot.agent.dto.FundAnalysisRequestDTO;
import fundcopilot.agent.service.FundAnalysisAgentService;
import fundcopilot.agent.vo.AgentAnalysisResponseVO;
import fundcopilot.agent.vo.AgentStreamEventVO;
import fundcopilot.chat.dto.ChatRequestDTO;
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
@RequestMapping("/api/chat")
public class ChatController {
    private static final String DEFAULT_FUND_CODE = "000001";

    private final FundAnalysisAgentService fundAnalysisAgentService;

    public ChatController(FundAnalysisAgentService fundAnalysisAgentService) {
        this.fundAnalysisAgentService = fundAnalysisAgentService;
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentStreamEventVO>> stream(@Valid @RequestBody ChatRequestDTO requestDTO) {
        String fundCode = requestDTO.fundCodes() == null || requestDTO.fundCodes().isEmpty()
                ? DEFAULT_FUND_CODE
                : requestDTO.fundCodes().get(0);
        FundAnalysisRequestDTO analysisRequestDTO = new FundAnalysisRequestDTO(
                fundCode,
                requestDTO.message(),
                Boolean.TRUE,
                Boolean.TRUE
        );

        return Flux.defer(() -> {
                    AgentAnalysisResponseVO responseVO = fundAnalysisAgentService.analyze(analysisRequestDTO);
                    List<AgentStreamEventVO> events = new ArrayList<>();
                    events.add(new AgentStreamEventVO(FundConstants.SSE_PROGRESS, "统一聊天入口已路由到基金分析 Agent"));
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
