package fundcopilot.observation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("fund_sync_job")
public class FundSyncJobDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String jobType;
    private String triggerType;
    private String status;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
