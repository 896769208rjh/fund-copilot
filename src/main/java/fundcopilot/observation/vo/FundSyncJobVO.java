package fundcopilot.observation.vo;

import java.time.LocalDateTime;

public record FundSyncJobVO(
        Long jobId,
        String jobType,
        String triggerType,
        String status,
        int totalCount,
        int successCount,
        int failedCount,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String errorMessage
) {
}
