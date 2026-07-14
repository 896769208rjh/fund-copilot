package fundcopilot.agent.workflow;

import fundcopilot.agent.service.FundAgentState;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class FundWorkflowNode {
    private final FundWorkflowStage stage;
    private final Set<String> dependencyStageCodes;
    private final Predicate<FundAgentState> activationPredicate;

    public FundWorkflowNode(FundWorkflowStage stage,
                            Set<String> dependencyStageCodes,
                            Predicate<FundAgentState> activationPredicate) {
        this.stage = Objects.requireNonNull(stage, "stage must not be null");
        this.dependencyStageCodes = Set.copyOf(new LinkedHashSet<>(dependencyStageCodes));
        this.activationPredicate = Objects.requireNonNull(
                activationPredicate, "activationPredicate must not be null");
    }

    public static FundWorkflowNode always(FundWorkflowStage stage, String... dependencyStageCodes) {
        return new FundWorkflowNode(stage, Set.of(dependencyStageCodes), ignored -> true);
    }

    public static FundWorkflowNode conditional(FundWorkflowStage stage,
                                               Predicate<FundAgentState> activationPredicate,
                                               String... dependencyStageCodes) {
        return new FundWorkflowNode(stage, Set.of(dependencyStageCodes), activationPredicate);
    }

    public FundWorkflowStage stage() {
        return stage;
    }

    public Set<String> dependencyStageCodes() {
        return dependencyStageCodes;
    }

    public boolean isActive(FundAgentState state) {
        return activationPredicate.test(state);
    }
}
