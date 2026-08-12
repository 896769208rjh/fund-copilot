package fundcopilot.observation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import fundcopilot.common.FundCacheService;
import fundcopilot.compliance.ComplianceService;
import fundcopilot.observation.entity.FundMasterDO;
import fundcopilot.observation.entity.FundMetricDailyDO;
import fundcopilot.observation.entity.FundRankDailyDO;
import fundcopilot.observation.entity.FundRankMembershipDO;
import fundcopilot.observation.entity.FundUniverseDO;
import fundcopilot.observation.mapper.FundMasterMapper;
import fundcopilot.observation.mapper.FundMetricDailyMapper;
import fundcopilot.observation.mapper.FundRankDailyMapper;
import fundcopilot.observation.mapper.FundRankMembershipMapper;
import fundcopilot.observation.mapper.FundUniverseMapper;
import fundcopilot.observation.model.FundCategory;
import fundcopilot.observation.vo.ObservationBoardVO;
import fundcopilot.observation.vo.ObservationCategoryVO;
import fundcopilot.observation.vo.ObservationFundVO;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ObservationBoardService {
    private static final String CACHE_KEY = "fund:observation:board";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final String METHODOLOGY = "按基金规模建立四类主流基金池；收益、回撤、波动、收益回撤比和数据质量在同类内进行确定性百分位评分；连续满足或不满足 3 天后才进入或退出公开榜单。";

    private final FundRankDailyMapper rankDailyMapper;
    private final FundMetricDailyMapper metricDailyMapper;
    private final FundMasterMapper masterMapper;
    private final FundRankMembershipMapper membershipMapper;
    private final FundUniverseMapper universeMapper;
    private final FundCacheService cacheService;

    public ObservationBoardService(FundRankDailyMapper rankDailyMapper,
                                   FundMetricDailyMapper metricDailyMapper,
                                   FundMasterMapper masterMapper,
                                   FundRankMembershipMapper membershipMapper,
                                   FundUniverseMapper universeMapper,
                                   FundCacheService cacheService) {
        this.rankDailyMapper = rankDailyMapper;
        this.metricDailyMapper = metricDailyMapper;
        this.masterMapper = masterMapper;
        this.membershipMapper = membershipMapper;
        this.universeMapper = universeMapper;
        this.cacheService = cacheService;
    }

    public ObservationBoardVO getBoard() {
        return cacheService.get(CACHE_KEY, new TypeReference<ObservationBoardVO>() { })
                .orElseGet(() -> {
                    ObservationBoardVO board = loadBoard();
                    cacheService.set(CACHE_KEY, board, CACHE_TTL);
                    return board;
                });
    }

    public ObservationCategoryVO getCategory(FundCategory category) {
        return getBoard().categories().stream()
                .filter(item -> item.category().equals(category.name()))
                .findFirst()
                .orElse(new ObservationCategoryVO(category.name(), category.getDisplayName(), null, 0, List.of()));
    }

    public void evictCache() {
        cacheService.delete(List.of(CACHE_KEY));
    }

    private ObservationBoardVO loadBoard() {
        List<ObservationCategoryVO> categories = new ArrayList<>();
        for (FundCategory category : FundCategory.values()) {
            categories.add(loadCategory(category));
        }
        return new ObservationBoardVO(categories, METHODOLOGY,
                ComplianceService.STANDARD_DISCLAIMER, LocalDateTime.now());
    }

    private ObservationCategoryVO loadCategory(FundCategory category) {
        FundRankDailyDO latest = rankDailyMapper.selectOne(new LambdaQueryWrapper<FundRankDailyDO>()
                .eq(FundRankDailyDO::getFundCategory, category.name())
                .eq(FundRankDailyDO::getVisible, Boolean.TRUE)
                .orderByDesc(FundRankDailyDO::getRankDate)
                .orderByAsc(FundRankDailyDO::getPublishedRank)
                .last("limit 1"));
        int universeSize = Math.toIntExact(universeMapper.selectCount(new LambdaQueryWrapper<FundUniverseDO>()
                .eq(FundUniverseDO::getFundCategory, category.name())
                .eq(FundUniverseDO::getActive, Boolean.TRUE)));
        if (latest == null) {
            return new ObservationCategoryVO(category.name(), category.getDisplayName(), null,
                    universeSize, List.of());
        }

        LocalDate rankDate = latest.getRankDate();
        List<FundRankDailyDO> ranks = rankDailyMapper.selectList(new LambdaQueryWrapper<FundRankDailyDO>()
                .eq(FundRankDailyDO::getFundCategory, category.name())
                .eq(FundRankDailyDO::getRankDate, rankDate)
                .eq(FundRankDailyDO::getVisible, Boolean.TRUE)
                .orderByAsc(FundRankDailyDO::getPublishedRank));
        List<Long> masterIds = ranks.stream().map(FundRankDailyDO::getMasterId).toList();
        Map<Long, FundMasterDO> masters = masterMapper.selectBatchIds(masterIds).stream()
                .collect(Collectors.toMap(FundMasterDO::getId, Function.identity()));
        Map<Long, FundMetricDailyDO> metrics = metricDailyMapper.selectList(
                        new LambdaQueryWrapper<FundMetricDailyDO>()
                                .in(FundMetricDailyDO::getMasterId, masterIds)
                                .eq(FundMetricDailyDO::getMetricDate, rankDate))
                .stream().collect(Collectors.toMap(FundMetricDailyDO::getMasterId, Function.identity()));
        Map<Long, FundRankMembershipDO> memberships = membershipMapper.selectList(
                        new LambdaQueryWrapper<FundRankMembershipDO>()
                                .in(FundRankMembershipDO::getMasterId, masterIds)
                                .eq(FundRankMembershipDO::getFundCategory, category.name()))
                .stream().collect(Collectors.toMap(FundRankMembershipDO::getMasterId, Function.identity()));

        List<ObservationFundVO> funds = ranks.stream()
                .sorted(Comparator.comparing(FundRankDailyDO::getPublishedRank))
                .map(rank -> toFund(rank, masters.get(rank.getMasterId()), metrics.get(rank.getMasterId()),
                        memberships.get(rank.getMasterId()), rankDate))
                .filter(fund -> fund != null)
                .toList();
        return new ObservationCategoryVO(category.name(), category.getDisplayName(), rankDate,
                universeSize, funds);
    }

    private ObservationFundVO toFund(FundRankDailyDO rank,
                                     FundMasterDO master,
                                     FundMetricDailyDO metric,
                                     FundRankMembershipDO membership,
                                     LocalDate rankDate) {
        if (master == null || metric == null) {
            return null;
        }
        String status = membership != null && membership.getEnteredAt() != null
                && membership.getEnteredAt().toLocalDate().equals(rankDate) ? "NEW" : "STABLE";
        return new ObservationFundVO(rank.getPublishedRank(), master.getPrimaryFundCode(),
                master.getFundName(), master.getLatestScale(), metric.getOneMonthReturn(),
                metric.getThreeMonthReturn(), metric.getSixMonthReturn(), metric.getOneYearReturn(),
                metric.getMaxDrawdown(), metric.getVolatility(), rank.getTotalScore(), status,
                metric.getSourceMetricDate());
    }
}
