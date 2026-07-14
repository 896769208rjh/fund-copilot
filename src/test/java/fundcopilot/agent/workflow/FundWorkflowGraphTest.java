package fundcopilot.agent.workflow;

import fundcopilot.agent.model.AgentThinkingMode;
import fundcopilot.agent.service.FundAgentState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FundWorkflowGraphTest {
    @Test
    void readyStagesShouldExposeParallelBranchesAndJoinNode() {
        FundWorkflowGraph graph = createGraph();
        FundAgentState state = state();

        assertThat(stageCodes(graph.readyStages(state, Set.of())))
                .containsExactly("DATA");
        assertThat(stageCodes(graph.readyStages(state, Set.of("DATA"))))
                .containsExactly("PERFORMANCE", "RISK", "PEER");
        assertThat(stageCodes(graph.readyStages(state, Set.of("DATA", "PERFORMANCE", "RISK", "PEER"))))
                .containsExactly("FACTOR");
        assertThat(graph.isComplete(state, Set.of("DATA", "PERFORMANCE", "RISK", "PEER", "FACTOR")))
                .isTrue();
    }

    @Test
    void rerunStageCodesShouldContainTargetAndGraphDescendantsOnly() {
        FundWorkflowGraph graph = createGraph();

        assertThat(graph.rerunStageCodes("RISK"))
                .containsExactly("RISK", "FACTOR")
                .doesNotContain("DATA", "PERFORMANCE", "PEER");
    }

    @Test
    void constructorShouldRejectCyclicDependencies() {
        FundWorkflowStage first = stage("FIRST", 1);
        FundWorkflowStage second = stage("SECOND", 2);

        assertThatThrownBy(() -> new FundWorkflowGraph(List.of(
                FundWorkflowNode.always(first, "SECOND"),
                FundWorkflowNode.always(second, "FIRST")
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("循环依赖");
    }

    @Test
    void inactiveDependencyShouldNotBlockJoinNode() {
        FundWorkflowStage data = stage("DATA", 1);
        FundWorkflowStage peer = stage("PEER", 2);
        FundWorkflowStage factor = stage("FACTOR", 3);
        FundWorkflowGraph graph = new FundWorkflowGraph(List.of(
                FundWorkflowNode.always(data),
                FundWorkflowNode.conditional(peer, ignored -> false, "DATA"),
                FundWorkflowNode.always(factor, "DATA", "PEER")
        ));

        assertThat(stageCodes(graph.readyStages(state(), Set.of("DATA"))))
                .containsExactly("FACTOR");
        assertThat(graph.inactiveStages(state()))
                .extracting(FundWorkflowStage::stageCode)
                .containsExactly("PEER");
    }

    private FundWorkflowGraph createGraph() {
        FundWorkflowStage data = stage("DATA", 1);
        FundWorkflowStage performance = stage("PERFORMANCE", 2);
        FundWorkflowStage risk = stage("RISK", 3);
        FundWorkflowStage peer = stage("PEER", 4);
        FundWorkflowStage factor = stage("FACTOR", 5);
        return new FundWorkflowGraph(List.of(
                FundWorkflowNode.always(data),
                FundWorkflowNode.always(performance, "DATA"),
                FundWorkflowNode.always(risk, "DATA"),
                FundWorkflowNode.always(peer, "DATA"),
                FundWorkflowNode.always(factor, "PERFORMANCE", "RISK", "PEER")
        ));
    }

    private FundWorkflowStage stage(String stageCode, int sortOrder) {
        return new FundWorkflowStage() {
            @Override
            public String stageCode() {
                return stageCode;
            }

            @Override
            public String stageName() {
                return stageCode;
            }

            @Override
            public int sortOrder() {
                return sortOrder;
            }

            @Override
            public FundStageResult execute(FundWorkflowContext context) {
                return FundStageResult.of(stageCode, List.of());
            }
        };
    }

    private FundAgentState state() {
        return new FundAgentState(1L, "TASK-1", "000001", "分析基金", AgentThinkingMode.BALANCED);
    }

    private List<String> stageCodes(List<FundWorkflowStage> stages) {
        return stages.stream().map(FundWorkflowStage::stageCode).toList();
    }
}
