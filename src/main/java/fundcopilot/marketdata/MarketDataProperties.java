package fundcopilot.marketdata;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fund-copilot.market-data.eastmoney")
public class MarketDataProperties {
    private int timeoutSeconds = 6;
    private int navPageSize = 20;
    private int navHistorySize = 320;
    private long requestIntervalMs = 300;
    private boolean demoFallbackEnabled = true;
    private String searchBaseUrl = "https://fundsuggest.eastmoney.com";
    private String navBaseUrl = "https://api.fund.eastmoney.com";

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public int getNavPageSize() { return navPageSize; }
    public void setNavPageSize(int navPageSize) { this.navPageSize = navPageSize; }
    public int getNavHistorySize() { return navHistorySize; }
    public void setNavHistorySize(int navHistorySize) { this.navHistorySize = navHistorySize; }
    public long getRequestIntervalMs() { return requestIntervalMs; }
    public void setRequestIntervalMs(long requestIntervalMs) { this.requestIntervalMs = requestIntervalMs; }
    public boolean isDemoFallbackEnabled() { return demoFallbackEnabled; }
    public void setDemoFallbackEnabled(boolean demoFallbackEnabled) { this.demoFallbackEnabled = demoFallbackEnabled; }
    public String getSearchBaseUrl() { return searchBaseUrl; }
    public void setSearchBaseUrl(String searchBaseUrl) { this.searchBaseUrl = searchBaseUrl; }
    public String getNavBaseUrl() { return navBaseUrl; }
    public void setNavBaseUrl(String navBaseUrl) { this.navBaseUrl = navBaseUrl; }
}
