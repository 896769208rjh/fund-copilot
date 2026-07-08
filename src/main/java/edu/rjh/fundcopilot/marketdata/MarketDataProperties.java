package edu.rjh.fundcopilot.marketdata;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fund-copilot.market-data.eastmoney")
public class MarketDataProperties {
    private int timeoutSeconds = 6;
    private int navPageSize = 120;
    private long requestIntervalMs = 300;

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public int getNavPageSize() { return navPageSize; }
    public void setNavPageSize(int navPageSize) { this.navPageSize = navPageSize; }
    public long getRequestIntervalMs() { return requestIntervalMs; }
    public void setRequestIntervalMs(long requestIntervalMs) { this.requestIntervalMs = requestIntervalMs; }
}
