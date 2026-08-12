package fundcopilot.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fundcopilot.fund.constant.FundConstants;
import fundcopilot.marketdata.MarketDataDtos.MarketFundSnapshot;
import fundcopilot.marketdata.MarketDataDtos.MarketFundSearchItem;
import fundcopilot.marketdata.MarketDataDtos.MarketFundUniverseItem;
import fundcopilot.marketdata.MarketDataDtos.MarketNavPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class EastmoneyFundDataProvider implements FundDataProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(EastmoneyFundDataProvider.class);
    private static final String USER_AGENT = "Mozilla/5.0 FundCopilot/1.0";
    private static final String REFERER = "https://fundf10.eastmoney.com/";
    private static final String DEFAULT_FUND_TYPE = "未知类型";
    private static final String DEFAULT_STATUS = "以销售平台确认为准";
    private static final String DEFAULT_RISK_LEVEL = "请以基金销售平台风险等级为准";
    private static final int SEARCH_LIMIT = 20;
    private static final int MAX_NAV_PAGE_COUNT = 100;
    private static final int RANK_PAGE_SIZE = 30;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final MarketDataProperties properties;

    @Autowired
    public EastmoneyFundDataProvider(ObjectMapper objectMapper, MarketDataProperties properties) {
        this(objectMapper, properties, createRestClient(properties));
    }

    EastmoneyFundDataProvider(ObjectMapper objectMapper,
                              MarketDataProperties properties,
                              RestClient restClient) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public MarketFundSnapshot fetchSnapshot(String fundCode) {
        return fetchSnapshot(fundCode, properties.getNavHistorySize(), properties.isDemoFallbackEnabled());
    }

    @Override
    public MarketFundSnapshot fetchSnapshot(String fundCode, int historySize) {
        return fetchSnapshot(fundCode, historySize, false);
    }

    private MarketFundSnapshot fetchSnapshot(String fundCode, int historySize, boolean allowDemoFallback) {
        try {
            throttle();
            MarketFundSearchItem searchItem = searchFunds(fundCode)
                    .stream()
                    .filter(item -> fundCode.equals(item.fundCode()))
                    .findFirst()
                    .orElse(null);
            List<MarketNavPoint> navPoints = fetchNavPoints(fundCode, historySize);
            MarketNavPoint latest = navPoints.isEmpty() ? null : navPoints.get(0);
            return new MarketFundSnapshot(
                    fundCode,
                    searchItem == null ? "基金 " + fundCode : searchItem.fundName(),
                    searchItem == null ? DEFAULT_FUND_TYPE : fallbackText(searchItem.fundType(), DEFAULT_FUND_TYPE),
                    searchItem == null ? "东方财富公开数据" : fallbackText(searchItem.fundCompany(), "东方财富公开数据"),
                    searchItem == null ? "以基金公告为准" : fallbackText(searchItem.fundManager(), "以基金公告为准"),
                    DEFAULT_RISK_LEVEL,
                    DEFAULT_STATUS,
                    DEFAULT_STATUS,
                    latest == null ? null : latest.unitNav(),
                    latest == null ? null : latest.navDate(),
                    FundConstants.EASTMONEY_FUND_PAGE_PREFIX + fundCode + ".html",
                    false,
                    LocalDateTime.now(),
                    navPoints
            );
        } catch (Exception exception) {
            if (allowDemoFallback) {
                LOGGER.warn("Fetch eastmoney fund data failed, using demo fallback, fundCode={}",
                        fundCode, exception);
                return fallbackSnapshot(fundCode);
            }
            LOGGER.warn("Fetch eastmoney fund data failed and demo fallback is disabled, fundCode={}, error={}",
                    fundCode, exception.toString());
            throw new MarketDataUnavailableException("东方财富基金数据暂时不可用: " + fundCode, exception);
        }
    }

    @Override
    public List<MarketFundSearchItem> searchFunds(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        try {
            throttle();
            String response = restClient.get()
                    .uri(properties.getSearchBaseUrl()
                            + "/FundSearch/api/FundSearchAPI.ashx?m=1&key={keyword}", keyword.trim())
                    .retrieve()
                    .body(String.class);
            if (response == null || response.isBlank()) {
                return List.of();
            }

            JsonNode dataList = objectMapper.readTree(response).path("Datas");
            if (!dataList.isArray()) {
                return List.of();
            }

            List<MarketFundSearchItem> searchItems = new ArrayList<>();
            for (JsonNode item : dataList) {
                if (searchItems.size() >= SEARCH_LIMIT) {
                    break;
                }
                MarketFundSearchItem searchItem = toSearchItem(item);
                if (searchItem != null) {
                    searchItems.add(searchItem);
                }
            }
            return searchItems;
        } catch (Exception exception) {
            LOGGER.warn("Search eastmoney fund failed, keyword={}", keyword, exception);
            return List.of();
        }
    }

    @Override
    public List<MarketFundUniverseItem> fetchFundsByScale(String fundType, int limit) {
        if (fundType == null || fundType.isBlank() || limit <= 0) {
            return List.of();
        }

        try {
            List<MarketFundUniverseItem> result = new ArrayList<>();
            for (int pageIndex = 1; result.size() < limit; pageIndex++) {
                throttle();
                String response = restClient.get()
                        .uri(properties.getRankBaseUrl()
                                        + "/FundMNewApi/FundMNRank?appType=ttjj&Sort=desc&product=EFund"
                                        + "&version=6.3.1&DataConstraintType=0&onFundCache=3&FundType={fundType}"
                                        + "&BUY=false&pageIndex={pageIndex}&pageSize={pageSize}&SortColumn=ENDNAV"
                                        + "&plat=Android&ISABNORMAL=true",
                                fundType, pageIndex, RANK_PAGE_SIZE)
                        .retrieve()
                        .body(String.class);
                JsonNode root = objectMapper.readTree(response);
                if (!root.path("Success").asBoolean(false)) {
                    throw new IllegalStateException("东方财富基金排行返回失败: "
                            + root.path("ErrCode").asText() + " " + root.path("ErrMsg").asText());
                }
                JsonNode dataList = root.path("Datas");
                if (!dataList.isArray() || dataList.isEmpty()) {
                    break;
                }
                for (JsonNode item : dataList) {
                    MarketFundUniverseItem fund = toUniverseItem(item);
                    if (fund != null) {
                        result.add(fund);
                    }
                    if (result.size() >= limit) {
                        break;
                    }
                }
                int totalCount = root.path("TotalCount").asInt(0);
                if (dataList.size() < RANK_PAGE_SIZE || totalCount > 0 && result.size() >= totalCount) {
                    break;
                }
            }
            return result;
        } catch (Exception exception) {
            LOGGER.warn("Fetch eastmoney fund universe failed, fundType={}", fundType, exception);
            throw new MarketDataUnavailableException("东方财富基金规模排行暂时不可用: " + fundType, exception);
        }
    }

    private List<MarketNavPoint> fetchNavPoints(String fundCode, int requestedHistorySize) throws Exception {
        int pageSize = Math.max(1, properties.getNavPageSize());
        int historySize = Math.max(pageSize, requestedHistorySize);
        Map<LocalDate, MarketNavPoint> pointsByDate = new LinkedHashMap<>();

        for (int pageIndex = 1; pageIndex <= MAX_NAV_PAGE_COUNT && pointsByDate.size() < historySize; pageIndex++) {
            throttle();
            String response = restClient.get()
                    .uri(properties.getNavBaseUrl()
                                    + "/f10/lsjz?fundCode={fundCode}&pageIndex={pageIndex}&pageSize={pageSize}",
                            fundCode, pageIndex, pageSize)
                    .retrieve()
                    .body(String.class);
            MarketNavPage navPage = parseNavPage(fundCode, response);
            navPage.points().forEach(point -> pointsByDate.putIfAbsent(point.navDate(), point));
            if (navPage.points().isEmpty()
                    || navPage.totalCount() > 0 && pointsByDate.size() >= navPage.totalCount()) {
                break;
            }
        }

        if (pointsByDate.isEmpty()) {
            throw new IllegalStateException("东方财富未返回有效净值数据");
        }
        return pointsByDate.values().stream()
                .sorted(Comparator.comparing(MarketNavPoint::navDate).reversed())
                .limit(historySize)
                .toList();
    }

    private MarketNavPage parseNavPage(String fundCode, String response) throws Exception {
        if (response == null || response.isBlank()) {
            return new MarketNavPage(List.of(), 0);
        }
        JsonNode root = objectMapper.readTree(response);
        JsonNode list = root.path("Data").path("LSJZList");
        if (!list.isArray()) {
            return new MarketNavPage(List.of(), root.path("TotalCount").asInt(0));
        }
        List<MarketNavPoint> points = new ArrayList<>();
        for (JsonNode item : list) {
            LocalDate date = parseDate(item.path("FSRQ").asText(null));
            BigDecimal unitNav = parseDecimal(item.path("DWJZ").asText(null));
            if (date != null && unitNav != null) {
                points.add(new MarketNavPoint(
                        date,
                        unitNav,
                        parseDecimal(item.path("LJJZ").asText(null)),
                        parseDecimal(item.path("JZZZL").asText(null)),
                        FundConstants.EASTMONEY_FUND_PAGE_PREFIX + fundCode + ".html"
                ));
            }
        }
        return new MarketNavPage(points, root.path("TotalCount").asInt(0));
    }

    private record MarketNavPage(List<MarketNavPoint> points, int totalCount) {
    }

    private MarketFundSnapshot fallbackSnapshot(String fundCode) {
        LocalDate today = LocalDate.now();
        List<MarketNavPoint> points = List.of(
                new MarketNavPoint(today.minusDays(1), BigDecimal.valueOf(1.0200), BigDecimal.valueOf(1.0200), BigDecimal.valueOf(0.25), FundConstants.EASTMONEY_FUND_PAGE_PREFIX + fundCode + ".html"),
                new MarketNavPoint(today.minusDays(2), BigDecimal.valueOf(1.0175), BigDecimal.valueOf(1.0175), BigDecimal.valueOf(-0.10), FundConstants.EASTMONEY_FUND_PAGE_PREFIX + fundCode + ".html"),
                new MarketNavPoint(today.minusDays(3), BigDecimal.valueOf(1.0185), BigDecimal.valueOf(1.0185), BigDecimal.valueOf(0.15), FundConstants.EASTMONEY_FUND_PAGE_PREFIX + fundCode + ".html")
        );

        return new MarketFundSnapshot(
                fundCode,
                "基金 " + fundCode,
                DEFAULT_FUND_TYPE,
                "东方财富公开数据",
                "以基金公告为准",
                DEFAULT_RISK_LEVEL,
                DEFAULT_STATUS,
                DEFAULT_STATUS,
                points.get(0).unitNav(),
                points.get(0).navDate(),
                FundConstants.EASTMONEY_FUND_PAGE_PREFIX + fundCode + ".html",
                true,
                LocalDateTime.now(),
                points
        );
    }

    private MarketFundSearchItem toSearchItem(JsonNode item) {
        String fundCode = item.path("CODE").asText(null);
        JsonNode baseInfo = item.path("FundBaseInfo");
        if (fundCode == null || fundCode.isBlank()) {
            fundCode = baseInfo.path("FCODE").asText(null);
        }
        if (fundCode == null || fundCode.isBlank()) {
            return null;
        }

        String fundName = fallbackText(baseInfo.path("SHORTNAME").asText(null), item.path("NAME").asText("基金 " + fundCode));
        return new MarketFundSearchItem(
                fundCode,
                fundName,
                baseInfo.path("FTYPE").asText(null),
                baseInfo.path("JJGS").asText(null),
                baseInfo.path("JJJL").asText(null),
                parseDecimal(baseInfo.path("DWJZ").asText(null)),
                parseDate(baseInfo.path("FSRQ").asText(null)),
                FundConstants.EASTMONEY_FUND_PAGE_PREFIX + fundCode + ".html"
        );
    }

    private MarketFundUniverseItem toUniverseItem(JsonNode item) {
        String fundCode = item.path("FCODE").asText(null);
        String fundName = item.path("SHORTNAME").asText(null);
        if (fundCode == null || fundCode.isBlank() || fundName == null || fundName.isBlank()) {
            return null;
        }
        return new MarketFundUniverseItem(
                fundCode,
                fundName,
                fallbackText(item.path("FUNDTYPE").asText(null), item.path("FTYPE").asText(null)),
                item.path("JJGS").asText(null),
                item.path("JJJL").asText(null),
                normalizeScale(parseDecimal(item.path("ENDNAV").asText(null))),
                parseDecimal(item.path("SYL_Y").asText(null)),
                parseDecimal(item.path("SYL_3Y").asText(null)),
                parseDecimal(item.path("SYL_6Y").asText(null)),
                parseDecimal(item.path("SYL_1N").asText(null)),
                parseDate(item.path("FSRQ").asText(null)),
                FundConstants.EASTMONEY_FUND_PAGE_PREFIX + fundCode + ".html"
        );
    }

    private String fallbackText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void throttle() {
        try {
            Thread.sleep(Duration.ofMillis(properties.getRequestIntervalMs()).toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Eastmoney request throttle interrupted", exception);
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank() || "--".equals(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            LOGGER.debug("Ignore invalid eastmoney date value={}", value);
            return null;
        }
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank() || "--".equals(value)) {
            return null;
        }
        return new BigDecimal(value);
    }

    private BigDecimal normalizeScale(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.abs().compareTo(BigDecimal.valueOf(1_000_000)) > 0
                ? value.divide(BigDecimal.valueOf(100_000_000), 6, java.math.RoundingMode.HALF_UP)
                : value;
    }

    private static RestClient createRestClient(MarketDataProperties properties) {
        Duration timeout = Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds()));
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", USER_AGENT)
                .defaultHeader("Referer", REFERER)
                .build();
    }
}
