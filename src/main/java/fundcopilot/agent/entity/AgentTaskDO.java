package fundcopilot.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("agent_task")
public class AgentTaskDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private String fundCode;
    private String question;
    private String thinkingMode;
    private String requestKey;
    private String status;
    private Boolean restricted;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String finalAnswer;
    private String disclaimer;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String errorMessage;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String stateSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String nextStageCode;
    private Integer retryCount;
    private LocalDateTime deadlineAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime startedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime completedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long elapsedMs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public String getFundCode() { return fundCode; }
    public void setFundCode(String fundCode) { this.fundCode = fundCode; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getThinkingMode() { return thinkingMode; }
    public void setThinkingMode(String thinkingMode) { this.thinkingMode = thinkingMode; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getRestricted() { return restricted; }
    public void setRestricted(Boolean restricted) { this.restricted = restricted; }
    public String getFinalAnswer() { return finalAnswer; }
    public void setFinalAnswer(String finalAnswer) { this.finalAnswer = finalAnswer; }
    public String getDisclaimer() { return disclaimer; }
    public void setDisclaimer(String disclaimer) { this.disclaimer = disclaimer; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getStateSnapshot() { return stateSnapshot; }
    public void setStateSnapshot(String stateSnapshot) { this.stateSnapshot = stateSnapshot; }
    public String getNextStageCode() { return nextStageCode; }
    public void setNextStageCode(String nextStageCode) { this.nextStageCode = nextStageCode; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public LocalDateTime getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(LocalDateTime deadlineAt) { this.deadlineAt = deadlineAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public Long getElapsedMs() { return elapsedMs; }
    public void setElapsedMs(Long elapsedMs) { this.elapsedMs = elapsedMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
