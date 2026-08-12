package fundcopilot.observation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ObservationJobRecoveryRunner implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(ObservationJobRecoveryRunner.class);

    private final ObservationSyncService syncService;

    public ObservationJobRecoveryRunner(ObservationSyncService syncService) {
        this.syncService = syncService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int recoveredCount = syncService.recoverStaleRunningJobs();
        if (recoveredCount > 0) {
            LOGGER.warn("Recovered {} stale observation jobs at startup", recoveredCount);
        }
    }
}
