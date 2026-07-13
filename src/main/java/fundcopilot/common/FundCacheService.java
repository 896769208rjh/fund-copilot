package fundcopilot.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

@Service
public class FundCacheService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FundCacheService.class);
    private static final Duration FAILURE_BACKOFF = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean redisEnabled;
    private volatile Instant redisUnavailableUntil = Instant.MIN;

    public FundCacheService(StringRedisTemplate redisTemplate,
                            ObjectMapper objectMapper,
                            @Value("${fund-copilot.cache.redis-enabled:true}") boolean redisEnabled) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.redisEnabled = redisEnabled;
    }

    public <T> Optional<T> get(String key, TypeReference<T> typeReference) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, typeReference));
        } catch (RedisConnectionFailureException exception) {
            markUnavailable(exception);
            return Optional.empty();
        } catch (Exception exception) {
            LOGGER.warn("Read cache failed, key={}", key, exception);
            return Optional.empty();
        }
    }

    public void set(String key, Object value, Duration ttl) {
        if (!isAvailable()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Serialize cache value failed, key={}", key, exception);
        } catch (RedisConnectionFailureException exception) {
            markUnavailable(exception);
        } catch (Exception exception) {
            LOGGER.warn("Write cache failed, key={}", key, exception);
        }
    }

    public void delete(Collection<String> keys) {
        if (!isAvailable() || keys == null || keys.isEmpty()) {
            return;
        }
        try {
            redisTemplate.delete(keys);
        } catch (RedisConnectionFailureException exception) {
            markUnavailable(exception);
        } catch (Exception exception) {
            LOGGER.warn("Delete cache failed, keys={}", keys, exception);
        }
    }

    private boolean isAvailable() {
        return redisEnabled && Instant.now().isAfter(redisUnavailableUntil);
    }

    private void markUnavailable(Exception exception) {
        redisUnavailableUntil = Instant.now().plus(FAILURE_BACKOFF);
        LOGGER.warn("Redis cache unavailable, cache will be skipped for {} seconds", FAILURE_BACKOFF.toSeconds(), exception);
    }
}
