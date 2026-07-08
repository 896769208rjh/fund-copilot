package fundcopilot.fund.service;

import fundcopilot.fund.entity.FundMetricSnapshotDO;
import fundcopilot.fund.entity.FundNavDO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FundMetricCalculatorTest {
    private final FundMetricCalculator fundMetricCalculator = new FundMetricCalculator();

    @Test
    void calculateShouldReturnZeroWhenNavListIsEmpty() {
        FundMetricSnapshotDO snapshotDO = fundMetricCalculator.calculate("000001", List.of());

        assertThat(snapshotDO.getFundCode()).isEqualTo("000001");
        assertThat(snapshotDO.getOneMonthReturn()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshotDO.getMaxDrawdown()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshotDO.getVolatility()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void calculateShouldReturnCoreMetricsForNormalNavList() {
        List<FundNavDO> navList = List.of(
                nav("2026-07-01", "1.0000"),
                nav("2026-07-02", "1.1000"),
                nav("2026-07-03", "1.0000"),
                nav("2026-07-06", "1.2000")
        );

        FundMetricSnapshotDO snapshotDO = fundMetricCalculator.calculate("000001", navList);

        assertThat(snapshotDO.getOneMonthReturn()).isEqualByComparingTo(new BigDecimal("20.0000"));
        assertThat(snapshotDO.getMaxDrawdown()).isEqualByComparingTo(new BigDecimal("-9.0909"));
        assertThat(snapshotDO.getVolatility()).isGreaterThan(BigDecimal.ZERO);
        assertThat(snapshotDO.getStatisticDate()).isEqualTo(LocalDate.parse("2026-07-06"));
    }

    private FundNavDO nav(String date, String unitNav) {
        FundNavDO navDO = new FundNavDO();
        navDO.setNavDate(LocalDate.parse(date));
        navDO.setUnitNav(new BigDecimal(unitNav));
        return navDO;
    }
}
