package fundcopilot.agent.service;

import fundcopilot.agent.model.AgentThinkingMode;
import fundcopilot.agent.vo.FundAgentReportSectionVO;
import fundcopilot.agent.vo.FundAgentStageVO;
import fundcopilot.compliance.ComplianceService.ComplianceResult;
import fundcopilot.fund.vo.FundAnalysisResultVO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FundAgentState {
    private final Long taskId;
    private final String taskNo;
    private final String fundCode;
    private final String question;
    private final AgentThinkingMode thinkingMode;
    private FundAnalysisResultVO analysis;
    private ComplianceResult complianceResult;
    private String finalAnswer;
    private String dataRoute;
    private String dataQuality;
    private String pastContext;
    private int sectionOrder;
    private List<String> positiveFactors = new ArrayList<>();
    private List<String> riskFactors = new ArrayList<>();
    private final List<FundAgentStageVO> stages = new ArrayList<>();
    private final List<FundAgentReportSectionVO> sections = new ArrayList<>();
    private final Map<String, Object> structuredReports = new LinkedHashMap<>();

    public FundAgentState(Long taskId,
                          String taskNo,
                          String fundCode,
                          String question,
                          AgentThinkingMode thinkingMode) {
        this.taskId = taskId;
        this.taskNo = taskNo;
        this.fundCode = fundCode;
        this.question = question;
        this.thinkingMode = AgentThinkingMode.fromNullable(thinkingMode);
    }

    public Long getTaskId() {
        return taskId;
    }

    public String getTaskNo() {
        return taskNo;
    }

    public String getFundCode() {
        return fundCode;
    }

    public String getQuestion() {
        return question;
    }

    public AgentThinkingMode getThinkingMode() {
        return thinkingMode;
    }

    public FundAnalysisResultVO getAnalysis() {
        return analysis;
    }

    public void setAnalysis(FundAnalysisResultVO analysis) {
        this.analysis = analysis;
    }

    public ComplianceResult getComplianceResult() {
        return complianceResult;
    }

    public void setComplianceResult(ComplianceResult complianceResult) {
        this.complianceResult = complianceResult;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public void setFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer;
    }

    public String getDataRoute() {
        return dataRoute;
    }

    public void setDataRoute(String dataRoute) {
        this.dataRoute = dataRoute;
    }

    public String getDataQuality() {
        return dataQuality;
    }

    public void setDataQuality(String dataQuality) {
        this.dataQuality = dataQuality;
    }

    public String getPastContext() {
        return pastContext;
    }

    public void setPastContext(String pastContext) {
        this.pastContext = pastContext;
    }

    public List<String> getPositiveFactors() {
        return positiveFactors;
    }

    public void setPositiveFactors(List<String> positiveFactors) {
        this.positiveFactors = positiveFactors == null ? new ArrayList<>() : new ArrayList<>(positiveFactors);
    }

    public List<String> getRiskFactors() {
        return riskFactors;
    }

    public void setRiskFactors(List<String> riskFactors) {
        this.riskFactors = riskFactors == null ? new ArrayList<>() : new ArrayList<>(riskFactors);
    }

    public List<FundAgentStageVO> getStages() {
        return stages;
    }

    public List<FundAgentReportSectionVO> getSections() {
        return sections;
    }

    public Map<String, Object> getStructuredReports() {
        return structuredReports;
    }

    public int nextSectionOrder() {
        sectionOrder++;
        return sectionOrder;
    }

    public void addStage(FundAgentStageVO stageVO) {
        stages.removeIf(stage -> stage.stageCode().equals(stageVO.stageCode()));
        stages.add(stageVO);
    }

    public void addSection(FundAgentReportSectionVO sectionVO) {
        sections.add(sectionVO);
        sectionOrder = Math.max(sectionOrder, sectionVO.sortOrder() == null ? 0 : sectionVO.sortOrder());
    }

    public void addSections(List<FundAgentReportSectionVO> sectionVOList) {
        sections.clear();
        sectionVOList.forEach(this::addSection);
    }

    public void addStages(List<FundAgentStageVO> stageVOList) {
        stages.clear();
        stageVOList.forEach(this::addStage);
    }

    public void removeSectionsByStage(String stageCode) {
        sections.removeIf(section -> section.stageCode().equals(stageCode));
    }

    public void putStructuredReport(String stageCode, Object structuredReport) {
        structuredReports.put(stageCode, structuredReport);
    }

    public FundAgentStateSnapshot toSnapshot() {
        return new FundAgentStateSnapshot(
                taskId,
                taskNo,
                fundCode,
                question,
                thinkingMode,
                analysis,
                complianceResult,
                finalAnswer,
                dataRoute,
                dataQuality,
                pastContext,
                List.copyOf(positiveFactors),
                List.copyOf(riskFactors),
                Map.copyOf(structuredReports)
        );
    }

    public static FundAgentState fromSnapshot(FundAgentStateSnapshot snapshot) {
        FundAgentState state = new FundAgentState(
                snapshot.taskId(),
                snapshot.taskNo(),
                snapshot.fundCode(),
                snapshot.question(),
                snapshot.thinkingMode()
        );
        state.setAnalysis(snapshot.analysis());
        state.setComplianceResult(snapshot.complianceResult());
        state.setFinalAnswer(snapshot.finalAnswer());
        state.setDataRoute(snapshot.dataRoute());
        state.setDataQuality(snapshot.dataQuality());
        state.setPastContext(snapshot.pastContext());
        state.setPositiveFactors(snapshot.positiveFactors());
        state.setRiskFactors(snapshot.riskFactors());
        if (snapshot.structuredReports() != null) {
            state.getStructuredReports().putAll(snapshot.structuredReports());
        }
        return state;
    }
}
