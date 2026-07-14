package fundcopilot.fund.service;

import fundcopilot.fund.vo.FundAdvancedMetricVO;
import fundcopilot.fund.vo.FundNavPointVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FundAdvancedMetricCalculatorTest {
    private final FundAdvancedMetricCalculator calculator = new FundAdvancedMetricCalculator();

    @Test
    void calculateShouldDescribeInsufficientSamples() {
        FundAdvancedMetricVO metrics = calculator.calculate(List.of(
                nav("2026-07-14", "1.0000")
        ));

        assertThat(metrics.sampleSize()).isEqualTo(1);
        assertThat(metrics.annualizedReturn()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(metrics.sampleBoundary()).contains("少于 2 条");
    }

    @Test
    void calculateShouldReturnAnnualizedAndDownsideMetrics() {
        FundAdvancedMetricVO metrics = calculator.calculate(List.of(
                nav("2025-07-14", "1.0000"),
                nav("2025-10-14", "1.1000"),
                nav("2026-01-14", "0.9900"),
                nav("2026-07-14", "1.1000")
        ));

        assertThat(metrics.annualizedReturn()).isEqualByComparingTo(new BigDecimal("10.0000"));
        assertThat(metrics.downsideVolatility()).isGreaterThan(BigDecimal.ZERO);
        assertThat(metrics.returnDrawdownRatio()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(metrics.observationDays()).isEqualTo(365L);
        assertThat(metrics.sampleBoundary()).contains("短样本");
    }

    @Test
    void calculateShouldNotAnnualizeVeryShortObservationWindow() {
        FundAdvancedMetricVO metrics = calculator.calculate(List.of(
                nav("2026-07-01", "1.0000"),
                nav("2026-07-07", "1.0500")
        ));

        assertThat(metrics.annualizedReturn()).isNull();
        assertThat(metrics.returnDrawdownRatio()).isNull();
        assertThat(metrics.sampleBoundary()).contains("短样本");
    }

    private FundNavPointVO nav(String date, String unitNav) {
        return new FundNavPointVO(
                LocalDate.parse(date),
                new BigDecimal(unitNav),
                null,
                null
        );
    }
}
