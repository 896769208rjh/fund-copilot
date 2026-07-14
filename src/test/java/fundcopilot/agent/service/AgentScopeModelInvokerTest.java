package fundcopilot.agent.service;

import fundcopilot.agent.AgentProperties;
import fundcopilot.agent.model.AgentThinkingMode;
import fundcopilot.agent.tool.FundAnalysisTools;
import fundcopilot.fund.constant.FundConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AgentScopeModelInvokerTest {
    private final AgentScopeModelInvoker modelInvoker = new AgentScopeModelInvoker(
            new AgentProperties(),
            mock(FundAnalysisTools.class),
            mock(AgentModelCallService.class)
    );

    @Test
    void resolveThinkingModeShouldUseFastModeForAuditAndCompliance() {
        assertThat(modelInvoker.resolveThinkingMode(
                FundConstants.AGENT_STAGE_DATA_COLLECTION, AgentThinkingMode.DEEP))
                .isEqualTo(AgentThinkingMode.FAST);
        assertThat(modelInvoker.resolveThinkingMode(
                FundConstants.AGENT_STAGE_COMPLIANCE_REVIEW, AgentThinkingMode.BALANCED))
                .isEqualTo(AgentThinkingMode.FAST);
    }

    @Test
    void resolveThinkingModeShouldKeepSynthesisAtLeastBalanced() {
        assertThat(modelInvoker.resolveThinkingMode(
                FundConstants.AGENT_STAGE_FACTOR_DEBATE, AgentThinkingMode.FAST))
                .isEqualTo(AgentThinkingMode.BALANCED);
        assertThat(modelInvoker.resolveThinkingMode(
                FundConstants.AGENT_STAGE_ANSWER_COMPOSER, AgentThinkingMode.DEEP))
                .isEqualTo(AgentThinkingMode.DEEP);
    }
}
