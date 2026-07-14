package fundcopilot.agent.workflow;

import fundcopilot.agent.service.AgentScopeModelInvoker;
import fundcopilot.agent.service.FundAgentState;
import fundcopilot.agent.vo.AgentStreamEventVO;
import fundcopilot.compliance.ComplianceService;
import fundcopilot.compliance.ComplianceService.ComplianceResult;
import fundcopilot.fund.constant.FundConstants;
import fundcopilot.fund.service.FundAdvancedMetricCalculator;
import fundcopilot.fund.service.FundQueryService;
import fundcopilot.fund.vo.FundAdvancedMetricVO;
import fundcopilot.fund.vo.FundAnalysisResultVO;
import fundcopilot.fund.vo.FundMetricVO;
import fundcopilot.fund.vo.FundSearchItemVO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
public class FundWorkflowStageFactory {
    public static final String SECTION_TYPE_DATA = "DATA";
    public static final String SECTION_TYPE_ANALYSIS = "ANALYSIS";
    public static final String SECTION_TYPE_RISK = "RISK";
    public static final String SECTION_TYPE_COMPLIANCE = "COMPLIANCE";
    public static final String SECTION_TYPE_ANSWER = "ANSWER";

    private static final String DATA_ROUTE = "Redis 缓存 -> MySQL 本地库 -> 东方财富实时同步 -> 本地演示兜底";
    private static final String ANSWER_BOUNDARY = "只做公开数据分析和风险揭示，不构成投资建议。";

    private final FundQueryService fundQueryService;
    private final ComplianceService complianceService;
    private final AgentScopeModelInvoker agentScopeModelInvoker;
    private final FundAdvancedMetricCalculator fundAdvancedMetricCalculator;

    public FundWorkflowStageFactory(FundQueryService fundQueryService,
                                    ComplianceService complianceService,
                                    AgentScopeModelInvoker agentScopeModelInvoker,
                                    FundAdvancedMetricCalculator fundAdvancedMetricCalculator) {
        this.fundQueryService = fundQueryService;
        this.complianceService = complianceService;
        this.agentScopeModelInvoker = agentScopeModelInvoker;
        this.fundAdvancedMetricCalculator = fundAdvancedMetricCalculator;
    }

    public List<FundWorkflowStage> createStages() {
        return List.of(
                new DataCollectionStage(),
                new PerformanceAnalysisStage(),
                new RiskAnalysisStage(),
                new PeerComparisonStage(),
                new FactorDebateStage(),
                new ComplianceReviewStage(),
                new AnswerComposerStage()
        );
    }

    public FundWorkflowGraph createGraph() {
        List<FundWorkflowStage> stages = createStages();
        return new FundWorkflowGraph(List.of(
                FundWorkflowNode.always(findStage(stages, FundConstants.AGENT_STAGE_DATA_COLLECTION)),
                FundWorkflowNode.always(
                        findStage(stages, FundConstants.AGENT_STAGE_PERFORMANCE_ANALYSIS),
                        FundConstants.AGENT_STAGE_DATA_COLLECTION),
                FundWorkflowNode.always(
                        findStage(stages, FundConstants.AGENT_STAGE_RISK_ANALYSIS),
                        FundConstants.AGENT_STAGE_DATA_COLLECTION),
                FundWorkflowNode.conditional(
                        findStage(stages, FundConstants.AGENT_STAGE_PEER_COMPARISON),
                        this::shouldRunPeerComparison,
                        FundConstants.AGENT_STAGE_DATA_COLLECTION),
                FundWorkflowNode.always(
                        findStage(stages, FundConstants.AGENT_STAGE_FACTOR_DEBATE),
                        FundConstants.AGENT_STAGE_PERFORMANCE_ANALYSIS,
                        FundConstants.AGENT_STAGE_RISK_ANALYSIS,
                        FundConstants.AGENT_STAGE_PEER_COMPARISON),
                FundWorkflowNode.always(
                        findStage(stages, FundConstants.AGENT_STAGE_COMPLIANCE_REVIEW),
                        FundConstants.AGENT_STAGE_FACTOR_DEBATE),
                FundWorkflowNode.always(
                        findStage(stages, FundConstants.AGENT_STAGE_ANSWER_COMPOSER),
                        FundConstants.AGENT_STAGE_COMPLIANCE_REVIEW)
        ));
    }

