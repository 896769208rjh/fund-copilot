package fundcopilot.compliance;

import fundcopilot.compliance.ComplianceService.ComplianceResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ComplianceServiceTest {
    private final ComplianceService complianceService = new ComplianceService();

    @Test
    void checkShouldRestrictBuySuggestionQuestion() {
        ComplianceResult result = complianceService.check("这只基金适合买入吗");

        assertThat(result.restricted()).isTrue();
        assertThat(result.disclaimer()).contains("不构成任何投资建议");
    }

    @Test
    void checkShouldAllowNormalAnalysisQuestion() {
        ComplianceResult result = complianceService.check("分析一下这只基金的历史表现");

        assertThat(result.restricted()).isFalse();
        assertThat(result.disclaimer()).contains("基金有风险");
    }
}
