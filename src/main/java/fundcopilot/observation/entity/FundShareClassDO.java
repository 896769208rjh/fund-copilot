package fundcopilot.observation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("fund_share_class")
public class FundShareClassDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long masterId;
    private String fundCode;
    private String shareClass;
    private Boolean primaryShare;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
