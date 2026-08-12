package fundcopilot.observation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("fund_universe")
public class FundUniverseDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long masterId;
    private String fundCategory;
    private Integer scaleRank;
    private LocalDate selectedDate;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
