package fundcopilot.observation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fundcopilot.marketdata.FundDataProvider;
import fundcopilot.marketdata.MarketDataDtos.MarketFundUniverseItem;
import fundcopilot.observation.config.ObservationProperties;
import fundcopilot.observation.entity.FundMasterDO;
import fundcopilot.observation.entity.FundShareClassDO;
import fundcopilot.observation.entity.FundUniverseDO;
import fundcopilot.observation.mapper.FundMasterMapper;
import fundcopilot.observation.mapper.FundShareClassMapper;
import fundcopilot.observation.mapper.FundUniverseMapper;
import fundcopilot.observation.model.FundCategory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FundUniverseService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FundUniverseService.class);
    private final FundDataProvider fundDataProvider;
    private final ObservationProperties properties;
    private final FundMasterMapper masterMapper;
    private final FundShareClassMapper shareClassMapper;
    private final FundUniverseMapper universeMapper;

    public FundUniverseService(FundDataProvider fundDataProvider,
                               ObservationProperties properties,
                               FundMasterMapper masterMapper,
                               FundShareClassMapper shareClassMapper,
                               FundUniverseMapper universeMapper) {
        this.fundDataProvider = fundDataProvider;
        this.properties = properties;
        this.masterMapper = masterMapper;
        this.shareClassMapper = shareClassMapper;
        this.universeMapper = universeMapper;
    }

    public UniverseRefreshResult refreshAllCategories() {
        int refreshedCategories = 0;
        int failedCategories = 0;
        for (FundCategory category : FundCategory.values()) {
            try {
                List<MarketFundUniverseItem> candidates = fundDataProvider.fetchFundsByScale(
                        category.getEastmoneyFundType(), properties.getUniverseCandidateSize());
                replaceCategoryUniverse(category, candidates);
                refreshedCategories++;
            } catch (RuntimeException exception) {
                failedCategories++;
                LOGGER.warn("Refresh category universe failed, retaining previous universe, category={}",
                        category, exception);
            }
        }
        int activeCount = listActiveUniverse().size();
        if (activeCount == 0) {
                throw new IllegalStateException("四类基金规模池均同步失败，且本地没有可保留的基金池");
        }
        return new UniverseRefreshResult(activeCount, refreshedCategories, failedCategories);
    }

    @Transactional(rollbackFor = Exception.class)
    public int replaceCategoryUniverse(FundCategory category, List<MarketFundUniverseItem> candidates) {
        List<FundAggregate> selected = aggregateFunds(category, candidates).stream()
                .sorted(Comparator.comparing(FundAggregate::scale, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(properties.getUniverseSize())
                .toList();
        if (selected.isEmpty()) {
            throw new IllegalStateException(category.getDisplayName() + "未获取到可用基金，保留原基金池");
        }

        LocalDate selectedDate = LocalDate.now();
        Map<Long, FundUniverseDO> existingByMasterId = universeMapper.selectList(
                        new LambdaQueryWrapper<FundUniverseDO>()
                                .eq(FundUniverseDO::getFundCategory, category.name()))
                .stream()
                .collect(Collectors.toMap(FundUniverseDO::getMasterId, Function.identity()));

        List<Long> selectedMasterIds = new ArrayList<>();
        for (int index = 0; index < selected.size(); index++) {
            FundAggregate aggregate = selected.get(index);
            FundMasterDO master = upsertMaster(category, aggregate, selectedDate);
            syncShareClasses(master.getId(), aggregate);
            selectedMasterIds.add(master.getId());

            FundUniverseDO universe = existingByMasterId.get(master.getId());
            if (universe == null) {
                universe = new FundUniverseDO();
                universe.setMasterId(master.getId());
                universe.setFundCategory(category.name());
            }
            universe.setScaleRank(index + 1);
            universe.setSelectedDate(selectedDate);
            universe.setActive(true);
            if (universe.getId() == null) {
                universeMapper.insert(universe);
            } else {
                universeMapper.updateById(universe);
            }
        }

        for (FundUniverseDO existing : existingByMasterId.values()) {
            if (!selectedMasterIds.contains(existing.getMasterId()) && Boolean.TRUE.equals(existing.getActive())) {
                existing.setActive(false);
                universeMapper.updateById(existing);
            }
        }
        return selected.size();
    }

    public List<FundUniverseDO> listActiveUniverse() {
        return universeMapper.selectList(new LambdaQueryWrapper<FundUniverseDO>()
                .eq(FundUniverseDO::getActive, Boolean.TRUE)
                .orderByAsc(FundUniverseDO::getFundCategory)
                .orderByAsc(FundUniverseDO::getScaleRank));
    }

    public Map<Long, FundMasterDO> mastersById(List<FundUniverseDO> universe) {
        if (universe.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = universe.stream().map(FundUniverseDO::getMasterId).distinct().toList();
        return masterMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(FundMasterDO::getId, Function.identity()));
    }

    private List<FundAggregate> aggregateFunds(FundCategory category,
                                               List<MarketFundUniverseItem> candidates) {
        Map<String, FundAggregate> aggregates = new LinkedHashMap<>();
        List<MarketFundUniverseItem> safeCandidates = candidates == null ? List.of() : candidates;
        for (MarketFundUniverseItem fund : safeCandidates) {
            if (!category.accepts(fund)) {
                continue;
            }
            String identityKey = identityKey(category, fund);
            aggregates.computeIfAbsent(identityKey, key -> new FundAggregate(key))
                    .add(fund);
        }
        return aggregates.values().stream().filter(aggregate -> aggregate.primary() != null).toList();
    }

    private FundMasterDO upsertMaster(FundCategory category,
                                      FundAggregate aggregate,
                                      LocalDate scaleDate) {
        FundMasterDO master = masterMapper.selectOne(new LambdaQueryWrapper<FundMasterDO>()
                .eq(FundMasterDO::getIdentityKey, aggregate.identityKey()));
        if (master == null) {
            master = masterMapper.selectOne(new LambdaQueryWrapper<FundMasterDO>()
                    .eq(FundMasterDO::getPrimaryFundCode, aggregate.primary().fundCode()));
        }
        if (master == null) {
            master = new FundMasterDO();
            master.setIdentityKey(aggregate.identityKey());
        }
        MarketFundUniverseItem primary = aggregate.primary();
        master.setPrimaryFundCode(primary.fundCode());
        master.setFundName(normalizeFundName(primary.fundName()));
        master.setFundCategory(category.name());
        master.setFundCompany(primary.fundCompany());
        master.setFundManager(primary.fundManager());
        master.setLatestScale(aggregate.scale());
        master.setScaleDate(scaleDate);
        master.setSourceUrl(primary.sourceUrl());
        master.setActive(true);
        if (master.getId() == null) {
            masterMapper.insert(master);
        } else {
            masterMapper.updateById(master);
        }
        return master;
    }

    private void syncShareClasses(Long masterId, FundAggregate aggregate) {
        Map<String, FundShareClassDO> existingByCode = shareClassMapper.selectList(
                        new LambdaQueryWrapper<FundShareClassDO>()
                                .eq(FundShareClassDO::getMasterId, masterId))
                .stream()
                .collect(Collectors.toMap(FundShareClassDO::getFundCode, Function.identity()));
        for (MarketFundUniverseItem share : aggregate.shares()) {
            FundShareClassDO shareClass = existingByCode.get(share.fundCode());
            if (shareClass == null) {
                shareClass = new FundShareClassDO();
                shareClass.setMasterId(masterId);
                shareClass.setFundCode(share.fundCode());
            }
            shareClass.setShareClass(detectShareClass(share.fundName()));
            shareClass.setPrimaryShare(share.fundCode().equals(aggregate.primary().fundCode()));
            if (shareClass.getId() == null) {
                shareClassMapper.insert(shareClass);
            } else {
                shareClassMapper.updateById(shareClass);
            }
        }
    }

    private String identityKey(FundCategory category, MarketFundUniverseItem fund) {
        return normalizeToken(fund.fundCompany()) + "|" + category.name() + "|"
                + normalizeToken(normalizeFundName(fund.fundName()));
    }

    private String normalizeToken(String value) {
        return Objects.toString(value, "").replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String normalizeFundName(String value) {
        return Objects.toString(value, "")
                .replaceAll("(?i)(?:[（(]?[A-EHIO][）)]?|[A-EHIO]类)$", "")
                .trim();
    }

    private String detectShareClass(String fundName) {
        String value = Objects.toString(fundName, "").trim().toUpperCase(Locale.ROOT);
        if (value.matches(".*(?:[（(]?[A-EHIO][）)]?|[A-EHIO]类)$")) {
            return value.replaceAll(".*([A-EHIO])(?:[）)]|类)?$", "$1");
        }
        return "DEFAULT";
    }

    private int primaryPriority(MarketFundUniverseItem fund) {
        return switch (detectShareClass(fund.fundName())) {
            case "A" -> 0;
            case "DEFAULT" -> 1;
            case "I" -> 2;
            case "C" -> 4;
            default -> 3;
        };
    }

    private final class FundAggregate {
        private final String identityKey;
        private final List<MarketFundUniverseItem> shares = new ArrayList<>();
        private BigDecimal scale = BigDecimal.ZERO;
        private FundAggregate(String identityKey) { this.identityKey = identityKey; }
        private FundAggregate add(MarketFundUniverseItem fund) {
            shares.add(fund);
            if (fund.scaleInBillions() != null) {
                scale = scale.add(fund.scaleInBillions());
            }
            return this;
        }
        private String identityKey() { return identityKey; }
        private List<MarketFundUniverseItem> shares() { return shares; }
        private BigDecimal scale() { return scale; }
        private MarketFundUniverseItem primary() {
            return shares.stream()
                    .min(Comparator.comparingInt(FundUniverseService.this::primaryPriority)
                            .thenComparing(MarketFundUniverseItem::fundCode))
                    .orElse(null);
        }
    }

    public record UniverseRefreshResult(int activeCount, int refreshedCategories, int failedCategories) {
    }
}
