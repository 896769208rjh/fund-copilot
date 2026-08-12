package fundcopilot.observation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("fund_metric_daily")
public class FundMetricDailyDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long masterId;
    private LocalDate metricDate;
    private LocalDate sourceMetricDate;
    private BigDecimal oneMonthReturn;
    private BigDecimal threeMonthReturn;
    private BigDecimal sixMonthReturn;
    private BigDecimal oneYearReturn;
    private BigDecimal maxDrawdown;
    private BigDecimal volatility;
    private BigDecimal returnDrawdownRatio;
    private Integer sampleSize;
    private BigDecimal performanceScore;
    private BigDecimal drawdownScore;
    private BigDecimal volatilityScore;
    private BigDecimal ratioScore;
    private BigDecimal dataQualityScore;
    private BigDecimal totalScore;
    private Boolean eligible;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
