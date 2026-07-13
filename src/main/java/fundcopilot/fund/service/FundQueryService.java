package fundcopilot.fund.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import fundcopilot.compliance.ComplianceService;
import fundcopilot.common.FundCacheService;
import fundcopilot.fund.entity.AlipayFundPoolDO;
import fundcopilot.fund.entity.FundMetricSnapshotDO;
import fundcopilot.fund.entity.FundNavDO;
import fundcopilot.fund.entity.FundProfileDO;
import fundcopilot.fund.mapper.AlipayFundPoolMapper;
import fundcopilot.fund.mapper.FundMetricSnapshotMapper;
import fundcopilot.fund.mapper.FundNavMapper;
import fundcopilot.fund.mapper.FundProfileMapper;
import fundcopilot.fund.vo.FundAnalysisResultVO;
import fundcopilot.fund.vo.FundCompareColumnVO;
import fundcopilot.fund.vo.FundCompareResultVO;
import fundcopilot.fund.vo.FundCompareRowVO;
import fundcopilot.fund.vo.FundDetailVO;
import fundcopilot.fund.vo.FundMetricVO;
import fundcopilot.fund.vo.FundNavPointVO;
import fundcopilot.fund.vo.FundSearchItemVO;
import fundcopilot.marketdata.FundDataProvider;
import fundcopilot.marketdata.MarketDataDtos.MarketFundSnapshot;
import fundcopilot.marketdata.MarketDataDtos.MarketFundSearchItem;
import fundcopilot.marketdata.MarketDataDtos.MarketNavPoint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@Service
public class FundQueryService {
    private static final int DEFAULT_NAV_LIMIT = 120;
    private static final int MAX_COMPARE_FUND_SIZE = 6;
    private static final String DATA_SOURCE_NAME = "东方财富/天天基金公开数据";
    private static final String HIGH_RISK_LEVEL = "高风险";
    private static final Duration SEARCH_CACHE_TTL = Duration.ofMinutes(10);
    private static final Duration FUND_CACHE_TTL = Duration.ofMinutes(15);
    private static final Duration COMPARE_CACHE_TTL = Duration.ofMinutes(10);
    private static final String CACHE_PREFIX_SEARCH = "fund:search:";
    private static final String CACHE_PREFIX_DETAIL = "fund:detail:";
    private static final String CACHE_PREFIX_NAV = "fund:nav:";
    private static final String CACHE_PREFIX_ANALYSIS = "fund:analysis:";
    private static final String CACHE_PREFIX_COMPARE = "fund:compare:";

    private final FundProfileMapper fundProfileMapper;
    private final FundNavMapper fundNavMapper;
    private final FundMetricSnapshotMapper fundMetricSnapshotMapper;
    private final AlipayFundPoolMapper alipayFundPoolMapper;
    private final FundDataProvider fundDataProvider;
    private final FundMetricCalculator fundMetricCalculator;
    private final FundCacheService fundCacheService;

    public FundQueryService(FundProfileMapper fundProfileMapper,
                            FundNavMapper fundNavMapper,
                            FundMetricSnapshotMapper fundMetricSnapshotMapper,
                            AlipayFundPoolMapper alipayFundPoolMapper,
                            FundDataProvider fundDataProvider,
                            FundMetricCalculator fundMetricCalculator,
                            FundCacheService fundCacheService) {
        this.fundProfileMapper = fundProfileMapper;
        this.fundNavMapper = fundNavMapper;
        this.fundMetricSnapshotMapper = fundMetricSnapshotMapper;
        this.alipayFundPoolMapper = alipayFundPoolMapper;
        this.fundDataProvider = fundDataProvider;
        this.fundMetricCalculator = fundMetricCalculator;
        this.fundCacheService = fundCacheService;
    }

