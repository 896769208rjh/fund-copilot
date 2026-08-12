package fundcopilot.marketdata;

import fundcopilot.marketdata.MarketDataDtos.MarketFundSnapshot;
import fundcopilot.marketdata.MarketDataDtos.MarketFundSearchItem;
import fundcopilot.marketdata.MarketDataDtos.MarketFundUniverseItem;

import java.util.List;

public interface FundDataProvider {
    MarketFundSnapshot fetchSnapshot(String fundCode);

    default MarketFundSnapshot fetchSnapshot(String fundCode, int historySize) {
        return fetchSnapshot(fundCode);
    }

    List<MarketFundSearchItem> searchFunds(String keyword);

    List<MarketFundUniverseItem> fetchFundsByScale(String fundType, int limit);
}
