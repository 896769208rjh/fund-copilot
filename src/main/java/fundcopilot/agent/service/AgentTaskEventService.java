package fundcopilot.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fundcopilot.agent.entity.AgentTaskEventDO;
import fundcopilot.agent.mapper.AgentTaskEventMapper;
import fundcopilot.agent.vo.AgentStreamEventVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AgentTaskEventService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentTaskEventService.class);
    private static final int EVENT_REPLAY_LIMIT = 128;
    private static final Duration EMIT_RETRY_DURATION = Duration.ofMillis(100);

    private final Map<Long, Sinks.Many<AgentStreamEventVO>> taskSinks = new ConcurrentHashMap<>();
    private final Map<Long, AtomicLong> sequenceCounters = new ConcurrentHashMap<>();
    private final AgentTaskEventMapper agentTaskEventMapper;
    private final ObjectMapper objectMapper;

    public AgentTaskEventService(AgentTaskEventMapper agentTaskEventMapper, ObjectMapper objectMapper) {
        this.agentTaskEventMapper = agentTaskEventMapper;
        this.objectMapper = objectMapper;
    }

    public Flux<AgentStreamEventVO> openStream(Long taskId) {
        return taskSinks.computeIfAbsent(taskId, ignored -> createSink()).asFlux();
    }

    public Optional<Flux<AgentStreamEventVO>> findActiveStream(Long taskId) {
        Sinks.Many<AgentStreamEventVO> sink = taskSinks.get(taskId);
        return sink == null ? Optional.empty() : Optional.of(sink.asFlux());
    }

    public void publish(Long taskId, AgentStreamEventVO eventVO) {
        persistEvent(taskId, eventVO);
        Sinks.Many<AgentStreamEventVO> sink = taskSinks.get(taskId);
        if (sink != null) {
            sink.emitNext(eventVO, Sinks.EmitFailureHandler.busyLooping(EMIT_RETRY_DURATION));
        }
    }

    public void complete(Long taskId) {
        Sinks.Many<AgentStreamEventVO> sink = taskSinks.remove(taskId);
        sequenceCounters.remove(taskId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }

    public List<AgentStreamEventVO> replayEvents(Long taskId) {
        return agentTaskEventMapper.selectList(new LambdaQueryWrapper<AgentTaskEventDO>()
                        .eq(AgentTaskEventDO::getTaskId, taskId)
                        .orderByAsc(AgentTaskEventDO::getSequenceNo))
                .stream()
                .map(this::toEventVO)
                .toList();
    }

    private Sinks.Many<AgentStreamEventVO> createSink() {
        return Sinks.many().replay().limit(EVENT_REPLAY_LIMIT);
    }

    private void persistEvent(Long taskId, AgentStreamEventVO eventVO) {
        try {
            AgentTaskEventDO eventDO = new AgentTaskEventDO();
            eventDO.setTaskId(taskId);
            eventDO.setSequenceNo(nextSequence(taskId));
            eventDO.setEventType(eventVO.type());
            eventDO.setPayloadJson(objectMapper.writeValueAsString(eventVO.payload()));
            eventDO.setCreatedAt(LocalDateTime.now());
            agentTaskEventMapper.insert(eventDO);
        } catch (RuntimeException | JsonProcessingException exception) {
            LOGGER.error("Persist agent task event failed, taskId={}, eventType={}",
                    taskId, eventVO.type(), exception);
        }
    }

    private long nextSequence(Long taskId) {
        AtomicLong counter = sequenceCounters.computeIfAbsent(taskId,
                ignored -> new AtomicLong(loadLastSequence(taskId)));
        return counter.incrementAndGet();
    }

    private long loadLastSequence(Long taskId) {
        AgentTaskEventDO latestEvent = agentTaskEventMapper.selectOne(new LambdaQueryWrapper<AgentTaskEventDO>()
                .eq(AgentTaskEventDO::getTaskId, taskId)
                .orderByDesc(AgentTaskEventDO::getSequenceNo)
                .last("limit 1"));
        return latestEvent == null ? 0L : latestEvent.getSequenceNo();
    }

    private AgentStreamEventVO toEventVO(AgentTaskEventDO eventDO) {
        try {
            JsonNode payload = objectMapper.readTree(eventDO.getPayloadJson());
            return new AgentStreamEventVO(eventDO.getEventType(), payload);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Deserialize agent task event failed, eventId={}", eventDO.getId(), exception);
            return new AgentStreamEventVO(eventDO.getEventType(), eventDO.getPayloadJson());
        }
    }
}
