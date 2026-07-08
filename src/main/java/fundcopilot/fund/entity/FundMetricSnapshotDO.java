package fundcopilot.fund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("fund_metric_snapshot")
public class FundMetricSnapshotDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String fundCode;
    private BigDecimal oneMonthReturn;
    private BigDecimal threeMonthReturn;
    private BigDecimal sixMonthReturn;
    private BigDecimal oneYearReturn;
    private BigDecimal maxDrawdown;
    private BigDecimal volatility;
    private LocalDate statisticDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFundCode() { return fundCode; }
    public void setFundCode(String fundCode) { this.fundCode = fundCode; }
    public BigDecimal getOneMonthReturn() { return oneMonthReturn; }
    public void setOneMonthReturn(BigDecimal oneMonthReturn) { this.oneMonthReturn = oneMonthReturn; }
    public BigDecimal getThreeMonthReturn() { return threeMonthReturn; }
    public void setThreeMonthReturn(BigDecimal threeMonthReturn) { this.threeMonthReturn = threeMonthReturn; }
    public BigDecimal getSixMonthReturn() { return sixMonthReturn; }
    public void setSixMonthReturn(BigDecimal sixMonthReturn) { this.sixMonthReturn = sixMonthReturn; }
    public BigDecimal getOneYearReturn() { return oneYearReturn; }
    public void setOneYearReturn(BigDecimal oneYearReturn) { this.oneYearReturn = oneYearReturn; }
    public BigDecimal getMaxDrawdown() { return maxDrawdown; }
    public void setMaxDrawdown(BigDecimal maxDrawdown) { this.maxDrawdown = maxDrawdown; }
    public BigDecimal getVolatility() { return volatility; }
    public void setVolatility(BigDecimal volatility) { this.volatility = volatility; }
    public LocalDate getStatisticDate() { return statisticDate; }
    public void setStatisticDate(LocalDate statisticDate) { this.statisticDate = statisticDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
