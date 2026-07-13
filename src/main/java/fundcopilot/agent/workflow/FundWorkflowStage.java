package fundcopilot.agent.workflow;

public interface FundWorkflowStage {
    String stageCode();

    String stageName();

    int sortOrder();

    FundStageResult execute(FundWorkflowContext context);
}