    private FundWorkflowStage findStage(List<FundWorkflowStage> stages, String stageCode) {
        return stages.stream()
                .filter(stage -> stage.stageCode().equals(stageCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("缺少工作流阶段: " + stageCode));
    }

    private boolean shouldRunPeerComparison(FundAgentState state) {
        return state.getAnalysis() != null
                && state.getAnalysis().detail() != null
                && !Boolean.TRUE.equals(state.getAnalysis().detail().stale());
    }

    private class DataCollectionStage implements FundWorkflowStage {
        @Override
        public String stageCode() {
            return FundConstants.AGENT_STAGE_DATA_COLLECTION;
        }

        @Override
        public String stageName() {
            return "数据采集 Agent";
        }

        @Override
        public int sortOrder() {
            return 1;
        }

        @Override
        public FundStageResult execute(FundWorkflowContext context) {
            FundAnalysisResultVO analysisResultVO = fundQueryService.analyze(context.getState().getFundCode());
            String dataQuality = Boolean.TRUE.equals(analysisResultVO.detail().stale())
                    ? "使用本地演示兜底数据，需谨慎解读。"
                    : "公开数据已同步到本地库，可用于演示分析。";
            FundStructuredReports.DataReport report = new FundStructuredReports.DataReport(
                    analysisResultVO.detail().fundCode(),
                    analysisResultVO.detail().fundName(),
                    Objects.toString(analysisResultVO.detail().fundType(), "未知"),
                    Objects.toString(analysisResultVO.detail().latestNav(), "暂无"),
                    Objects.toString(analysisResultVO.detail().latestNavDate(), "暂无"),
                    analysisResultVO.dataSource(),
                    DATA_ROUTE,
                    dataQuality,
                    analysisMode(context, stageCode()),
                    buildAgentNarrative(
                            context,
                            stageCode(),
                            stageName(),
                            "请检查基金数据来源、样本数量和数据质量，输出一段简短审计意见。",
                            "基金：" + analysisResultVO.detail().fundName()
                                    + "\n代码：" + analysisResultVO.detail().fundCode()
                                    + "\n数据来源：" + analysisResultVO.dataSource()
                                    + "\n样本数量：" + analysisResultVO.navPoints().size()
                                    + "\n数据质量：" + dataQuality,
                            "本阶段使用本地确定性数据检查，已记录数据路由和数据质量。"),
                    analysisResultVO.navPoints().size()
            );

            context.getState().setAnalysis(analysisResultVO);
            context.getState().setDataRoute(DATA_ROUTE);
            context.getState().setDataQuality(dataQuality);
            return FundStageResult.of("已读取基金基础信息、净值序列和指标快照。",
                    List.of(toReport(stageCode(), "基金数据概览", renderDataReport(report), report)));
        }
    }

    private class PerformanceAnalysisStage implements FundWorkflowStage {
        @Override
        public String stageCode() {
            return FundConstants.AGENT_STAGE_PERFORMANCE_ANALYSIS;
        }

        @Override
        public String stageName() {
            return "业绩分析 Agent";
        }

        @Override
        public int sortOrder() {
            return 2;
        }

        @Override
        public FundStageResult execute(FundWorkflowContext context) {
            FundMetricVO metrics = context.getState().getAnalysis().metrics();
            FundAdvancedMetricVO advancedMetrics = fundAdvancedMetricCalculator.calculate(
                    context.getState().getAnalysis().navPoints());
            FundStructuredReports.PerformanceReport report = new FundStructuredReports.PerformanceReport(
                    formatPercent(metrics.oneMonthReturn()),
                    formatPercent(metrics.threeMonthReturn()),
                    formatPercent(metrics.sixMonthReturn()),
                    formatPercent(metrics.oneYearReturn()),
                    formatPercent(advancedMetrics.annualizedReturn()),
                    formatPercent(advancedMetrics.downsideVolatility()),
                    formatDecimal(advancedMetrics.returnDrawdownRatio()),
                    advancedMetrics.sampleBoundary(),
                    Objects.toString(metrics.statisticDate(), "暂无"),
                    analysisMode(context, stageCode()),
                    buildAgentNarrative(
                            context,
                            stageCode(),
                            stageName(),
                            "请基于历史收益区间生成简短业绩解读，禁止给出买卖建议。",
                            "近1月：" + formatPercent(metrics.oneMonthReturn())
                                    + "\n近3月：" + formatPercent(metrics.threeMonthReturn())
                                    + "\n近6月：" + formatPercent(metrics.sixMonthReturn())
                                    + "\n近1年：" + formatPercent(metrics.oneYearReturn())
                                    + "\n区间年化：" + formatPercent(advancedMetrics.annualizedReturn())
                                    + "\n下行波动：" + formatPercent(advancedMetrics.downsideVolatility())
                                    + "\n收益回撤比：" + formatDecimal(advancedMetrics.returnDrawdownRatio())
                                    + "\n样本边界：" + advancedMetrics.sampleBoundary(),
                            "本阶段使用本地确定性指标解读，未调用 LLM。")
            );
            return FundStageResult.of("已完成收益区间和历史表现梳理。",
                    List.of(toReport(stageCode(), "历史表现", renderPerformanceReport(report), report)));
        }
    }

    private class RiskAnalysisStage implements FundWorkflowStage {
        @Override
        public String stageCode() {
            return FundConstants.AGENT_STAGE_RISK_ANALYSIS;
        }

        @Override
        public String stageName() {
            return "风险分析 Agent";
        }

        @Override
        public int sortOrder() {
            return 3;
        }

        @Override
        public FundStageResult execute(FundWorkflowContext context) {
            FundMetricVO metrics = context.getState().getAnalysis().metrics();
            FundStructuredReports.RiskReport report = new FundStructuredReports.RiskReport(
                    Objects.toString(context.getState().getAnalysis().detail().riskLevel(), "暂无"),
                    formatPercent(metrics.maxDrawdown()),
                    formatPercent(metrics.volatility()),
                    context.getState().getAnalysis().risks(),
                    analysisMode(context, stageCode()),
                    buildAgentNarrative(
                            context,
                            stageCode(),
                            stageName(),
                            "请基于风险等级、最大回撤和波动率生成简短风险解读，禁止预测未来收益。",
                            "风险等级：" + Objects.toString(context.getState().getAnalysis().detail().riskLevel(), "暂无")
                                    + "\n最大回撤：" + formatPercent(metrics.maxDrawdown())
                                    + "\n年化波动：" + formatPercent(metrics.volatility())
                                    + "\n风险项：" + String.join("；", context.getState().getAnalysis().risks()),
                            "本阶段使用本地确定性风险解读，未调用 LLM。")
            );
            return FundStageResult.of("已完成回撤、波动率和风险等级解释。",
                    List.of(toReport(stageCode(), "波动与回撤", renderRiskReport(report), report)));
        }
    }

    private class PeerComparisonStage implements FundWorkflowStage {
        @Override
        public String stageCode() {
            return FundConstants.AGENT_STAGE_PEER_COMPARISON;
        }

        @Override
        public String stageName() {
            return "同池对比 Agent";
        }

        @Override
        public int sortOrder() {
            return 4;
        }

        @Override
        public FundStageResult execute(FundWorkflowContext context) {
            List<String> peerLines = fundQueryService.listAlipayFundPool()
                    .stream()
                    .filter(peer -> !peer.fundCode().equals(context.getState().getFundCode()))
                    .limit(3)
                    .map(this::buildPeerLine)
                    .toList();
            List<String> peers = peerLines.isEmpty()
                    ? List.of("当前演示基金池暂无可比较基金。")
                    : peerLines;
            FundStructuredReports.PeerComparisonReport report = new FundStructuredReports.PeerComparisonReport(
                    "支付宝基金池演示列表",
                    peers,
                    "横向比较只用于识别差异，不输出排名、买入或卖出建议。",
                    analysisMode(context, stageCode()),
                    buildAgentNarrative(
                            context,
                            stageCode(),
                            stageName(),
                            "请基于同池基金指标生成横向差异解读，不要输出排名或购买建议。",
                            String.join("\n", peers),
                            "本阶段使用本地确定性同池对比，未调用 LLM。")
            );
            return FundStageResult.of("已基于演示基金池生成横向参考，不输出排名或购买建议。",
                    List.of(toReport(stageCode(), "支付宝基金池横向参考", renderPeerReport(report), report)));
        }

        private String buildPeerLine(FundSearchItemVO peer) {
            try {
                FundAnalysisResultVO peerAnalysis = fundQueryService.analyze(peer.fundCode());
                FundMetricVO metrics = peerAnalysis.metrics();
                return peer.fundName() + "（" + peer.fundCode() + "）：近1年 "
                        + formatPercent(metrics.oneYearReturn()) + "，最大回撤 "
                        + formatPercent(metrics.maxDrawdown()) + "，年化波动 "
                        + formatPercent(metrics.volatility());
            } catch (RuntimeException exception) {
                return peer.fundName() + "（" + peer.fundCode() + "）：暂无法读取对比指标。";
            }
        }
    }

    private class FactorDebateStage implements FundWorkflowStage {
        @Override
        public String stageCode() {
            return FundConstants.AGENT_STAGE_FACTOR_DEBATE;
        }

        @Override
        public String stageName() {
            return "优势风险讨论 Agent";
        }

        @Override
        public int sortOrder() {
            return 5;
        }

        @Override
        public FundStageResult execute(FundWorkflowContext context) {
            FundMetricVO metrics = context.getState().getAnalysis().metrics();
            List<String> positiveFactors = buildPositiveFactors(context, metrics);
            List<String> riskFactors = buildRiskFactors(context, metrics);
            String conclusion = "以上只用于形成分析维度，不转化为买卖动作。";
            FundStructuredReports.FactorDiscussionReport report = new FundStructuredReports.FactorDiscussionReport(
                    positiveFactors,
                    riskFactors,
                    conclusion,
                    analysisMode(context, stageCode()),
                    buildAgentNarrative(
                            context,
                            stageCode(),
                            stageName(),
                            "请围绕优势因素和风险因素做平衡讨论，不要转化为买卖动作。",
                            "优势因素：\n- " + String.join("\n- ", positiveFactors)
                                    + "\n风险因素：\n- " + String.join("\n- ", riskFactors),
                            "本阶段使用本地确定性因素讨论，未调用 LLM。")
            );
            context.getState().setPositiveFactors(positiveFactors);
            context.getState().setRiskFactors(riskFactors);
            return FundStageResult.of("已完成优势因素和风险因素讨论。",
                    List.of(toReport(stageCode(), "优势因素 vs 风险因素", renderFactorReport(report), report)));
        }

        private List<String> buildPositiveFactors(FundWorkflowContext context, FundMetricVO metrics) {
            List<String> positiveFactors = new ArrayList<>();
            positiveFactors.add("数据可追溯至公开来源，当前任务已保留数据日期和来源。");
            if (isPositive(metrics.oneYearReturn())) {
                positiveFactors.add("近一年收益率为正，可作为历史表现观察点。");
            }
            if (Boolean.FALSE.equals(context.getState().getAnalysis().detail().stale())) {
                positiveFactors.add("当前基金资料来自缓存或同步数据，不是纯本地兜底数据。");
            }
            if (context.getState().getPastContext() != null && !context.getState().getPastContext().isBlank()) {
                positiveFactors.add("存在同基金或相近分析记忆，可用于保持风险表达口径一致。");
            }
            return positiveFactors;
        }

        private List<String> buildRiskFactors(FundWorkflowContext context, FundMetricVO metrics) {
            List<String> riskFactors = new ArrayList<>(context.getState().getAnalysis().risks());
            if (isNegative(metrics.maxDrawdown())) {
                riskFactors.add("最大回撤为负，需关注极端行情下的净值承压。");
            }
            if (Boolean.TRUE.equals(context.getState().getAnalysis().detail().stale())) {
                riskFactors.add("当前存在兜底数据标记，不适合做精细化结论。");
            }
            return riskFactors;
        }
    }

    private class ComplianceReviewStage implements FundWorkflowStage {
        @Override
        public String stageCode() {
            return FundConstants.AGENT_STAGE_COMPLIANCE_REVIEW;
        }

        @Override
        public String stageName() {
            return "合规审核 Agent";
        }

        @Override
        public int sortOrder() {
            return 6;
        }

        @Override
        public FundStageResult execute(FundWorkflowContext context) {
            ComplianceResult complianceResult = complianceService.check(context.getState().getQuestion());
            FundStructuredReports.ComplianceReport report = new FundStructuredReports.ComplianceReport(
                    complianceResult.restricted(),
                    complianceResult.message(),
                    complianceResult.disclaimer()
            );
            context.getState().setComplianceResult(complianceResult);
            FundStageResult result = FundStageResult.of(
                    complianceResult.restricted() ? "已触发合规改写，禁止输出买卖建议。" : "未触发投资建议拦截。",
                    List.of(toReport(stageCode(), "合规审核", renderComplianceReport(report), report))
            );
            if (!complianceResult.restricted()) {
                return result;
            }
            return result.withEvent(new AgentStreamEventVO(FundConstants.SSE_COMPLIANCE_BLOCKED,
                    complianceResult.message()));
        }
    }

    private class AnswerComposerStage implements FundWorkflowStage {
        @Override
        public String stageCode() {
            return FundConstants.AGENT_STAGE_ANSWER_COMPOSER;
        }

        @Override
        public String stageName() {
            return "客服回答 Agent";
        }

        @Override
        public int sortOrder() {
            return 7;
        }

        @Override
        public FundStageResult execute(FundWorkflowContext context) {
            String deterministicAnswer = buildDeterministicAnswer(context);
            String answer = invokeAgentScope(context, deterministicAnswer);
            String answerMode = agentScopeModelInvoker.isEnabled()
                    ? "AgentScope + OpenAI 兼容模型"
                    : "本地确定性回答";
            FundStructuredReports.AnswerReport report = new FundStructuredReports.AnswerReport(
                    answer,
                    answerMode,
                    ANSWER_BOUNDARY
            );
            context.getState().setFinalAnswer(answer);
            return FundStageResult.of("已生成合规客服回答。",
                    List.of(toReport(stageCode(), "最终回答", renderAnswerReport(report), report)));
        }
    }

    private FundStageReport toReport(String stageCode, String title, String content, Object structuredData) {
        String sectionType = switch (stageCode) {
            case FundConstants.AGENT_STAGE_DATA_COLLECTION -> SECTION_TYPE_DATA;
            case FundConstants.AGENT_STAGE_RISK_ANALYSIS -> SECTION_TYPE_RISK;
            case FundConstants.AGENT_STAGE_COMPLIANCE_REVIEW -> SECTION_TYPE_COMPLIANCE;
            case FundConstants.AGENT_STAGE_ANSWER_COMPOSER -> SECTION_TYPE_ANSWER;
            default -> SECTION_TYPE_ANALYSIS;
        };
        return new FundStageReport(sectionType, title, content, structuredData);
    }

    private String buildDeterministicAnswer(FundWorkflowContext context) {
        FundAnalysisResultVO analysisResultVO = context.getState().getAnalysis();
        ComplianceResult complianceResult = context.getState().getComplianceResult();
        StringBuilder builder = new StringBuilder();
        builder.append("已基于公开数据完成基金分析。");
        if (complianceResult != null && complianceResult.restricted()) {
            builder.append("你的问题涉及买卖建议或收益承诺，我将只提供事实分析、风险揭示和适当性提醒。");
        }
        builder.append("\n\n基金：")
                .append(analysisResultVO.detail().fundName())
                .append("（")
                .append(analysisResultVO.detail().fundCode())
                .append("）");
        builder.append("\n数据日期：")
                .append(Objects.toString(analysisResultVO.detail().latestNavDate(), "暂无"));
        builder.append("\n历史表现：近1年收益率 ")
                .append(formatPercent(analysisResultVO.metrics().oneYearReturn()))
                .append("，最大回撤 ")
                .append(formatPercent(analysisResultVO.metrics().maxDrawdown()))
                .append("，年化波动率 ")
                .append(formatPercent(analysisResultVO.metrics().volatility()))
                .append("。");
        builder.append("\n风险点：")
                .append(String.join("；", context.getState().getRiskFactors()));
        builder.append("\n数据质量：")
                .append(Objects.toString(context.getState().getDataQuality(), "暂无"));
        if (context.getState().getPastContext() != null && !context.getState().getPastContext().isBlank()) {
            builder.append("\n历史记忆：本次回答已参考同基金历史分析口径。");
        }
        builder.append("\n适用边界：以上指标只反映公开数据和历史表现，不能推导未来收益，也不能替代个人风险承受能力评估。");
        builder.append("\n\n")
                .append(ComplianceService.STANDARD_DISCLAIMER);
        return builder.toString();
    }

    private String invokeAgentScope(FundWorkflowContext context, String fallback) {
        String prompt = "基金代码：" + context.getState().getFundCode()
                + "\n用户问题：" + context.getState().getQuestion()
                + "\n合规检查：" + context.getState().getComplianceResult().message()
                + "\n历史记忆：\n" + Objects.toString(context.getState().getPastContext(), "暂无")
                + "\n工作流报告：\n" + renderSections(context);
        return agentScopeModelInvoker.generateFinalAnswer(
                context.getState().getTaskId(),
                context.getState().getFundCode(),
                prompt,
                fallback,
                context.getState().getThinkingMode()
        );
    }

    private String renderSections(FundWorkflowContext context) {
        return context.getState().getSections()
                .stream()
                .sorted(Comparator.comparing(section -> section.sortOrder() == null ? 0 : section.sortOrder()))
                .map(section -> "## " + section.title() + "\n" + section.content())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    private String renderDataReport(FundStructuredReports.DataReport report) {
        return String.join("\n",
                "基金：" + report.fundName() + "（" + report.fundCode() + "）",
                "类型：" + report.fundType(),
                "最新净值：" + report.latestNav(),
                "净值日期：" + report.navDate(),
                "数据来源：" + report.dataSource(),
                "数据路由：" + report.dataRoute(),
                "数据质量：" + report.dataQuality(),
                "分析模式：" + report.analysisMode(),
                "Agent 解读：" + report.agentNarrative(),
                "样本数量：" + report.sampleSize() + " 条");
    }

    private String renderPerformanceReport(FundStructuredReports.PerformanceReport report) {
        return String.join("\n",
                "近1月收益率：" + report.oneMonthReturn(),
                "近3月收益率：" + report.threeMonthReturn(),
                "近6月收益率：" + report.sixMonthReturn(),
                "近1年收益率：" + report.oneYearReturn(),
                "区间年化收益率：" + report.annualizedReturn(),
                "下行波动率：" + report.downsideVolatility(),
                "收益回撤比：" + report.returnDrawdownRatio(),
                "样本边界：" + report.sampleBoundary(),
                "统计日期：" + report.statisticDate(),
                "分析模式：" + report.analysisMode(),
                "Agent 解读：" + report.agentNarrative());
    }

    private String renderRiskReport(FundStructuredReports.RiskReport report) {
        return String.join("\n",
                "风险等级：" + report.riskLevel(),
                "最大回撤：" + report.maxDrawdown(),
                "年化波动率：" + report.volatility(),
                "分析模式：" + report.analysisMode(),
                "Agent 解读：" + report.agentNarrative(),
                "风险提示：" + String.join("；", report.riskItems()));
    }

    private String renderPeerReport(FundStructuredReports.PeerComparisonReport report) {
        return "对比池：" + report.peerUniverse()
                + "\n" + String.join("\n", report.peers())
                + "\n分析模式：" + report.analysisMode()
                + "\nAgent 解读：" + report.agentNarrative()
                + "\n边界：" + report.boundary();
    }

    private String renderFactorReport(FundStructuredReports.FactorDiscussionReport report) {
        return "优势因素：\n- " + String.join("\n- ", report.positiveFactors())
                + "\n\n风险因素：\n- " + String.join("\n- ", report.riskFactors())
                + "\n\n分析模式：" + report.analysisMode()
                + "\nAgent 解读：" + report.agentNarrative()
                + "\n\n讨论结论：" + report.conclusion();
    }

    private String renderComplianceReport(FundStructuredReports.ComplianceReport report) {
        return "是否触发限制：" + (report.restricted() ? "是" : "否")
                + "\n" + report.message()
                + "\n" + report.disclaimer();
    }

    private String renderAnswerReport(FundStructuredReports.AnswerReport report) {
        return "回答模式：" + report.answerMode()
                + "\n边界：" + report.boundary()
                + "\n\n" + report.answer();
    }

    private String analysisMode(FundWorkflowContext context, String stageCode) {
        return agentScopeModelInvoker.analysisMode(stageCode, context.getState().getThinkingMode());
    }

    private String buildAgentNarrative(FundWorkflowContext workflowContext,
                                       String stageCode,
                                       String agentName,
                                       String instruction,
                                       String context,
                                       String fallback) {
        return agentScopeModelInvoker.generateNarrative(
                workflowContext.getState().getTaskId(),
                stageCode,
                agentName,
                instruction,
                context,
                fallback,
                workflowContext.getState().getThinkingMode()
        );
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean isNegative(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) < 0;
    }

    private String formatPercent(BigDecimal value) {
        return value == null ? "暂无" : value.stripTrailingZeros().toPlainString() + "%";
    }

    private String formatDecimal(BigDecimal value) {
        return value == null ? "暂无" : value.stripTrailingZeros().toPlainString();
    }
}
