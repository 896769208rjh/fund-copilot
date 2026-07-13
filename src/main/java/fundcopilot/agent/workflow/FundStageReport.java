package fundcopilot.agent.workflow;

public record FundStageReport(
        String sectionType,
        String title,
        String content,
        Object structuredData
) {
}
