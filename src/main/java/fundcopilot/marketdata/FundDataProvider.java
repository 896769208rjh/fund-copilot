package fundcopilot.marketdata;

import fundcopilot.marketdata.MarketDataDtos.MarketFundSnapshot;
import fundcopilot.marketdata.MarketDataDtos.MarketFundSearchItem;

import java.util.List;

public interface FundDataProvider {
    MarketFundSnapshot fetchSnapshot(String fundCode);

    List<MarketFundSearchItem> searchFunds(String keyword);
}
