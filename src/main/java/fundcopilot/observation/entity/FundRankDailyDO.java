package fundcopilot.observation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("fund_rank_daily")
public class FundRankDailyDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long masterId;
    private String fundCategory;
    private LocalDate rankDate;
    private Integer rawRank;
    private Integer publishedRank;
    private BigDecimal totalScore;
    private Boolean visible;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
