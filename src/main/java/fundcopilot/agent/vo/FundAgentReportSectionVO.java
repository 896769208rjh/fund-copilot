package fundcopilot.agent.vo;

import java.time.LocalDateTime;

public record FundAgentReportSectionVO(
        Long id,
        String stageCode,
        String sectionType,
        String title,
        String content,
        Integer sortOrder,
        LocalDateTime createdAt
) {
}
