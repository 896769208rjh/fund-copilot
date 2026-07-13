package fundcopilot.agent.workflow;

import fundcopilot.agent.vo.AgentStreamEventVO;

import java.util.ArrayList;
import java.util.List;

public record FundStageResult(
        String summary,
        List<FundStageReport> reports,
        List<AgentStreamEventVO> events
) {
    public static FundStageResult of(String summary, List<FundStageReport> reports) {
        return new FundStageResult(summary, reports, List.of());
    }

    public FundStageResult withEvent(AgentStreamEventVO eventVO) {
        List<AgentStreamEventVO> copiedEvents = new ArrayList<>(events);
        copiedEvents.add(eventVO);
        return new FundStageResult(summary, reports, copiedEvents);
    }
}
