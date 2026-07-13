package fundcopilot.fund.constant;

public final class FundConstants {
    public static final String DEFAULT_USER_ID = "demo-user";
    public static final String EASTMONEY_FUND_PAGE_PREFIX = "https://fund.eastmoney.com/";
    public static final String AGENT_NAME_FUND_ANALYSIS = "FundAnalysisAgent";
    public static final String AGENT_TASK_STATUS_PENDING = "PENDING";
    public static final String AGENT_TASK_STATUS_RUNNING = "RUNNING";
    public static final String AGENT_STATUS_SUCCESS = "SUCCESS";
    public static final String AGENT_STATUS_FAILED = "FAILED";
    public static final String AGENT_STATUS_CANCELLED = "CANCELLED";
    public static final String AGENT_STATUS_TIMEOUT = "TIMEOUT";
    public static final String AGENT_STAGE_STATUS_RUNNING = "RUNNING";
    public static final String AGENT_STAGE_STATUS_SKIPPED = "SKIPPED";
    public static final String AGENT_STAGE_DATA_COLLECTION = "DATA_COLLECTION";
    public static final String AGENT_STAGE_PERFORMANCE_ANALYSIS = "PERFORMANCE_ANALYSIS";
    public static final String AGENT_STAGE_RISK_ANALYSIS = "RISK_ANALYSIS";
    public static final String AGENT_STAGE_PEER_COMPARISON = "PEER_COMPARISON";
    public static final String AGENT_STAGE_FACTOR_DEBATE = "FACTOR_DEBATE";
    public static final String AGENT_STAGE_COMPLIANCE_REVIEW = "COMPLIANCE_REVIEW";
    public static final String AGENT_STAGE_ANSWER_COMPOSER = "ANSWER_COMPOSER";
    public static final String SSE_PROGRESS = "PROGRESS";
    public static final String SSE_AGENT_STEP = "AGENT_STEP";
    public static final String SSE_CARD = "CARD";
    public static final String SSE_TOKEN = "TOKEN";
    public static final String SSE_DONE = "DONE";
    public static final String SSE_TASK_CREATED = "TASK_CREATED";
    public static final String SSE_STAGE_STARTED = "STAGE_STARTED";
    public static final String SSE_STAGE_DONE = "STAGE_DONE";
    public static final String SSE_SECTION = "SECTION";
    public static final String SSE_COMPLIANCE_BLOCKED = "COMPLIANCE_BLOCKED";
    public static final String SSE_TASK_CANCELLED = "TASK_CANCELLED";
    public static final String SSE_TASK_TIMEOUT = "TASK_TIMEOUT";
    public static final String SSE_TASK_RERUN_STARTED = "TASK_RERUN_STARTED";
    public static final String SSE_ERROR = "ERROR";

    private FundConstants() {
    }
}
