package fundcopilot.observation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("fund_rank_membership")
public class FundRankMembershipDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long masterId;
    private String fundCategory;
    private Boolean active;
    private Integer qualifyingStreak;
    private Integer disqualifyingStreak;
    private LocalDate lastEvaluatedDate;
    private LocalDateTime enteredAt;
    private LocalDateTime exitedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
