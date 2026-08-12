package fundcopilot.observation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fundcopilot.fund.entity.FundMetricSnapshotDO;
import fundcopilot.fund.mapper.FundMetricSnapshotMapper;
import fundcopilot.fund.mapper.FundNavMapper;
import fundcopilot.fund.entity.FundNavDO;
import fundcopilot.observation.config.ObservationProperties;
import fundcopilot.observation.entity.FundMasterDO;
import fundcopilot.observation.entity.FundMetricDailyDO;
import fundcopilot.observation.entity.FundUniverseDO;
import fundcopilot.observation.entity.FundRankMembershipDO;
import fundcopilot.observation.mapper.FundMetricDailyMapper;
import fundcopilot.observation.mapper.FundMasterMapper;
import fundcopilot.observation.mapper.FundRankMembershipMapper;
import fundcopilot.observation.model.FundCategory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DeterministicRankingService {
    private static final int MINIMUM_SAMPLE_SIZE = 133;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final FundMetricSnapshotMapper metricSnapshotMapper;
    private final FundMetricDailyMapper metricDailyMapper;
    private final FundNavMapper fundNavMapper;
    private final FundMasterMapper masterMapper;
    private final FundRankMembershipMapper membershipMapper;
    private final FundUniverseService universeService;
    private final ObservationProperties properties;

    public DeterministicRankingService(FundMetricSnapshotMapper metricSnapshotMapper,
                                       FundMetricDailyMapper metricDailyMapper,
                                       FundNavMapper fundNavMapper,
                                       FundMasterMapper masterMapper,
                                       FundRankMembershipMapper membershipMapper,
                                       FundUniverseService universeService,
                                       ObservationProperties properties) {
        this.metricSnapshotMapper = metricSnapshotMapper;
        this.metricDailyMapper = metricDailyMapper;
        this.fundNavMapper = fundNavMapper;
        this.masterMapper = masterMapper;
        this.membershipMapper = membershipMapper;
        this.universeService = universeService;
        this.properties = properties;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<FundCategory, List<FundMetricDailyDO>> calculate(LocalDate calculationDate) {
        List<FundUniverseDO> universe = universeService.listActiveUniverse();
        List<FundRankMembershipDO> incumbents = membershipMapper.selectList(
                new LambdaQueryWrapper<FundRankMembershipDO>()
                        .eq(FundRankMembershipDO::getActive, Boolean.TRUE));
        List<Long> masterIds = java.util.stream.Stream.concat(
                        universe.stream().map(FundUniverseDO::getMasterId),
                        incumbents.stream().map(FundRankMembershipDO::getMasterId))
                .distinct().toList();
        Map<Long, FundMasterDO> masters = masterIds.isEmpty() ? Map.of()
                : masterMapper.selectBatchIds(masterIds).stream()
                .collect(Collectors.toMap(FundMasterDO::getId, Function.identity()));
        List<String> codes = masters.values().stream().map(FundMasterDO::getPrimaryFundCode).distinct().toList();
        Map<String, FundMetricSnapshotDO> snapshots = codes.isEmpty()
                ? Map.of()
                : metricSnapshotMapper.selectList(new LambdaQueryWrapper<FundMetricSnapshotDO>()
                                .in(FundMetricSnapshotDO::getFundCode, codes))
                        .stream()
                        .collect(Collectors.toMap(FundMetricSnapshotDO::getFundCode, Function.identity()));

        Map<FundCategory, List<FundMetricDailyDO>> metricsByCategory = new EnumMap<>(FundCategory.class);
        for (FundCategory category : FundCategory.values()) {
            List<FundMetricDailyDO> metrics = new ArrayList<>();
            List<Long> categoryMasterIds = java.util.stream.Stream.concat(
                            universe.stream()
                                    .filter(member -> category.name().equals(member.getFundCategory()))
                                    .map(FundUniverseDO::getMasterId),
                            incumbents.stream()
                                    .filter(member -> category.name().equals(member.getFundCategory()))
                                    .map(FundRankMembershipDO::getMasterId))
                    .distinct().toList();
            for (Long masterId : categoryMasterIds) {
                FundMasterDO master = masters.get(masterId);
                if (master == null) {
                    continue;
                }
                metrics.add(upsertRawMetric(master, snapshots.get(master.getPrimaryFundCode()), calculationDate));
            }
            LocalDate sourceMetricDate = selectPublicationSourceDate(metrics);
            scoreCategory(category, metrics, sourceMetricDate);
            metrics.forEach(metricDailyMapper::updateById);
            metricsByCategory.put(category, metrics.stream()
                    .filter(metric -> sourceMetricDate != null
                            && sourceMetricDate.equals(metric.getSourceMetricDate()))
                    .sorted(Comparator.comparing(FundMetricDailyDO::getTotalScore,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList());
        }
        return metricsByCategory;
    }

    private FundMetricDailyDO upsertRawMetric(FundMasterDO master,
                                              FundMetricSnapshotDO snapshot,
                                              LocalDate calculationDate) {
        FundMetricDailyDO metric = metricDailyMapper.selectOne(new LambdaQueryWrapper<FundMetricDailyDO>()
                .eq(FundMetricDailyDO::getMasterId, master.getId())
                .eq(FundMetricDailyDO::getMetricDate, calculationDate));
        if (metric == null) {
            metric = new FundMetricDailyDO();
            metric.setMasterId(master.getId());
            metric.setMetricDate(calculationDate);
        }
        if (snapshot != null) {
            metric.setSourceMetricDate(snapshot.getStatisticDate());
            metric.setOneMonthReturn(snapshot.getOneMonthReturn());
            metric.setThreeMonthReturn(snapshot.getThreeMonthReturn());
            metric.setSixMonthReturn(snapshot.getSixMonthReturn());
            metric.setOneYearReturn(snapshot.getOneYearReturn());
            metric.setMaxDrawdown(snapshot.getMaxDrawdown());
            metric.setVolatility(snapshot.getVolatility());
            metric.setSampleSize(Math.toIntExact(fundNavMapper.selectCount(
                    new LambdaQueryWrapper<FundNavDO>()
                            .eq(FundNavDO::getFundCode, master.getPrimaryFundCode()))));
            metric.setReturnDrawdownRatio(ratio(snapshot.getSixMonthReturn(), snapshot.getMaxDrawdown()));
        } else {
            metric.setSourceMetricDate(null);
            metric.setSampleSize(0);
        }
        metric.setEligible(isEligible(metric));
        if (metric.getId() == null) {
            metricDailyMapper.insert(metric);
        } else {
            metricDailyMapper.updateById(metric);
        }
        return metric;
    }

    private boolean isEligible(FundMetricDailyDO metric) {
        return metric.getSourceMetricDate() != null
                && metric.getSampleSize() != null && metric.getSampleSize() >= MINIMUM_SAMPLE_SIZE
                && metric.getSixMonthReturn() != null
                && metric.getMaxDrawdown() != null
                && metric.getVolatility() != null;
    }

    private LocalDate selectPublicationSourceDate(List<FundMetricDailyDO> metrics) {
        Map<LocalDate, Long> coverageByDate = new HashMap<>();
        metrics.stream()
                .filter(FundMetricDailyDO::getEligible)
                .map(FundMetricDailyDO::getSourceMetricDate)
                .filter(date -> date != null)
                .forEach(date -> coverageByDate.merge(date, 1L, Long::sum));
        return coverageByDate.entrySet().stream()
                .max(Map.Entry.<LocalDate, Long>comparingByValue()
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private void scoreCategory(FundCategory category,
                               List<FundMetricDailyDO> metrics,
                               LocalDate sourceMetricDate) {
        List<FundMetricDailyDO> eligible = metrics.stream()
                .filter(FundMetricDailyDO::getEligible)
                .filter(metric -> sourceMetricDate != null
                        && sourceMetricDate.equals(metric.getSourceMetricDate()))
                .toList();
        for (FundMetricDailyDO metric : metrics) {
            if (!eligible.contains(metric)) {
                clearScores(metric);
                continue;
            }
            metric.setPerformanceScore(percentile(eligible, metric, this::performanceReturn, true));
            metric.setDrawdownScore(percentile(eligible, metric, this::drawdownMagnitude, false));
            metric.setVolatilityScore(percentile(eligible, metric, FundMetricDailyDO::getVolatility, false));
            metric.setRatioScore(percentile(eligible, metric, FundMetricDailyDO::getReturnDrawdownRatio, true));
            metric.setDataQualityScore(BigDecimal.valueOf(metric.getSampleSize())
                    .divide(BigDecimal.valueOf(properties.getSyncHistorySize()), 6, RoundingMode.HALF_UP)
                    .multiply(ONE_HUNDRED)
                    .min(ONE_HUNDRED)
                    .setScale(4, RoundingMode.HALF_UP));
            Weights weights = Weights.forCategory(category);
            metric.setTotalScore(weighted(metric, weights));
        }
    }

    private BigDecimal performanceReturn(FundMetricDailyDO metric) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal weight = BigDecimal.ZERO;
        total = addWeighted(total, metric.getOneMonthReturn(), 15);
        weight = addWeight(weight, metric.getOneMonthReturn(), 15);
        total = addWeighted(total, metric.getThreeMonthReturn(), 25);
        weight = addWeight(weight, metric.getThreeMonthReturn(), 25);
        total = addWeighted(total, metric.getSixMonthReturn(), 30);
        weight = addWeight(weight, metric.getSixMonthReturn(), 30);
        total = addWeighted(total, metric.getOneYearReturn(), 30);
        weight = addWeight(weight, metric.getOneYearReturn(), 30);
        return weight.signum() == 0 ? null : total.divide(weight, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal drawdownMagnitude(FundMetricDailyDO metric) {
        return metric.getMaxDrawdown() == null ? null : metric.getMaxDrawdown().abs();
    }

    private BigDecimal percentile(List<FundMetricDailyDO> metrics,
                                  FundMetricDailyDO current,
                                  Function<FundMetricDailyDO, BigDecimal> extractor,
                                  boolean higherIsBetter) {
        BigDecimal currentValue = extractor.apply(current);
        List<BigDecimal> values = metrics.stream().map(extractor).filter(value -> value != null).sorted().toList();
        if (currentValue == null || values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        if (values.size() == 1) {
            return ONE_HUNDRED;
        }
        long lowerCount = values.stream().filter(value -> value.compareTo(currentValue) < 0).count();
        long equalCount = values.stream().filter(value -> value.compareTo(currentValue) == 0).count();
        BigDecimal rank = BigDecimal.valueOf(lowerCount)
                .add(BigDecimal.valueOf(equalCount - 1).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP));
        BigDecimal ascending = rank.divide(BigDecimal.valueOf(values.size() - 1L), 8, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED);
        return (higherIsBetter ? ascending : ONE_HUNDRED.subtract(ascending)).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(BigDecimal returnValue, BigDecimal maxDrawdown) {
        if (returnValue == null || maxDrawdown == null || maxDrawdown.signum() == 0) {
            return null;
        }
        return returnValue.divide(maxDrawdown.abs(), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal weighted(FundMetricDailyDO metric, Weights weights) {
        return metric.getPerformanceScore().multiply(weights.performance())
                .add(metric.getDrawdownScore().multiply(weights.drawdown()))
                .add(metric.getVolatilityScore().multiply(weights.volatility()))
                .add(metric.getRatioScore().multiply(weights.ratio()))
                .add(metric.getDataQualityScore().multiply(weights.dataQuality()))
                .divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal addWeighted(BigDecimal total, BigDecimal value, int weight) {
        return value == null ? total : total.add(value.multiply(BigDecimal.valueOf(weight)));
    }

    private BigDecimal addWeight(BigDecimal total, BigDecimal value, int weight) {
        return value == null ? total : total.add(BigDecimal.valueOf(weight));
    }

    private void clearScores(FundMetricDailyDO metric) {
        metric.setPerformanceScore(null);
        metric.setDrawdownScore(null);
        metric.setVolatilityScore(null);
        metric.setRatioScore(null);
        metric.setDataQualityScore(null);
        metric.setTotalScore(null);
    }

    private record Weights(BigDecimal performance,
                           BigDecimal drawdown,
                           BigDecimal volatility,
                           BigDecimal ratio,
                           BigDecimal dataQuality) {
        private static Weights forCategory(FundCategory category) {
            return switch (category) {
                case ACTIVE_EQUITY, EQUITY_HYBRID -> of(45, 25, 15, 10, 5);
                case BOND -> of(30, 30, 25, 10, 5);
                case INDEX -> of(40, 20, 15, 15, 10);
            };
        }

        private static Weights of(int performance, int drawdown, int volatility, int ratio, int quality) {
            return new Weights(BigDecimal.valueOf(performance), BigDecimal.valueOf(drawdown),
                    BigDecimal.valueOf(volatility), BigDecimal.valueOf(ratio), BigDecimal.valueOf(quality));
        }
    }
}
