package fundcopilot.fund.service;

import fundcopilot.fund.vo.FundAdvancedMetricVO;
import fundcopilot.fund.vo.FundNavPointVO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Component
public class FundAdvancedMetricCalculator {
    private static final int SCALE = 4;
    private static final int TRADING_DAYS_PER_YEAR = 252;
    private static final int MIN_RELIABLE_SAMPLE_SIZE = 60;
    private static final int MIN_RELIABLE_OBSERVATION_DAYS = 90;
    private static final int MIN_ANNUALIZATION_OBSERVATION_DAYS = 30;

    public FundAdvancedMetricVO calculate(List<FundNavPointVO> navPoints) {
        List<FundNavPointVO> sortedNavPoints = navPoints.stream()
                .filter(navPoint -> navPoint.navDate() != null)
                .filter(navPoint -> navPoint.unitNav() != null && navPoint.unitNav().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(FundNavPointVO::navDate))
                .toList();
        if (sortedNavPoints.size() < 2) {
            return new FundAdvancedMetricVO(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    0L,
                    sortedNavPoints.size(),
                    "有效净值样本少于 2 条，无法计算高级指标。"
            );
        }

        long observationDays = Math.max(1L, ChronoUnit.DAYS.between(
                sortedNavPoints.get(0).navDate(),
                sortedNavPoints.get(sortedNavPoints.size() - 1).navDate()));
        BigDecimal annualizedReturn = observationDays < MIN_ANNUALIZATION_OBSERVATION_DAYS
                ? null
                : annualizedReturn(sortedNavPoints, observationDays);
        BigDecimal downsideVolatility = downsideVolatility(sortedNavPoints);
        BigDecimal maxDrawdown = maxDrawdown(sortedNavPoints);
        BigDecimal returnDrawdownRatio = annualizedReturn == null || maxDrawdown.compareTo(BigDecimal.ZERO) == 0
                ? null
                : annualizedReturn.divide(maxDrawdown.abs(), SCALE, RoundingMode.HALF_UP);
        return new FundAdvancedMetricVO(
                annualizedReturn,
                downsideVolatility,
                returnDrawdownRatio,
                observationDays,
                sortedNavPoints.size(),
                buildSampleBoundary(sortedNavPoints.size(), observationDays)
        );
    }

    private BigDecimal annualizedReturn(List<FundNavPointVO> navPoints, long observationDays) {
        double firstNav = navPoints.get(0).unitNav().doubleValue();
        double lastNav = navPoints.get(navPoints.size() - 1).unitNav().doubleValue();
        double growthFactor = lastNav / firstNav;
        if (growthFactor <= 0) {
            return BigDecimal.ZERO;
        }
        double annualized = (Math.pow(growthFactor, 365.0D / observationDays) - 1.0D) * 100.0D;
        return finiteValue(annualized);
    }

    private BigDecimal downsideVolatility(List<FundNavPointVO> navPoints) {
        double squaredDownsideSum = 0.0D;
        int returnCount = navPoints.size() - 1;
        for (int index = 1; index < navPoints.size(); index++) {
            double previousNav = navPoints.get(index - 1).unitNav().doubleValue();
            double currentNav = navPoints.get(index).unitNav().doubleValue();
            double dailyReturn = currentNav / previousNav - 1.0D;
            double downsideReturn = Math.min(dailyReturn, 0.0D);
            squaredDownsideSum += downsideReturn * downsideReturn;
        }
        double downsideDeviation = Math.sqrt(squaredDownsideSum / returnCount)
                * Math.sqrt(TRADING_DAYS_PER_YEAR) * 100.0D;
        return finiteValue(downsideDeviation);
    }

    private BigDecimal maxDrawdown(List<FundNavPointVO> navPoints) {
        double peak = navPoints.get(0).unitNav().doubleValue();
        double maxDrawdown = 0.0D;
        for (FundNavPointVO navPoint : navPoints) {
            double currentNav = navPoint.unitNav().doubleValue();
            peak = Math.max(peak, currentNav);
            maxDrawdown = Math.min(maxDrawdown, currentNav / peak - 1.0D);
        }
        return finiteValue(maxDrawdown * 100.0D);
    }

    private String buildSampleBoundary(int sampleSize, long observationDays) {
        if (sampleSize < MIN_RELIABLE_SAMPLE_SIZE || observationDays < MIN_RELIABLE_OBSERVATION_DAYS) {
            return "当前仅有 " + sampleSize + " 条净值、覆盖 " + observationDays
                    + " 天，高级指标受短样本影响，仅用于风险观察。";
        }
        return "基于 " + sampleSize + " 条净值、覆盖 " + observationDays
                + " 天计算，仍不代表未来表现。";
    }

    private BigDecimal finiteValue(double value) {
        return Double.isFinite(value)
                ? BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }
}
