package fundcopilot.agent.contract;

import java.util.List;

public final class ComplianceAgentContract {
    private ComplianceAgentContract() {
    }

    public record Input(String question) {
    }

    public record Output(
            boolean restricted,
            String message,
            List<String> triggeredRuleCodes,
            List<String> rewriteRequirements,
            String disclaimer
    ) {
        public Output {
            triggeredRuleCodes = triggeredRuleCodes == null ? List.of() : List.copyOf(triggeredRuleCodes);
            rewriteRequirements = rewriteRequirements == null ? List.of() : List.copyOf(rewriteRequirements);
        }

        public boolean isValid() {
            return hasText(message)
                    && rewriteRequirements.stream().allMatch(ComplianceAgentContract::hasText)
                    && triggeredRuleCodes.stream().allMatch(ComplianceAgentContract::hasText)
                    && hasText(disclaimer);
        }

        public String render() {
            String rules = triggeredRuleCodes.isEmpty() ? "未触发限制规则" : String.join("；", triggeredRuleCodes);
            String requirements = rewriteRequirements.isEmpty()
                    ? "保持事实分析和风险揭示"
                    : String.join("；", rewriteRequirements);
            return message.trim()
                    + " 规则：" + rules
                    + " 回答要求：" + requirements
                    + " 免责声明：" + disclaimer.trim();
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
