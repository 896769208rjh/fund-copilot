package fundcopilot.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_memory_entry")
public class AgentMemoryEntryDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String fundCode;
    private Long taskId;
    private String question;
    private String summary;
    private String riskSummary;
    private String reflection;
    private LocalDateTime createdAt;
}
