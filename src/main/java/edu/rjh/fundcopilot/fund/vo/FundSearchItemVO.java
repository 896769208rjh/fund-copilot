package edu.rjh.fundcopilot.fund.vo;

public record FundSearchItemVO(
        String fundCode,
        String fundName,
        String fundType,
        String riskLevel,
        String displayTag
) {
}