    public List<FundSearchItemVO> search(String keyword) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        String cacheKey = CACHE_PREFIX_SEARCH + cacheToken(safeKeyword);
        return fundCacheService.get(cacheKey, new TypeReference<List<FundSearchItemVO>>() {
                })
                .orElseGet(() -> {
                    List<FundSearchItemVO> result = doSearch(safeKeyword);
                    fundCacheService.set(cacheKey, result, SEARCH_CACHE_TTL);
                    return result;
                });
    }

    private List<FundSearchItemVO> doSearch(String safeKeyword) {
        LambdaQueryWrapper<FundProfileDO> wrapper = new LambdaQueryWrapper<>();
        if (!safeKeyword.isBlank()) {
            wrapper.and(condition -> condition
                    .like(FundProfileDO::getFundCode, safeKeyword)
                    .or()
                    .like(FundProfileDO::getFundName, safeKeyword));
        }
        wrapper.orderByAsc(FundProfileDO::getFundCode).last("limit 20");

        Map<String, FundSearchItemVO> resultMap = new LinkedHashMap<>();
        fundProfileMapper.selectList(wrapper)
                .stream()
                .map(profile -> new FundSearchItemVO(
                        profile.getFundCode(),
                        profile.getFundName(),
                        profile.getFundType(),
                        profile.getRiskLevel(),
                        findAlipayTag(profile.getFundCode())
                ))
                .forEach(item -> resultMap.put(item.fundCode(), item));

        if (!safeKeyword.isBlank()) {
            fundDataProvider.searchFunds(safeKeyword)
                    .stream()
                    .map(this::toSearchItemVO)
                    .forEach(item -> resultMap.putIfAbsent(item.fundCode(), item));
        }

        return resultMap.values().stream().limit(20).toList();
    }

    public FundDetailVO getDetail(String fundCode) {
        String safeFundCode = normalizeFundCode(fundCode);
        String cacheKey = CACHE_PREFIX_DETAIL + safeFundCode;
        return fundCacheService.get(cacheKey, new TypeReference<FundDetailVO>() {
                })
                .orElseGet(() -> {
                    FundDetailVO detailVO = doGetDetail(safeFundCode);
                    fundCacheService.set(cacheKey, detailVO, FUND_CACHE_TTL);
                    return detailVO;
                });
    }

    private FundDetailVO doGetDetail(String fundCode) {
        FundProfileDO profileDO = findProfile(fundCode);
        if (profileDO == null) {
            syncFund(fundCode);
            profileDO = findProfile(fundCode);
        }
        if (profileDO == null) {
            throw new IllegalArgumentException("基金不存在: " + fundCode);
        }
        return toDetailVO(profileDO);
    }

    public List<FundNavPointVO> getNavPoints(String fundCode, Integer limit) {
        String safeFundCode = normalizeFundCode(fundCode);
        int safeLimit = limit == null || limit <= 0 ? DEFAULT_NAV_LIMIT : Math.min(limit, DEFAULT_NAV_LIMIT);
        String cacheKey = CACHE_PREFIX_NAV + safeFundCode + ":" + safeLimit;
        return fundCacheService.get(cacheKey, new TypeReference<List<FundNavPointVO>>() {
                })
                .orElseGet(() -> {
                    List<FundNavPointVO> navPoints = doGetNavPoints(safeFundCode, safeLimit);
                    fundCacheService.set(cacheKey, navPoints, FUND_CACHE_TTL);
                    return navPoints;
                });
    }

    private List<FundNavPointVO> doGetNavPoints(String fundCode, int safeLimit) {
        LambdaQueryWrapper<FundNavDO> wrapper = new LambdaQueryWrapper<FundNavDO>()
                .eq(FundNavDO::getFundCode, fundCode)
                .orderByDesc(FundNavDO::getNavDate)
                .last("limit " + safeLimit);

        return fundNavMapper.selectList(wrapper)
                .stream()
                .sorted(Comparator.comparing(FundNavDO::getNavDate))
                .map(this::toNavPointVO)
                .toList();
    }

    public FundAnalysisResultVO analyze(String fundCode) {
        String safeFundCode = normalizeFundCode(fundCode);
        String cacheKey = CACHE_PREFIX_ANALYSIS + safeFundCode;
        return fundCacheService.get(cacheKey, new TypeReference<FundAnalysisResultVO>() {
                })
                .orElseGet(() -> {
                    FundAnalysisResultVO resultVO = doAnalyze(safeFundCode);
                    fundCacheService.set(cacheKey, resultVO, FUND_CACHE_TTL);
                    return resultVO;
                });
    }

    private FundAnalysisResultVO doAnalyze(String fundCode) {
        FundDetailVO detail = getDetail(fundCode);
        List<FundNavDO> navList = loadNavList(fundCode, DEFAULT_NAV_LIMIT);
        FundMetricSnapshotDO metricSnapshotDO = findMetric(fundCode);
        if (metricSnapshotDO == null && !navList.isEmpty()) {
            metricSnapshotDO = fundMetricCalculator.calculate(fundCode, navList);
        }

        FundMetricVO metricVO = toMetricVO(metricSnapshotDO);
        List<FundNavPointVO> navPoints = navList.stream()
                .sorted(Comparator.comparing(FundNavDO::getNavDate))
                .map(this::toNavPointVO)
                .toList();

        return new FundAnalysisResultVO(
                detail,
                metricVO,
                navPoints,
                buildHighlights(detail, metricVO),
                buildRisks(detail, metricVO),
                ComplianceService.STANDARD_DISCLAIMER,
                DATA_SOURCE_NAME,
                LocalDateTime.now()
        );
    }

    public FundCompareResultVO compare(String codesText) {
        List<String> fundCodes = parseCompareFundCodes(codesText);
        String cacheKey = CACHE_PREFIX_COMPARE + cacheToken(String.join(",", fundCodes));
        return fundCacheService.get(cacheKey, new TypeReference<FundCompareResultVO>() {
                })
                .orElseGet(() -> {
                    FundCompareResultVO resultVO = doCompare(fundCodes);
                    fundCacheService.set(cacheKey, resultVO, COMPARE_CACHE_TTL);
                    return resultVO;
                });
    }

    @Transactional(rollbackFor = Exception.class)
    public FundDetailVO syncFund(String fundCode) {
        String safeFundCode = normalizeFundCode(fundCode);
        MarketFundSnapshot snapshot = fundDataProvider.fetchSnapshot(safeFundCode);
        FundProfileDO profileDO = upsertProfile(snapshot);
        upsertNavPoints(safeFundCode, snapshot.navPoints());

        List<FundNavDO> navList = loadNavList(safeFundCode, DEFAULT_NAV_LIMIT);
        if (!navList.isEmpty()) {
            upsertMetric(fundMetricCalculator.calculate(safeFundCode, navList));
        }

        evictFundCache(safeFundCode);
        return toDetailVO(profileDO);
    }

    public List<FundSearchItemVO> listAlipayFundPool() {
        return alipayFundPoolMapper.selectList(new LambdaQueryWrapper<AlipayFundPoolDO>()
                        .eq(AlipayFundPoolDO::getFocus, Boolean.TRUE)
                        .orderByAsc(AlipayFundPoolDO::getFundCode))
                .stream()
                .map(pool -> {
                    FundProfileDO profileDO = findProfile(pool.getFundCode());
                    return new FundSearchItemVO(
                            pool.getFundCode(),
                            profileDO == null ? pool.getFundCode() : profileDO.getFundName(),
                            profileDO == null ? null : profileDO.getFundType(),
                            profileDO == null ? null : profileDO.getRiskLevel(),
                            pool.getDisplayTag()
                    );
                })
                .toList();
    }

    private FundProfileDO upsertProfile(MarketFundSnapshot snapshot) {
        FundProfileDO exists = findProfile(snapshot.fundCode());
        FundProfileDO profileDO = exists == null ? new FundProfileDO() : exists;
        profileDO.setFundCode(snapshot.fundCode());
        profileDO.setFundName(snapshot.fundName());
        profileDO.setFundType(snapshot.fundType());
        profileDO.setFundCompany(snapshot.fundCompany());
        profileDO.setFundManager(snapshot.fundManager());
        profileDO.setRiskLevel(snapshot.riskLevel());
        profileDO.setPurchaseStatus(snapshot.purchaseStatus());
        profileDO.setRedeemStatus(snapshot.redeemStatus());
        profileDO.setLatestNav(snapshot.latestNav());
        profileDO.setLatestNavDate(snapshot.latestNavDate());
        profileDO.setSourceUrl(snapshot.sourceUrl());
        profileDO.setStale(snapshot.stale());
        profileDO.setLastSyncAt(snapshot.syncedAt());

        if (exists == null) {
            fundProfileMapper.insert(profileDO);
        } else {
            fundProfileMapper.updateById(profileDO);
        }
        return profileDO;
    }

    private void upsertNavPoints(String fundCode, List<MarketNavPoint> navPoints) {
        for (MarketNavPoint point : navPoints) {
            FundNavDO exists = fundNavMapper.selectOne(new LambdaQueryWrapper<FundNavDO>()
                    .eq(FundNavDO::getFundCode, fundCode)
                    .eq(FundNavDO::getNavDate, point.navDate()));
            FundNavDO navDO = exists == null ? new FundNavDO() : exists;
            navDO.setFundCode(fundCode);
            navDO.setNavDate(point.navDate());
            navDO.setUnitNav(point.unitNav());
            navDO.setAccumulatedNav(point.accumulatedNav());
            navDO.setDailyGrowthRate(point.dailyGrowthRate());
            navDO.setSourceUrl(point.sourceUrl());
            if (exists == null) {
                fundNavMapper.insert(navDO);
            } else {
                fundNavMapper.updateById(navDO);
            }
        }
    }

    private void upsertMetric(FundMetricSnapshotDO metricSnapshotDO) {
        FundMetricSnapshotDO exists = findMetric(metricSnapshotDO.getFundCode());
        if (exists == null) {
            fundMetricSnapshotMapper.insert(metricSnapshotDO);
            return;
        }

        metricSnapshotDO.setId(exists.getId());
        fundMetricSnapshotMapper.updateById(metricSnapshotDO);
    }

    private FundProfileDO findProfile(String fundCode) {
        if (fundCode == null || fundCode.isBlank()) {
            return null;
        }
        return fundProfileMapper.selectOne(new LambdaQueryWrapper<FundProfileDO>()
                .eq(FundProfileDO::getFundCode, fundCode.trim()));
    }

    private FundMetricSnapshotDO findMetric(String fundCode) {
        return fundMetricSnapshotMapper.selectOne(new LambdaQueryWrapper<FundMetricSnapshotDO>()
                .eq(FundMetricSnapshotDO::getFundCode, fundCode));
    }

    private List<FundNavDO> loadNavList(String fundCode, int limit) {
        return fundNavMapper.selectList(new LambdaQueryWrapper<FundNavDO>()
                        .eq(FundNavDO::getFundCode, fundCode)
                        .orderByDesc(FundNavDO::getNavDate)
                        .last("limit " + limit))
                .stream()
                .sorted(Comparator.comparing(FundNavDO::getNavDate))
                .toList();
    }

    private String findAlipayTag(String fundCode) {
        AlipayFundPoolDO poolDO = alipayFundPoolMapper.selectOne(new LambdaQueryWrapper<AlipayFundPoolDO>()
                .eq(AlipayFundPoolDO::getFundCode, fundCode));
        return poolDO == null ? null : poolDO.getDisplayTag();
    }

    private FundDetailVO toDetailVO(FundProfileDO profileDO) {
        return new FundDetailVO(
                profileDO.getFundCode(),
                profileDO.getFundName(),
                profileDO.getFundType(),
                profileDO.getFundCompany(),
                profileDO.getFundManager(),
                profileDO.getRiskLevel(),
                profileDO.getPurchaseStatus(),
                profileDO.getRedeemStatus(),
                profileDO.getLatestNav(),
                profileDO.getLatestNavDate(),
                profileDO.getSourceUrl(),
                profileDO.getStale(),
                profileDO.getLastSyncAt()
        );
    }

    private FundNavPointVO toNavPointVO(FundNavDO navDO) {
        return new FundNavPointVO(
                navDO.getNavDate(),
                navDO.getUnitNav(),
                navDO.getAccumulatedNav(),
                navDO.getDailyGrowthRate()
        );
    }

    private FundMetricVO toMetricVO(FundMetricSnapshotDO metricSnapshotDO) {
        if (metricSnapshotDO == null) {
            return new FundMetricVO(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    null
            );
        }
        return new FundMetricVO(
                metricSnapshotDO.getOneMonthReturn(),
                metricSnapshotDO.getThreeMonthReturn(),
                metricSnapshotDO.getSixMonthReturn(),
                metricSnapshotDO.getOneYearReturn(),
                metricSnapshotDO.getMaxDrawdown(),
                metricSnapshotDO.getVolatility(),
                metricSnapshotDO.getStatisticDate()
        );
    }

    private List<String> buildHighlights(FundDetailVO detail, FundMetricVO metrics) {
        return List.of(
                "基金类型：" + Objects.toString(detail.fundType(), "未知"),
                "最新净值日期：" + Objects.toString(detail.latestNavDate(), "暂无"),
                "近一年收益率：" + formatPercent(metrics.oneYearReturn()),
                "申购状态：" + Objects.toString(detail.purchaseStatus(), "以平台确认为准")
        );
    }

    private List<String> buildRisks(FundDetailVO detail, FundMetricVO metrics) {
        List<String> baseRisks = new ArrayList<>();
        baseRisks.add("最大回撤：" + formatPercent(metrics.maxDrawdown()) + "，回撤越大代表历史波动压力越高。");
        baseRisks.add("年化波动率：" + formatPercent(metrics.volatility()) + "，仅反映历史净值波动。");
        if (detail.riskLevel() != null && detail.riskLevel().contains(HIGH_RISK_LEVEL)) {
            baseRisks.add("该基金风险等级较高，需结合自身风险承受能力判断。");
        }
        return baseRisks;
    }

    private FundCompareResultVO doCompare(List<String> fundCodes) {
        List<FundAnalysisResultVO> analyses = fundCodes.stream()
                .map(this::analyze)
                .toList();
        List<FundCompareColumnVO> columns = analyses.stream()
                .map(analysis -> new FundCompareColumnVO(
                        analysis.detail().fundCode(),
                        analysis.detail().fundName(),
                        analysis.detail().fundType(),
                        analysis.detail().riskLevel()
                ))
                .toList();
        List<FundCompareRowVO> rows = List.of(
                compareRow("基金类型", analyses, analysis -> fallbackText(analysis.detail().fundType())),
                compareRow("基金公司", analyses, analysis -> fallbackText(analysis.detail().fundCompany())),
                compareRow("基金经理", analyses, analysis -> fallbackText(analysis.detail().fundManager())),
                compareRow("风险等级", analyses, analysis -> fallbackText(analysis.detail().riskLevel())),
                compareRow("最新净值", analyses, analysis -> formatDecimal(analysis.detail().latestNav())),
                compareRow("净值日期", analyses, analysis -> Objects.toString(analysis.detail().latestNavDate(), "暂无")),
                compareRow("申购/赎回", analyses, analysis -> fallbackText(analysis.detail().purchaseStatus())
                        + " / " + fallbackText(analysis.detail().redeemStatus())),
                compareRow("近1月收益率", analyses, analysis -> formatPercent(analysis.metrics().oneMonthReturn())),
                compareRow("近3月收益率", analyses, analysis -> formatPercent(analysis.metrics().threeMonthReturn())),
                compareRow("近6月收益率", analyses, analysis -> formatPercent(analysis.metrics().sixMonthReturn())),
                compareRow("近1年收益率", analyses, analysis -> formatPercent(analysis.metrics().oneYearReturn())),
                compareRow("最大回撤", analyses, analysis -> formatPercent(analysis.metrics().maxDrawdown())),
                compareRow("年化波动率", analyses, analysis -> formatPercent(analysis.metrics().volatility())),
                compareRow("数据来源", analyses, FundAnalysisResultVO::dataSource)
        );
        return new FundCompareResultVO(columns, rows, buildCompareSummary(analyses), LocalDateTime.now());
    }

    private FundCompareRowVO compareRow(String dimension,
                                        List<FundAnalysisResultVO> analyses,
                                        Function<FundAnalysisResultVO, String> valueFunction) {
        return new FundCompareRowVO(
                dimension,
                analyses.stream().map(valueFunction).toList()
        );
    }

    private String buildCompareSummary(List<FundAnalysisResultVO> analyses) {
        String returnLeader = findMetricLeader(analyses, analysis -> analysis.metrics().oneYearReturn());
        String drawdownLeader = findMetricLeader(analyses, analysis -> analysis.metrics().maxDrawdown());
        String volatilityLeader = findMetricLeader(analyses, analysis -> analysis.metrics().volatility());
        return "小结："
                + returnLeader + "近一年收益率相对更高；"
                + drawdownLeader + "最大回撤相对更低；"
                + volatilityLeader + "年化波动率相对更高。"
                + "以上仅用于历史数据横向比较，不构成投资建议。";
    }

    private String findMetricLeader(List<FundAnalysisResultVO> analyses,
                                    Function<FundAnalysisResultVO, BigDecimal> metricFunction) {
        return analyses.stream()
                .filter(analysis -> metricFunction.apply(analysis) != null)
                .max(Comparator.comparing(metricFunction))
                .map(analysis -> analysis.detail().fundName() + "（" + analysis.detail().fundCode() + "）")
                .orElse("暂无基金");
    }

    private List<String> parseCompareFundCodes(String codesText) {
        if (codesText == null || codesText.isBlank()) {
            throw new IllegalArgumentException("基金代码不能为空");
        }
        Map<String, String> fundCodeMap = new LinkedHashMap<>();
        for (String rawCode : codesText.split("[\\s,，;；]+")) {
            if (rawCode.isBlank()) {
                continue;
            }
            String fundCode = normalizeFundCode(rawCode);
            fundCodeMap.putIfAbsent(fundCode, fundCode);
        }
        if (fundCodeMap.isEmpty()) {
            throw new IllegalArgumentException("基金代码不能为空");
        }
        if (fundCodeMap.size() > MAX_COMPARE_FUND_SIZE) {
            throw new IllegalArgumentException("最多支持同时对比 " + MAX_COMPARE_FUND_SIZE + " 只基金");
        }
        return new ArrayList<>(fundCodeMap.keySet());
    }

    private FundSearchItemVO toSearchItemVO(MarketFundSearchItem searchItem) {
        return new FundSearchItemVO(
                searchItem.fundCode(),
                searchItem.fundName(),
                searchItem.fundType(),
                null,
                "东方财富"
        );
    }

    private String normalizeFundCode(String fundCode) {
        if (fundCode == null || fundCode.isBlank()) {
            throw new IllegalArgumentException("基金代码不能为空");
        }
        String safeFundCode = fundCode.trim();
        if (!safeFundCode.matches("\\d{6}")) {
            throw new IllegalArgumentException("基金代码必须是6位数字: " + fundCode);
        }
        return safeFundCode;
    }

    private void evictFundCache(String fundCode) {
        fundCacheService.delete(List.of(
                CACHE_PREFIX_DETAIL + fundCode,
                CACHE_PREFIX_ANALYSIS + fundCode,
                CACHE_PREFIX_NAV + fundCode + ":" + DEFAULT_NAV_LIMIT,
                CACHE_PREFIX_SEARCH + cacheToken(fundCode)
        ));
    }

    private String cacheToken(String value) {
        return URLEncoder.encode(Objects.toString(value, ""), StandardCharsets.UTF_8);
    }

    private String fallbackText(String value) {
        return value == null || value.isBlank() ? "暂无" : value;
    }

    private String formatPercent(BigDecimal value) {
        return value == null ? "暂无" : value.stripTrailingZeros().toPlainString() + "%";
    }

    private String formatDecimal(BigDecimal value) {
        return value == null ? "暂无" : value.stripTrailingZeros().toPlainString();
    }
}
