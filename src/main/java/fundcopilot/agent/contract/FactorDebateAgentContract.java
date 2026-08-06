package fundcopilot.agent.contract;

import java.util.List;

public final class FactorDebateAgentContract {
    private FactorDebateAgentContract() {
    }

    public record Input(
            String fundCode,
            List<String> positiveFactors,
            List<String> riskFactors,
            boolean pastContextAvailable,
            String discussionBoundary
    ) {
        public Input {
            positiveFactors = positiveFactors == null ? List.of() : List.copyOf(positiveFactors);
            riskFactors = riskFactors == null ? List.of() : List.copyOf(riskFactors);
        }
    }

    public record Output(
            List<String> positiveArguments,
            List<String> counterArguments,
            List<String> unresolvedIssues,
            String balancedConclusion
    ) {
        public Output {
            positiveArguments = immutable(positiveArguments);
            counterArguments = immutable(counterArguments);
            unresolvedIssues = immutable(unresolvedIssues);
        }

        public boolean isValid() {
            return hasItems(positiveArguments)
                    && hasItems(counterArguments)
                    && hasItems(unresolvedIssues)
                    && hasText(balancedConclusion);
        }

        public String render() {
            return "正向论据：" + String.join("；", positiveArguments)
                    + " 反向论据：" + String.join("；", counterArguments)
                    + " 未决问题：" + String.join("；", unresolvedIssues)
                    + " 平衡结论：" + balancedConclusion.trim();
        }
    }

    private static List<String> immutable(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static boolean hasItems(List<String> values) {
        return !values.isEmpty() && values.stream().allMatch(FactorDebateAgentContract::hasText);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
