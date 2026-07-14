package fundcopilot.agent.workflow;

import fundcopilot.agent.service.FundAgentState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class FundWorkflowGraph {
    private final Map<String, FundWorkflowNode> nodeMap = new LinkedHashMap<>();

    public FundWorkflowGraph(List<FundWorkflowNode> nodes) {
        nodes.stream()
                .sorted(Comparator.comparingInt(node -> node.stage().sortOrder()))
                .forEach(this::registerNode);
        validateDependencies();
        validateAcyclic();
    }

    public Optional<FundWorkflowStage> findStage(String stageCode) {
        return Optional.ofNullable(nodeMap.get(stageCode)).map(FundWorkflowNode::stage);
    }

    public List<FundWorkflowStage> readyStages(FundAgentState state, Set<String> completedStageCodes) {
        return nodeMap.values().stream()
                .filter(node -> node.isActive(state))
                .filter(node -> !completedStageCodes.contains(node.stage().stageCode()))
                .filter(node -> dependenciesSatisfied(node, state, completedStageCodes))
                .map(FundWorkflowNode::stage)
                .sorted(Comparator.comparingInt(FundWorkflowStage::sortOrder))
                .toList();
    }

    public boolean isComplete(FundAgentState state, Set<String> completedStageCodes) {
        return activeStageCodes(state).stream().allMatch(completedStageCodes::contains);
    }

    public Set<String> activeStageCodes(FundAgentState state) {
        Set<String> activeStageCodes = new LinkedHashSet<>();
        nodeMap.values().stream()
                .filter(node -> node.isActive(state))
                .forEach(node -> activeStageCodes.add(node.stage().stageCode()));
        return Set.copyOf(activeStageCodes);
    }

    public List<FundWorkflowStage> inactiveStages(FundAgentState state) {
        return nodeMap.values().stream()
                .filter(node -> !node.isActive(state))
                .map(FundWorkflowNode::stage)
                .sorted(Comparator.comparingInt(FundWorkflowStage::sortOrder))
                .toList();
    }

    public List<String> rerunStageCodes(String stageCode) {
        if (!nodeMap.containsKey(stageCode)) {
            throw new IllegalArgumentException("未知工作流阶段: " + stageCode);
        }
        Set<String> rerunStageCodes = new LinkedHashSet<>();
        Deque<String> pendingStageCodes = new ArrayDeque<>();
        pendingStageCodes.add(stageCode);
        while (!pendingStageCodes.isEmpty()) {
            String currentStageCode = pendingStageCodes.removeFirst();
            if (!rerunStageCodes.add(currentStageCode)) {
                continue;
            }
            nodeMap.values().stream()
                    .filter(node -> node.dependencyStageCodes().contains(currentStageCode))
                    .map(node -> node.stage().stageCode())
                    .forEach(pendingStageCodes::addLast);
        }
        return rerunStageCodes.stream()
                .map(nodeMap::get)
                .map(FundWorkflowNode::stage)
                .sorted(Comparator.comparingInt(FundWorkflowStage::sortOrder))
                .map(FundWorkflowStage::stageCode)
                .toList();
    }

    public List<FundWorkflowStage> orderedStages() {
        return nodeMap.values().stream()
                .map(FundWorkflowNode::stage)
                .sorted(Comparator.comparingInt(FundWorkflowStage::sortOrder))
                .toList();
    }

    private void registerNode(FundWorkflowNode node) {
        String stageCode = node.stage().stageCode();
        if (nodeMap.putIfAbsent(stageCode, node) != null) {
            throw new IllegalArgumentException("工作流存在重复节点: " + stageCode);
        }
    }

    private boolean dependenciesSatisfied(FundWorkflowNode node,
                                          FundAgentState state,
                                          Set<String> completedStageCodes) {
        return node.dependencyStageCodes().stream().allMatch(dependencyStageCode -> {
            FundWorkflowNode dependencyNode = nodeMap.get(dependencyStageCode);
            return !dependencyNode.isActive(state) || completedStageCodes.contains(dependencyStageCode);
        });
    }

    private void validateDependencies() {
        for (FundWorkflowNode node : nodeMap.values()) {
            for (String dependencyStageCode : node.dependencyStageCodes()) {
                if (!nodeMap.containsKey(dependencyStageCode)) {
                    throw new IllegalArgumentException(node.stage().stageCode()
                            + " 依赖不存在的节点: " + dependencyStageCode);
                }
                if (node.stage().stageCode().equals(dependencyStageCode)) {
                    throw new IllegalArgumentException("工作流节点不能依赖自身: " + dependencyStageCode);
                }
            }
        }
    }

    private void validateAcyclic() {
        Map<String, Integer> indegrees = new HashMap<>();
        Map<String, List<String>> outgoingEdges = new HashMap<>();
        nodeMap.keySet().forEach(stageCode -> {
            indegrees.put(stageCode, 0);
            outgoingEdges.put(stageCode, new ArrayList<>());
        });
        for (FundWorkflowNode node : nodeMap.values()) {
            for (String dependencyStageCode : node.dependencyStageCodes()) {
                indegrees.compute(node.stage().stageCode(), (ignored, value) -> value == null ? 1 : value + 1);
                outgoingEdges.get(dependencyStageCode).add(node.stage().stageCode());
            }
        }

        Deque<String> zeroIndegreeNodes = new ArrayDeque<>();
        indegrees.forEach((stageCode, indegree) -> {
            if (indegree == 0) {
                zeroIndegreeNodes.addLast(stageCode);
            }
        });
        Set<String> visitedNodes = new HashSet<>();
        while (!zeroIndegreeNodes.isEmpty()) {
            String stageCode = zeroIndegreeNodes.removeFirst();
            visitedNodes.add(stageCode);
            for (String nextStageCode : outgoingEdges.get(stageCode)) {
                int nextIndegree = indegrees.computeIfPresent(nextStageCode, (ignored, value) -> value - 1);
                if (nextIndegree == 0) {
                    zeroIndegreeNodes.addLast(nextStageCode);
                }
            }
        }
        if (visitedNodes.size() != nodeMap.size()) {
            throw new IllegalArgumentException("基金工作流不能包含循环依赖");
        }
    }
}
