package fundcopilot.marketdata;

import fundcopilot.marketdata.MarketDataDtos.MarketFundSnapshot;

public interface FundDataProvider {
    MarketFundSnapshot fetchSnapshot(String fundCode);
}
