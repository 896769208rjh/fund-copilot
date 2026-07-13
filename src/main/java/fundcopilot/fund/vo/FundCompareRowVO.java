package fundcopilot.fund.vo;

import java.util.List;

public record FundCompareRowVO(
        String dimension,
        List<String> values
) {
}
