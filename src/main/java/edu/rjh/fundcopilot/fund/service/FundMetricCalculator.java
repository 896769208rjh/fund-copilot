package edu.rjh.fundcopilot.fund.service;

import edu.rjh.fundcopilot.fund.entity.FundMetricSnapshotDO;
import edu.rjh.fundcopilot.fund.entity.FundNavDO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Component
public class FundMetricCalculator {
    private static final int SCALE = 6;

    public FundMetricSnapshotDO calculate(String fundCode, List<FundNavDO> navs) {
        List<FundNavDO> sorted = navs.stream()
                .filter(nav -> nav.getUnitNav() != null)
                .sorted(Comparator.comparing(FundNavDO::getNavDate))
                .toList();

        FundMetricSnapshotDO snapshot = new FundMetricSnapshotDO();
        snapshot.setFundCode(fundCode);
        snapshot.setStatisticDate(sorted.isEmpty() ? LocalDate.now() : sorted.get(sorted.size() - 1).getNavDate());

        snapshot.setOneMonthReturn(returnFromWindow(sorted, 22));
        snapshot.setThreeMonthReturn(returnFromWindow(sorted, 66));
        snapshot.setSixMonthReturn(returnFromWindow(sorted, 132));
        snapshot.setOneYearReturn(returnFromWindow(sorted, 252));
        snapshot.setMaxDrawdown(maxDrawdown(sorted));
        snapshot.setVolatility(volatility(sorted));
        return snapshot;
    }

    private BigDecimal returnFromWindow(List<FundNavDO> navs, int tradingDays) {
        if (navs.size() < 2) {
            return BigDecimal.ZERO;
        }

        int start = Math.max(0, navs.size() - tradingDays - 1);
        BigDecimal first = navs.get(start).getUnitNav();
        BigDecimal last = navs.get(navs.size() - 1).getUnitNav();

        if (first == null || first.compareTo(BigDecimal.ZERO) == 0 || last == null) {
            return BigDecimal.ZERO;
        }

        return last.subtract(first)
                .divide(first, SCALE, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal maxDrawdown(List<FundNavDO> navs) {
        if (navs.size() < 2) {
            return BigDecimal.ZERO;
        }

        BigDecimal peak = navs.get(0).getUnitNav();
        BigDecimal maxDrawdown = BigDecimal.ZERO;

        for (FundNavDO nav : navs) {
            BigDecimal value = nav.getUnitNav();
            if (value.compareTo(peak) > 0) {
                peak = value;
            }
            if (peak.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal drawdown = value.subtract(peak)
                        .divide(peak, SCALE, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                if (drawdown.compareTo(maxDrawdown) < 0) {
                    maxDrawdown = drawdown;
                }
            }
        }

        return maxDrawdown.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal volatility(List<FundNavDO> navs) {
        if (navs.size() < 3) {
            return BigDecimal.ZERO;
        }

        double[] dailyReturns = new double[navs.size() - 1];
        for (int i = 1; i < navs.size(); i++) {
            double previous = navs.get(i - 1).getUnitNav().doubleValue();
            double current = navs.get(i).getUnitNav().doubleValue();
            dailyReturns[i - 1] = previous == 0 ? 0 : (current - previous) / previous;
        }

        double mean = 0;
        for (double value : dailyReturns) {
            mean += value;
        }
        mean = mean / dailyReturns.length;

        double variance = 0;
        for (double value : dailyReturns) {
            variance += Math.pow(value - mean, 2);
        }
        variance = variance / (dailyReturns.length - 1);

        double annualized = Math.sqrt(variance) * Math.sqrt(252) * 100;
        return BigDecimal.valueOf(annualized).setScale(4, RoundingMode.HALF_UP);
    }
}
