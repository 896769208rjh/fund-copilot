package fundcopilot.observation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fundcopilot.fund.service.FundQueryService;
import fundcopilot.observation.config.ObservationExecutorConfig;
import fundcopilot.observation.config.ObservationProperties;
import fundcopilot.observation.entity.FundMasterDO;
import fundcopilot.observation.entity.FundSyncJobDO;
import fundcopilot.observation.entity.FundUniverseDO;
import fundcopilot.observation.mapper.FundSyncJobMapper;
import fundcopilot.observation.model.FundCategory;
import fundcopilot.observation.vo.FundSyncJobVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

@Service
public class ObservationSyncService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ObservationSyncService.class);

    private final FundUniverseService universeService;
    private final FundQueryService fundQueryService;
    private final DeterministicRankingService rankingService;
    private final StableRankingService stableRankingService;
    private final ObservationBoardService boardService;
    private final FundSyncJobMapper jobMapper;
    private final ObservationProperties properties;
    private final Executor executor;

    public ObservationSyncService(FundUniverseService universeService,
                                  FundQueryService fundQueryService,
                                  DeterministicRankingService rankingService,
                                  StableRankingService stableRankingService,
                                  ObservationBoardService boardService,
                                  FundSyncJobMapper jobMapper,
                                  ObservationProperties properties,
                                  @Qualifier(ObservationExecutorConfig.OBSERVATION_SYNC_EXECUTOR) Executor executor) {
        this.universeService = universeService;
        this.fundQueryService = fundQueryService;
        this.rankingService = rankingService;
        this.stableRankingService = stableRankingService;
        this.boardService = boardService;
        this.jobMapper = jobMapper;
        this.properties = properties;
        this.executor = executor;
    }

    public synchronized FundSyncJobVO startFullSync(String triggerType) {
        FundSyncJobDO running = findRunningJob();
        if (running != null) {
            return toVO(running);
        }
        FundSyncJobDO job = createJob("FULL_SYNC", triggerType);
        try {
            executor.execute(() -> executeFullSync(job.getId()));
        } catch (RuntimeException exception) {
            failJob(job.getId(), exception);
            throw exception;
        }
        return toVO(job);
    }

    public FundSyncJobVO runRetryIfNeeded(String triggerType) {
        FundSyncJobDO latest = latestFullSyncJob();
        if (latest != null && "SUCCESS".equals(latest.getStatus())
                && latest.getCompletedAt() != null
                && latest.getCompletedAt().toLocalDate().equals(LocalDate.now())) {
            return toVO(latest);
        }
        return startFullSync(triggerType);
    }

    public synchronized FundSyncJobVO startRanking(String triggerType) {
        FundSyncJobDO running = findRunningJob();
        if (running != null) {
            return toVO(running);
        }
        FundSyncJobDO job = createJob("RANKING", triggerType);
        try {
            executor.execute(() -> executeRanking(job.getId()));
        } catch (RuntimeException exception) {
            failJob(job.getId(), exception);
            throw exception;
        }
        return toVO(job);
    }

    public FundSyncJobVO latestJobVO() {
        FundSyncJobDO latest = latestJob();
        return latest == null ? null : toVO(latest);
    }

    private void executeFullSync(Long jobId) {
        FundSyncJobDO job = jobMapper.selectById(jobId);
        try {
            FundUniverseService.UniverseRefreshResult refreshResult = universeService.refreshAllCategories();
            List<FundUniverseDO> universe = universeService.listActiveUniverse();
            Map<Long, FundMasterDO> masters = universeService.mastersById(universe);
            job.setTotalCount(universe.size());
            jobMapper.updateById(job);

            int successCount = 0;
            int failedCount = 0;
            StringBuilder errors = new StringBuilder();
            for (FundUniverseDO member : universe) {
                FundMasterDO master = masters.get(member.getMasterId());
                if (master == null) {
                    failedCount++;
                    continue;
                }
                try {
                    fundQueryService.syncFundIncrementally(master.getPrimaryFundCode(), properties.getSyncHistorySize());
                    successCount++;
                } catch (Exception exception) {
                    failedCount++;
                    appendError(errors, master.getPrimaryFundCode(), exception);
                    LOGGER.warn("Observation fund sync failed, fundCode={}", master.getPrimaryFundCode(), exception);
                }
                throttle();
            }
            job.setSuccessCount(successCount);
            job.setFailedCount(failedCount);
            job.setErrorMessage(errors.isEmpty() ? null : errors.toString());
            if (successCount > 0) {
                LocalDate rankDate = LocalDate.now();
                var metrics = rankingService.calculate(rankDate);
                stableRankingService.publish(rankDate, metrics);
                boardService.evictCache();
            }
            job.setStatus(successCount == 0 && refreshResult.activeCount() > 0 ? "FAILED"
                    : failedCount > 0 || refreshResult.failedCategories() > 0
                    ? "PARTIAL_SUCCESS" : "SUCCESS");
            job.setCompletedAt(LocalDateTime.now());
            jobMapper.updateById(job);
        } catch (Exception exception) {
            failJob(jobId, exception);
        }
    }

    private void executeRanking(Long jobId) {
        try {
            LocalDate rankDate = LocalDate.now();
            Map<FundCategory, List<fundcopilot.observation.entity.FundMetricDailyDO>> metrics =
                    rankingService.calculate(rankDate);
            stableRankingService.publish(rankDate, metrics);
            boardService.evictCache();
            FundSyncJobDO job = jobMapper.selectById(jobId);
            job.setTotalCount(metrics.values().stream().mapToInt(List::size).sum());
            job.setSuccessCount(job.getTotalCount());
            job.setFailedCount(0);
            job.setStatus("SUCCESS");
            job.setCompletedAt(LocalDateTime.now());
            jobMapper.updateById(job);
        } catch (Exception exception) {
            failJob(jobId, exception);
        }
    }

    private FundSyncJobDO createJob(String jobType, String triggerType) {
        FundSyncJobDO job = new FundSyncJobDO();
        job.setJobType(jobType);
        job.setTriggerType(triggerType);
        job.setStatus("RUNNING");
        job.setTotalCount(0);
        job.setSuccessCount(0);
        job.setFailedCount(0);
        job.setStartedAt(LocalDateTime.now());
        try {
            jobMapper.insert(job);
            return job;
        } catch (DuplicateKeyException exception) {
            FundSyncJobDO running = findRunningJob();
            if (running != null) {
                return running;
            }
            throw exception;
        }
    }

    private FundSyncJobDO findRunningJob() {
        return jobMapper.selectOne(new LambdaQueryWrapper<FundSyncJobDO>()
                .eq(FundSyncJobDO::getStatus, "RUNNING")
                .orderByDesc(FundSyncJobDO::getStartedAt)
                .last("limit 1"));
    }

    private FundSyncJobDO latestJob() {
        return jobMapper.selectOne(new LambdaQueryWrapper<FundSyncJobDO>()
                .orderByDesc(FundSyncJobDO::getStartedAt)
                .last("limit 1"));
    }

    private FundSyncJobDO latestFullSyncJob() {
        return jobMapper.selectOne(new LambdaQueryWrapper<FundSyncJobDO>()
                .eq(FundSyncJobDO::getJobType, "FULL_SYNC")
                .orderByDesc(FundSyncJobDO::getStartedAt)
                .last("limit 1"));
    }

    private void failJob(Long jobId, Exception exception) {
        LOGGER.error("Observation job failed, jobId={}", jobId, exception);
        FundSyncJobDO job = jobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        job.setStatus("FAILED");
        job.setCompletedAt(LocalDateTime.now());
        job.setErrorMessage(truncate(Objects.toString(exception.getMessage(), exception.toString()), 2000));
        jobMapper.updateById(job);
    }

    private void appendError(StringBuilder errors, String fundCode, Exception exception) {
        if (errors.length() >= 1800) {
            return;
        }
        if (!errors.isEmpty()) {
            errors.append("; ");
        }
        errors.append(fundCode).append(':').append(Objects.toString(exception.getMessage(), "同步失败"));
    }

    private void throttle() {
        try {
            Thread.sleep(Math.max(0, properties.getSyncIntervalMs()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("基金池同步被中断", exception);
        }
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private FundSyncJobVO toVO(FundSyncJobDO job) {
        return new FundSyncJobVO(job.getId(), job.getJobType(), job.getTriggerType(), job.getStatus(),
                Objects.requireNonNullElse(job.getTotalCount(), 0),
                Objects.requireNonNullElse(job.getSuccessCount(), 0),
                Objects.requireNonNullElse(job.getFailedCount(), 0), job.getStartedAt(),
                job.getCompletedAt(), job.getErrorMessage());
    }
}
