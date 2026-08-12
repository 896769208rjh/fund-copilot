package fundcopilot.observation.controller;

import fundcopilot.common.ApiResponse;
import fundcopilot.observation.service.ObservationSyncService;
import fundcopilot.observation.vo.FundSyncJobVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/observations")
@ConditionalOnProperty(prefix = "fund-copilot.observation", name = "admin-endpoints-enabled",
        havingValue = "true")
public class ObservationAdminController {
    private final ObservationSyncService syncService;

    public ObservationAdminController(ObservationSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/sync")
    public ApiResponse<FundSyncJobVO> sync() {
        return ApiResponse.ok(syncService.startFullSync("MANUAL"));
    }

    @PostMapping("/rank")
    public ApiResponse<FundSyncJobVO> rank() {
        return ApiResponse.ok(syncService.startRanking("MANUAL"));
    }

    @GetMapping("/sync-jobs/latest")
    public ApiResponse<FundSyncJobVO> latestJob() {
        return ApiResponse.ok(syncService.latestJobVO());
    }
}
