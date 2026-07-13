package fundcopilot.agent.vo;

import fundcopilot.fund.vo.FundAnalysisResultVO;

import java.time.LocalDateTime;
import java.util.List;

public record FundAgentTaskVO(
        Long taskId,
        String taskNo,
        String fundCode,
        String question,
        String status,
        Boolean restricted,
        String finalAnswer,
        String disclaimer,
        String errorMessage,
        String nextStageCode,
        Integer retryCount,
        LocalDateTime deadlineAt,
        String reportMarkdown,
        FundAnalysisResultVO analysis,
        List<FundAgentStageVO> stages,
        List<FundAgentReportSectionVO> sections,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        Long elapsedMs,
        LocalDateTime createdAt
) {
}
