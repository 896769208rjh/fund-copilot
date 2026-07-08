package edu.rjh.fundcopilot.marketdata;

import edu.rjh.fundcopilot.marketdata.MarketDataDtos.MarketFundSnapshot;

public interface FundDataProvider {
    MarketFundSnapshot fetchSnapshot(String fundCode);
}
