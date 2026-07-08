package fundcopilot.fund.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fundcopilot.compliance.ComplianceService;
import fundcopilot.fund.entity.AlipayFundPoolDO;
import fundcopilot.fund.entity.FundMetricSnapshotDO;
import fundcopilot.fund.entity.FundNavDO;
import fundcopilot.fund.entity.FundProfileDO;
import fundcopilot.fund.mapper.AlipayFundPoolMapper;
import fundcopilot.fund.mapper.FundMetricSnapshotMapper;
import fundcopilot.fund.mapper.FundNavMapper;
import fundcopilot.fund.mapper.FundProfileMapper;
import fundcopilot.fund.vo.FundAnalysisResultVO;
import fundcopilot.fund.vo.FundDetailVO;
import fundcopilot.fund.vo.FundMetricVO;
import fundcopilot.fund.vo.FundNavPointVO;
import fundcopilot.fund.vo.FundSearchItemVO;
import fundcopilot.marketdata.FundDataProvider;
import fundcopilot.marketdata.MarketDataDtos.MarketFundSnapshot;
import fundcopilot.marketdata.MarketDataDtos.MarketNavPoint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class FundQueryService {
    private static final int DEFAULT_NAV_LIMIT = 120;
    private static final String DATA_SOURCE_NAME = "东方财富/天天基金公开数据";
    private static final String HIGH_RISK_LEVEL = "高风险";

    private final FundProfileMapper fundProfileMapper;
    private final FundNavMapper fundNavMapper;
    private final FundMetricSnapshotMapper fundMetricSnapshotMapper;
    private final AlipayFundPoolMapper alipayFundPoolMapper;
    private final FundDataProvider fundDataProvider;
    private final FundMetricCalculator fundMetricCalculator;

    public FundQueryService(FundProfileMapper fundProfileMapper,
                            FundNavMapper fundNavMapper,
                            FundMetricSnapshotMapper fundMetricSnapshotMapper,
                            AlipayFundPoolMapper alipayFundPoolMapper,
                            FundDataProvider fundDataProvider,
                            FundMetricCalculator fundMetricCalculator) {
        this.fundProfileMapper = fundProfileMapper;
        this.fundNavMapper = fundNavMapper;
        this.fundMetricSnapshotMapper = fundMetricSnapshotMapper;
        this.alipayFundPoolMapper = alipayFundPoolMapper;
        this.fundDataProvider = fundDataProvider;
        this.fundMetricCalculator = fundMetricCalculator;
    }

    public List<FundSearchItemVO> search(String keyword) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        LambdaQueryWrapper<FundProfileDO> wrapper = new LambdaQueryWrapper<>();
        if (!safeKeyword.isBlank()) {
            wrapper.and(condition -> condition
                    .like(FundProfileDO::getFundCode, safeKeyword)
                    .or()
                    .like(FundProfileDO::getFundName, safeKeyword));
        }
        wrapper.orderByAsc(FundProfileDO::getFundCode).last("limit 20");

        return fundProfileMapper.selectList(wrapper)
                .stream()
                .map(profile -> new FundSearchItemVO(
                        profile.getFundCode(),
                        profile.getFundName(),
                        profile.getFundType(),
                        profile.getRiskLevel(),
                        findAlipayTag(profile.getFundCode())
                ))
                .toList();
    }

    public FundDetailVO getDetail(String fundCode) {
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
        int safeLimit = limit == null || limit <= 0 ? DEFAULT_NAV_LIMIT : Math.min(limit, DEFAULT_NAV_LIMIT);
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

    @Transactional(rollbackFor = Exception.class)
    public FundDetailVO syncFund(String fundCode) {
        MarketFundSnapshot snapshot = fundDataProvider.fetchSnapshot(fundCode);
        FundProfileDO profileDO = upsertProfile(snapshot);
        upsertNavPoints(fundCode, snapshot.navPoints());

        List<FundNavDO> navList = loadNavList(fundCode, DEFAULT_NAV_LIMIT);
        if (!navList.isEmpty()) {
            upsertMetric(fundMetricCalculator.calculate(fundCode, navList));
        }

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

    private String formatPercent(BigDecimal value) {
        return value == null ? "暂无" : value.stripTrailingZeros().toPlainString() + "%";
    }
}
