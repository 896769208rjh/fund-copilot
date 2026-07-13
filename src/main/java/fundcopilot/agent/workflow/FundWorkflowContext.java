package fundcopilot.agent.workflow;

import fundcopilot.agent.service.FundAgentState;

public class FundWorkflowContext {
    private final FundAgentState state;

    public FundWorkflowContext(FundAgentState state) {
        this.state = state;
    }

    public FundAgentState getState() {
        return state;
    }
}
