package fundcopilot.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fundcopilot.agent.entity.AgentModelCallDO;
import fundcopilot.agent.mapper.AgentModelCallMapper;
import fundcopilot.agent.model.AgentModelCallTrace;
import fundcopilot.agent.model.AgentThinkingMode;
import fundcopilot.agent.vo.AgentModelCallVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AgentModelCallService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentModelCallService.class);

    private final AgentModelCallMapper agentModelCallMapper;

    public AgentModelCallService(AgentModelCallMapper agentModelCallMapper) {
        this.agentModelCallMapper = agentModelCallMapper;
    }

    public void record(AgentModelCallTrace trace) {
        try {
            AgentModelCallDO modelCallDO = new AgentModelCallDO();
            modelCallDO.setTaskId(trace.taskId());
            modelCallDO.setStageCode(trace.stageCode());
            modelCallDO.setAgentName(trace.agentName());
            modelCallDO.setModelName(trace.modelName());
            modelCallDO.setThinkingMode(trace.thinkingMode().name());
            modelCallDO.setPromptVersion(trace.promptVersion());
            modelCallDO.setOutputSchema(trace.outputSchema());
            modelCallDO.setAttemptNo(trace.attemptNo());
            modelCallDO.setStatus(trace.status());
            modelCallDO.setInputTokens(trace.inputTokens());
            modelCallDO.setOutputTokens(trace.outputTokens());
            modelCallDO.setInputChars(trace.inputChars());
            modelCallDO.setOutputChars(trace.outputChars());
            modelCallDO.setElapsedMs(trace.elapsedMs());
            modelCallDO.setFallbackReason(trace.fallbackReason());
            modelCallDO.setErrorMessage(trace.errorMessage());
            modelCallDO.setCreatedAt(LocalDateTime.now());
            agentModelCallMapper.insert(modelCallDO);
        } catch (RuntimeException exception) {
            LOGGER.warn("Persist model call trace failed, taskId={}, stageCode={}",
                    trace.taskId(), trace.stageCode(), exception);
        }
    }

    public List<AgentModelCallVO> listByTaskId(Long taskId) {
        return agentModelCallMapper.selectList(new LambdaQueryWrapper<AgentModelCallDO>()
                        .eq(AgentModelCallDO::getTaskId, taskId)
                        .orderByAsc(AgentModelCallDO::getCreatedAt)
                        .orderByAsc(AgentModelCallDO::getId))
                .stream()
                .map(this::toVO)
                .toList();
    }

    private AgentModelCallVO toVO(AgentModelCallDO modelCallDO) {
        return new AgentModelCallVO(
                modelCallDO.getId(),
                modelCallDO.getTaskId(),
                modelCallDO.getStageCode(),
                modelCallDO.getAgentName(),
                modelCallDO.getModelName(),
                AgentThinkingMode.fromValue(modelCallDO.getThinkingMode()),
                modelCallDO.getPromptVersion(),
                modelCallDO.getOutputSchema(),
                modelCallDO.getAttemptNo(),
                modelCallDO.getStatus(),
                modelCallDO.getInputTokens(),
                modelCallDO.getOutputTokens(),
                modelCallDO.getInputChars(),
                modelCallDO.getOutputChars(),
                modelCallDO.getElapsedMs(),
                modelCallDO.getFallbackReason(),
                modelCallDO.getErrorMessage(),
                modelCallDO.getCreatedAt()
        );
    }
}
