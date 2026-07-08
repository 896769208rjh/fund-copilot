package fundcopilot.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("agent_run_log")
public class AgentRunLogDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String agentName;
    private String fundCode;
    private String question;
    private String toolTrace;
    private String status;
    private Long elapsedMs;
    private String errorMessage;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getFundCode() { return fundCode; }
    public void setFundCode(String fundCode) { this.fundCode = fundCode; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getToolTrace() { return toolTrace; }
    public void setToolTrace(String toolTrace) { this.toolTrace = toolTrace; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getElapsedMs() { return elapsedMs; }
    public void setElapsedMs(Long elapsedMs) { this.elapsedMs = elapsedMs; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
