package fundcopilot.marketdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fundcopilot.fund.constant.FundConstants;
import fundcopilot.marketdata.MarketDataDtos.MarketFundSnapshot;
import fundcopilot.marketdata.MarketDataDtos.MarketFundSearchItem;
import fundcopilot.marketdata.MarketDataDtos.MarketNavPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class EastmoneyFundDataProvider implements FundDataProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(EastmoneyFundDataProvider.class);
    private static final String USER_AGENT = "Mozilla/5.0 FundCopilot/1.0";
    private static final String REFERER = "https://fundf10.eastmoney.com/";
    private static final String DEFAULT_FUND_TYPE = "未知类型";
    private static final String DEFAULT_STATUS = "以销售平台确认为准";
    private static final String DEFAULT_RISK_LEVEL = "请以基金销售平台风险等级为准";
    private static final int SEARCH_LIMIT = 20;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final MarketDataProperties properties;

    public EastmoneyFundDataProvider(ObjectMapper objectMapper, MarketDataProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.restClient = RestClient.builder()
                .defaultHeader("User-Agent", USER_AGENT)
                .defaultHeader("Referer", REFERER)
                .build();
    }

    @Override
    public MarketFundSnapshot fetchSnapshot(String fundCode) {
        try {
            throttle();
            MarketFundSearchItem searchItem = searchFunds(fundCode)
                    .stream()
                    .filter(item -> fundCode.equals(item.fundCode()))
                    .findFirst()
                    .orElse(null);
            List<MarketNavPoint> navPoints = fetchNavPoints(fundCode);
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
            LOGGER.warn("Fetch eastmoney fund data failed, fundCode={}", fundCode, exception);
            return fallbackSnapshot(fundCode);
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
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("fundsuggest.eastmoney.com")
                            .path("/FundSearch/api/FundSearchAPI.ashx")
                            .queryParam("m", 1)
                            .queryParam("key", keyword.trim())
                            .build())
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

    private List<MarketNavPoint> fetchNavPoints(String fundCode) throws Exception {
        String response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("api.fund.eastmoney.com")
                        .path("/f10/lsjz")
                        .queryParam("fundCode", fundCode)
                        .queryParam("pageIndex", 1)
                        .queryParam("pageSize", properties.getNavPageSize())
                        .build())
                .retrieve()
                .body(String.class);

        if (response == null || response.isBlank()) {
            return List.of();
        }

        JsonNode root = objectMapper.readTree(response);
        JsonNode list = root.path("Data").path("LSJZList");
        List<MarketNavPoint> points = new ArrayList<>();
        if (!list.isArray()) {
            return points;
        }

        for (JsonNode item : list) {
            LocalDate date = parseDate(item.path("FSRQ").asText(null));
            BigDecimal unitNav = parseDecimal(item.path("DWJZ").asText(null));
            BigDecimal accumulatedNav = parseDecimal(item.path("LJJZ").asText(null));
            BigDecimal growthRate = parseDecimal(item.path("JZZZL").asText(null));
            if (date != null && unitNav != null) {
                points.add(new MarketNavPoint(
                        date,
                        unitNav,
                        accumulatedNav,
                        growthRate,
                        FundConstants.EASTMONEY_FUND_PAGE_PREFIX + fundCode + ".html"
                ));
            }
        }

        return points;
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
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank() || "--".equals(value)) {
            return null;
        }
        return new BigDecimal(value);
    }
}
