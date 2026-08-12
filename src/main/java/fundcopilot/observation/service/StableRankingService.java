package fundcopilot.observation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fundcopilot.observation.config.ObservationProperties;
import fundcopilot.observation.entity.FundMetricDailyDO;
import fundcopilot.observation.entity.FundRankDailyDO;
import fundcopilot.observation.entity.FundRankMembershipDO;
import fundcopilot.observation.mapper.FundRankDailyMapper;
import fundcopilot.observation.mapper.FundRankMembershipMapper;
import fundcopilot.observation.mapper.FundUniverseMapper;
import fundcopilot.observation.entity.FundUniverseDO;
import fundcopilot.observation.model.FundCategory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StableRankingService {
    private final FundRankMembershipMapper membershipMapper;
    private final FundRankDailyMapper rankDailyMapper;
    private final ObservationProperties properties;
    private final FundUniverseMapper universeMapper;

    public StableRankingService(FundRankMembershipMapper membershipMapper,
                                FundRankDailyMapper rankDailyMapper,
                                FundUniverseMapper universeMapper,
                                ObservationProperties properties) {
        this.membershipMapper = membershipMapper;
        this.rankDailyMapper = rankDailyMapper;
        this.universeMapper = universeMapper;
        this.properties = properties;
    }

    @Transactional(rollbackFor = Exception.class)
    public void publish(LocalDate rankDate, Map<FundCategory, List<FundMetricDailyDO>> metricsByCategory) {
        for (FundCategory category : FundCategory.values()) {
            publishCategory(category, rankDate, metricsByCategory.getOrDefault(category, List.of()));
        }
    }

    private void publishCategory(FundCategory category,
                                 LocalDate rankDate,
                                 List<FundMetricDailyDO> metrics) {
        List<FundMetricDailyDO> ranked = metrics.stream()
                .filter(metric -> Boolean.TRUE.equals(metric.getEligible()) && metric.getTotalScore() != null)
                .sorted(Comparator.comparing(FundMetricDailyDO::getTotalScore).reversed()
                        .thenComparing(FundMetricDailyDO::getMasterId))
                .toList();
        Set<Long> currentUniverseIds = universeMapper.selectList(new LambdaQueryWrapper<FundUniverseDO>()
                        .eq(FundUniverseDO::getFundCategory, category.name())
                        .eq(FundUniverseDO::getActive, Boolean.TRUE))
                .stream().map(FundUniverseDO::getMasterId).collect(Collectors.toSet());
        List<FundMetricDailyDO> universeRanked = ranked.stream()
                .filter(metric -> currentUniverseIds.contains(metric.getMasterId())).toList();
        Set<Long> rawTopIds = universeRanked.stream().limit(properties.getRankingSize())
                .map(FundMetricDailyDO::getMasterId).collect(Collectors.toSet());

        Map<Long, FundRankMembershipDO> memberships = membershipMapper.selectList(
                        new LambdaQueryWrapper<FundRankMembershipDO>()
                                .eq(FundRankMembershipDO::getFundCategory, category.name()))
                .stream().collect(Collectors.toMap(FundRankMembershipDO::getMasterId, Function.identity()));
        boolean firstPublication = memberships.values().stream().noneMatch(item -> item.getLastEvaluatedDate() != null);
        Set<Long> allMasterIds = new HashSet<>(memberships.keySet());
        allMasterIds.addAll(metrics.stream().map(FundMetricDailyDO::getMasterId).toList());

        for (Long masterId : allMasterIds) {
            FundRankMembershipDO membership = memberships.computeIfAbsent(masterId,
                    id -> newMembership(id, category));
            if (rankDate.equals(membership.getLastEvaluatedDate())) {
                continue;
            }
            boolean qualifies = rawTopIds.contains(masterId);
            if (qualifies) {
                membership.setQualifyingStreak(membership.getQualifyingStreak() + 1);
                membership.setDisqualifyingStreak(0);
            } else {
                membership.setQualifyingStreak(0);
                membership.setDisqualifyingStreak(membership.getDisqualifyingStreak() + 1);
            }
            membership.setLastEvaluatedDate(rankDate);
        }

        LocalDateTime now = LocalDateTime.now();
        if (firstPublication) {
            for (Long masterId : rawTopIds) {
                activate(memberships.get(masterId), now);
            }
        } else {
            memberships.values().stream()
                    .filter(item -> Boolean.TRUE.equals(item.getActive()))
                    .filter(item -> item.getDisqualifyingStreak() >= properties.getExitStreakDays())
                    .forEach(item -> deactivate(item, now));

            int vacancies = properties.getRankingSize() - (int) memberships.values().stream()
                    .filter(item -> Boolean.TRUE.equals(item.getActive())).count();
            if (vacancies > 0) {
                for (FundMetricDailyDO metric : universeRanked) {
                    FundRankMembershipDO membership = memberships.get(metric.getMasterId());
                    if (!Boolean.TRUE.equals(membership.getActive())
                            && membership.getQualifyingStreak() >= properties.getEntryStreakDays()) {
                        activate(membership, now);
                        vacancies--;
                    }
                    if (vacancies == 0) {
                        break;
                    }
                }
            }
        }
        memberships.values().forEach(this::saveMembership);
        saveDailyRanks(category, rankDate, metrics, ranked, memberships);
    }

    private void saveDailyRanks(FundCategory category,
                                LocalDate rankDate,
                                List<FundMetricDailyDO> metrics,
                                List<FundMetricDailyDO> ranked,
                                Map<Long, FundRankMembershipDO> memberships) {
        Map<Long, Integer> rawRanks = new HashMap<>();
        for (int index = 0; index < ranked.size(); index++) {
            rawRanks.put(ranked.get(index).getMasterId(), index + 1);
        }
        List<FundMetricDailyDO> visibleMetrics = metrics.stream()
                .filter(metric -> Boolean.TRUE.equals(memberships.get(metric.getMasterId()).getActive()))
                .sorted(Comparator.comparing(FundMetricDailyDO::getTotalScore,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(FundMetricDailyDO::getMasterId))
                .limit(properties.getRankingSize())
                .toList();
        Map<Long, Integer> publishedRanks = new HashMap<>();
        for (int index = 0; index < visibleMetrics.size(); index++) {
            publishedRanks.put(visibleMetrics.get(index).getMasterId(), index + 1);
        }

        List<FundRankDailyDO> existingRanks = rankDailyMapper.selectList(
                new LambdaQueryWrapper<FundRankDailyDO>()
                        .eq(FundRankDailyDO::getFundCategory, category.name())
                        .eq(FundRankDailyDO::getRankDate, rankDate));
        Map<Long, FundRankDailyDO> byMasterId = existingRanks.stream()
                .collect(Collectors.toMap(FundRankDailyDO::getMasterId, Function.identity()));
        List<Long> processed = new ArrayList<>();
        for (FundMetricDailyDO metric : metrics) {
            FundRankDailyDO rank = byMasterId.get(metric.getMasterId());
            if (rank == null) {
                rank = new FundRankDailyDO();
                rank.setMasterId(metric.getMasterId());
                rank.setFundCategory(category.name());
                rank.setRankDate(rankDate);
            }
            rank.setRawRank(rawRanks.get(metric.getMasterId()));
            rank.setPublishedRank(publishedRanks.get(metric.getMasterId()));
            rank.setTotalScore(metric.getTotalScore());
            rank.setVisible(publishedRanks.containsKey(metric.getMasterId()));
            saveRank(rank);
            processed.add(metric.getMasterId());
        }
        for (FundRankDailyDO existing : existingRanks) {
            if (!processed.contains(existing.getMasterId())) {
                existing.setPublishedRank(null);
                existing.setVisible(false);
                rankDailyMapper.updateById(existing);
            }
        }
    }

    private FundRankMembershipDO newMembership(Long masterId, FundCategory category) {
        FundRankMembershipDO membership = new FundRankMembershipDO();
        membership.setMasterId(masterId);
        membership.setFundCategory(category.name());
        membership.setActive(false);
        membership.setQualifyingStreak(0);
        membership.setDisqualifyingStreak(0);
        return membership;
    }

    private void activate(FundRankMembershipDO membership, LocalDateTime now) {
        membership.setActive(true);
        membership.setEnteredAt(now);
        membership.setExitedAt(null);
    }

    private void deactivate(FundRankMembershipDO membership, LocalDateTime now) {
        membership.setActive(false);
        membership.setExitedAt(now);
    }

    private void saveMembership(FundRankMembershipDO membership) {
        if (membership.getId() == null) {
            membershipMapper.insert(membership);
        } else {
            membershipMapper.updateById(membership);
        }
    }

    private void saveRank(FundRankDailyDO rank) {
        if (rank.getId() == null) {
            rankDailyMapper.insert(rank);
        } else {
            rankDailyMapper.updateById(rank);
        }
    }
}
