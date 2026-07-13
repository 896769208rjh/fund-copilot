package fundcopilot.agent.workflow;

import fundcopilot.agent.service.FundAgentState;
import fundcopilot.fund.constant.FundConstants;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;

public class FundWorkflowGraph {
    private final Map<String, FundWorkflowStage> stageMap = new LinkedHashMap<>();

    public FundWorkflowGraph(List<FundWorkflowStage> stages) {
        stages.stream()
                .sorted(Comparator.comparingInt(FundWorkflowStage::sortOrder))
                .forEach(stage -> stageMap.put(stage.stageCode(), stage));
    }

    public Optional<FundWorkflowStage> startStage() {
        return Optional.ofNullable(stageMap.get(FundConstants.AGENT_STAGE_DATA_COLLECTION));
    }

    public Optional<FundWorkflowStage> findStage(String stageCode) {
        return Optional.ofNullable(stageMap.get(stageCode));
    }

    public Optional<FundWorkflowStage> nextStage(FundWorkflowStage currentStage,
                                                 FundAgentState state,
                                                 BiPredicate<Long, String> successfulStagePredicate) {
        String nextStageCode = nextStageCode(currentStage.stageCode(), state);
        while (nextStageCode != null && successfulStagePredicate.test(state.getTaskId(), nextStageCode)) {
            FundWorkflowStage skippedStage = stageMap.get(nextStageCode);
            nextStageCode = skippedStage == null ? null : nextStageCode(skippedStage.stageCode(), state);
        }
        return Optional.ofNullable(nextStageCode).map(stageMap::get);
    }

    public List<FundWorkflowStage> orderedStages() {
        return stageMap.values().stream()
                .sorted(Comparator.comparingInt(FundWorkflowStage::sortOrder))
                .toList();
    }

    private String nextStageCode(String currentStageCode, FundAgentState state) {
        Objects.requireNonNull(currentStageCode, "currentStageCode must not be null");
        return switch (currentStageCode) {
            case FundConstants.AGENT_STAGE_DATA_COLLECTION -> FundConstants.AGENT_STAGE_PERFORMANCE_ANALYSIS;
            case FundConstants.AGENT_STAGE_PERFORMANCE_ANALYSIS -> FundConstants.AGENT_STAGE_RISK_ANALYSIS;
            case FundConstants.AGENT_STAGE_RISK_ANALYSIS -> shouldRunPeerComparison(state)
                    ? FundConstants.AGENT_STAGE_PEER_COMPARISON
                    : FundConstants.AGENT_STAGE_FACTOR_DEBATE;
            case FundConstants.AGENT_STAGE_PEER_COMPARISON -> FundConstants.AGENT_STAGE_FACTOR_DEBATE;
            case FundConstants.AGENT_STAGE_FACTOR_DEBATE -> FundConstants.AGENT_STAGE_COMPLIANCE_REVIEW;
            case FundConstants.AGENT_STAGE_COMPLIANCE_REVIEW -> FundConstants.AGENT_STAGE_ANSWER_COMPOSER;
            case FundConstants.AGENT_STAGE_ANSWER_COMPOSER -> null;
            default -> throw new IllegalArgumentException("未知工作流阶段: " + currentStageCode);
        };
    }

    private boolean shouldRunPeerComparison(FundAgentState state) {
        return state.getAnalysis() != null
                && state.getAnalysis().detail() != null
                && !Boolean.TRUE.equals(state.getAnalysis().detail().stale());
    }
}
