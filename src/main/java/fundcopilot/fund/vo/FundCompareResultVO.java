package fundcopilot.fund.vo;

import java.time.LocalDateTime;
import java.util.List;

public record FundCompareResultVO(
        List<FundCompareColumnVO> columns,
        List<FundCompareRowVO> rows,
        String summary,
        LocalDateTime generatedAt
) {
}
