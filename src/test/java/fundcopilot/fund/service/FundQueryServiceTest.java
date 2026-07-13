package fundcopilot.fund.service;

import fundcopilot.fund.vo.FundCompareResultVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FundQueryServiceTest {
    @Autowired
    private FundQueryService fundQueryService;

    @Test
    void compareShouldReturnMultiFundDimensionRows() {
        FundCompareResultVO resultVO = fundQueryService.compare("000001,110022,161725");

        assertThat(resultVO.columns()).hasSize(3);
        assertThat(resultVO.rows())
                .extracting("dimension")
                .contains("基金经理", "近1年收益率", "最大回撤", "年化波动率");
        assertThat(resultVO.summary()).contains("不构成投资建议");
    }
}
