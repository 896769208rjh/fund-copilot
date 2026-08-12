package fundcopilot.observation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fundcopilot.marketdata.MarketDataDtos.MarketFundUniverseItem;
import fundcopilot.observation.entity.FundMasterDO;
import fundcopilot.observation.entity.FundShareClassDO;
import fundcopilot.observation.entity.FundUniverseDO;
import fundcopilot.observation.mapper.FundMasterMapper;
import fundcopilot.observation.mapper.FundShareClassMapper;
import fundcopilot.observation.mapper.FundUniverseMapper;
import fundcopilot.observation.model.FundCategory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FundUniversePersistenceService {
    private final FundMasterMapper masterMapper;
    private final FundShareClassMapper shareClassMapper;
    private final FundUniverseMapper universeMapper;

    public FundUniversePersistenceService(FundMasterMapper masterMapper,
                                          FundShareClassMapper shareClassMapper,
                                          FundUniverseMapper universeMapper) {
        this.masterMapper = masterMapper;
        this.shareClassMapper = shareClassMapper;
        this.universeMapper = universeMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public int replaceCategoryUniverse(FundCategory category,
                                       List<FundUniverseService.FundSelection> selected) {
        LocalDate selectedDate = LocalDate.now();
        Map<Long, FundUniverseDO> existingByMasterId = universeMapper.selectList(
                        new LambdaQueryWrapper<FundUniverseDO>()
                                .eq(FundUniverseDO::getFundCategory, category.name()))
                .stream()
                .collect(Collectors.toMap(FundUniverseDO::getMasterId, Function.identity()));

        List<Long> selectedMasterIds = new ArrayList<>();
        for (int index = 0; index < selected.size(); index++) {
            FundUniverseService.FundSelection selection = selected.get(index);
            FundMasterDO master = upsertMaster(category, selection, selectedDate);
            syncShareClasses(master.getId(), selection);
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

    private FundMasterDO upsertMaster(FundCategory category,
                                      FundUniverseService.FundSelection selection,
                                      LocalDate scaleDate) {
        FundMasterDO master = masterMapper.selectOne(new LambdaQueryWrapper<FundMasterDO>()
                .eq(FundMasterDO::getIdentityKey, selection.identityKey()));
        if (master == null) {
            master = masterMapper.selectOne(new LambdaQueryWrapper<FundMasterDO>()
                    .eq(FundMasterDO::getPrimaryFundCode, selection.primary().fundCode()));
        }
        if (master == null) {
            master = new FundMasterDO();
            master.setIdentityKey(selection.identityKey());
        }
        MarketFundUniverseItem primary = selection.primary();
        master.setPrimaryFundCode(primary.fundCode());
        master.setFundName(normalizeFundName(primary.fundName()));
        master.setFundCategory(category.name());
        master.setFundCompany(primary.fundCompany());
        master.setFundManager(primary.fundManager());
        master.setLatestScale(selection.scale());
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

    private void syncShareClasses(Long masterId, FundUniverseService.FundSelection selection) {
        Map<String, FundShareClassDO> existingByCode = shareClassMapper.selectList(
                        new LambdaQueryWrapper<FundShareClassDO>()
                                .eq(FundShareClassDO::getMasterId, masterId))
                .stream()
                .collect(Collectors.toMap(FundShareClassDO::getFundCode, Function.identity()));
        for (MarketFundUniverseItem share : selection.shares()) {
            FundShareClassDO shareClass = existingByCode.get(share.fundCode());
            if (shareClass == null) {
                shareClass = new FundShareClassDO();
                shareClass.setMasterId(masterId);
                shareClass.setFundCode(share.fundCode());
            }
            shareClass.setShareClass(detectShareClass(share.fundName()));
            shareClass.setPrimaryShare(share.fundCode().equals(selection.primary().fundCode()));
            if (shareClass.getId() == null) {
                shareClassMapper.insert(shareClass);
            } else {
                shareClassMapper.updateById(shareClass);
            }
        }
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
}
