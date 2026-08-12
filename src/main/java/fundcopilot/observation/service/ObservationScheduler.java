package fundcopilot.observation.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ObservationScheduler {
    private final ObservationSyncService syncService;

    public ObservationScheduler(ObservationSyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(cron = "${fund-copilot.observation.schedule.primary-sync:0 0 20 * * *}", zone = "Asia/Shanghai")
    public void primarySync() {
        syncService.startFullSync("SCHEDULED_20_00");
    }

    @Scheduled(cron = "${fund-copilot.observation.schedule.retry-sync:0 30 22 * * *}", zone = "Asia/Shanghai")
    public void retrySync() {
        syncService.runRetryIfNeeded("SCHEDULED_22_30");
    }

    @Scheduled(cron = "${fund-copilot.observation.schedule.compensation-sync:0 0 8 * * *}", zone = "Asia/Shanghai")
    public void compensationSync() {
        syncService.runRetryIfNeeded("SCHEDULED_08_00");
    }

    @Scheduled(cron = "${fund-copilot.observation.schedule.ranking:0 30 8 * * *}", zone = "Asia/Shanghai")
    public void calculateRanking() {
        syncService.startRanking("SCHEDULED_08_30");
    }
}
