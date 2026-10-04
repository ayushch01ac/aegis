package com.aegis.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * Coordinates side-effecting proxy requests with Redis. A key has a short-lived PROCESSING
 * lease followed by a replayable COMPLETED response. The acquire transition is one Lua script,
 * so multiple Aegis instances cannot execute the same fingerprint concurrently.
 */
@Service
public class IdempotencyService {

    private static final String STATE_PROCESSING = "PROCESSING";
    private static final String STATE_COMPLETED = "COMPLETED";
    private static final String ACQUIRE_SCRIPT = """
            if redis.call('EXISTS', KEYS[1]) == 0 then
                redis.call('HSET', KEYS[1], 'fingerprint', ARGV[1], 'state', 'PROCESSING')
                redis.call('EXPIRE', KEYS[1], ARGV[2])
                return 'OWNED'
            end
            if redis.call('HGET', KEYS[1], 'fingerprint') ~= ARGV[1] then return 'CONFLICT' end
            return redis.call('HGET', KEYS[1], 'state')
            """;
    private static final String COMPLETE_SCRIPT = """
            if redis.call('HGET', KEYS[1], 'fingerprint') == ARGV[1]
              and redis.call('HGET', KEYS[1], 'state') == 'PROCESSING' then
                redis.call('HSET', KEYS[1], 'state', 'COMPLETED', 'status', ARGV[2], 'headers', ARGV[3], 'body', ARGV[4])
                redis.call('EXPIRE', KEYS[1], ARGV[5])
                return 1
            end
            return 0
            """;
    private static final String RELEASE_SCRIPT = """
            if redis.call('HGET', KEYS[1], 'fingerprint') == ARGV[1]
              and redis.call('HGET', KEYS[1], 'state') == 'PROCESSING' then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final IdempotencyProperties properties;
    private final String keyPrefix;
    private final RedisScript<String> acquireScript = new DefaultRedisScript<>(ACQUIRE_SCRIPT, String.class);
    private final RedisScript<Long> completeScript = new DefaultRedisScript<>(COMPLETE_SCRIPT, Long.class);
    private final RedisScript<Long> releaseScript = new DefaultRedisScript<>(RELEASE_SCRIPT, Long.class);

    public IdempotencyService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            IdempotencyProperties properties,
            @org.springframework.beans.factory.annotation.Value("${aegis.redis.key-prefix:aegis:}") String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.keyPrefix = keyPrefix;
    }

    public IdempotencyResponse execute(String scope, String key, String fingerprint, Supplier<ResponseEntity<byte[]>> action) {
        if (!properties.enabled()) {
            return new IdempotencyResponse(action.get(), false);
        }
        String redisKey = keyPrefix + "idempotency:" + sha256(scope + "\n" + key);
        String state = acquire(redisKey, fingerprint);
        if ("CONFLICT".equals(state)) {
            throw new IdempotencyKeyConflictException();
        }
        if (STATE_COMPLETED.equals(state)) {
            return new IdempotencyResponse(readCompleted(redisKey), true);
        }
        if (STATE_PROCESSING.equals(state)) {
            return awaitCompletion(redisKey, fingerprint);
        }
        if (!"OWNED".equals(state)) {
            throw new IllegalStateException("Unexpected idempotency state: " + state);
        }

        try {
            ResponseEntity<byte[]> response = action.get();
            complete(redisKey, fingerprint, response);
            return new IdempotencyResponse(response, false);
        } catch (RuntimeException ex) {
            release(redisKey, fingerprint);
            throw ex;
        }
    }

    private String acquire(String redisKey, String fingerprint) {
        return redisTemplate.execute(acquireScript, Collections.singletonList(redisKey), fingerprint,
                String.valueOf(properties.processingTtlSeconds()));
    }

    private IdempotencyResponse awaitCompletion(String redisKey, String fingerprint) {
        long deadline = System.nanoTime() + Duration.ofMillis(properties.waitTimeoutMs()).toNanos();
        while (System.nanoTime() < deadline) {
            Map<Object, Object> record = redisTemplate.opsForHash().entries(redisKey);
            if (record.isEmpty()) {
                throw new IdempotencyInProgressException();
            }
            if (!fingerprint.equals(record.get("fingerprint"))) {
                throw new IdempotencyKeyConflictException();
            }
            if (STATE_COMPLETED.equals(record.get("state"))) {
                return new IdempotencyResponse(toResponse(record), true);
            }
            try {
                Thread.sleep(properties.pollIntervalMs());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IdempotencyInProgressException();
            }
        }
        throw new IdempotencyInProgressException();
    }

    private ResponseEntity<byte[]> readCompleted(String redisKey) {
        Map<Object, Object> record = redisTemplate.opsForHash().entries(redisKey);
        if (!STATE_COMPLETED.equals(record.get("state"))) {
            throw new IdempotencyInProgressException();
        }
        return toResponse(record);
    }

    private void complete(String redisKey, String fingerprint, ResponseEntity<byte[]> response) {
        String headers = serializeHeaders(response.getHeaders());
        String body = Base64.getEncoder().encodeToString(response.getBody() == null ? new byte[0] : response.getBody());
        redisTemplate.execute(completeScript, Collections.singletonList(redisKey), fingerprint,
                String.valueOf(response.getStatusCode().value()), headers, body,
                String.valueOf(properties.completedTtlSeconds()));
    }

    private void release(String redisKey, String fingerprint) {
        redisTemplate.execute(releaseScript, Collections.singletonList(redisKey), fingerprint);
    }

    private ResponseEntity<byte[]> toResponse(Map<Object, Object> record) {
        try {
            int status = Integer.parseInt(String.valueOf(record.get("status")));
            Map<String, List<String>> headers = objectMapper.readValue(String.valueOf(record.get("headers")), new TypeReference<>() {});
            HttpHeaders httpHeaders = new HttpHeaders();
            headers.forEach((name, values) -> httpHeaders.put(name, values));
            byte[] body = Base64.getDecoder().decode(String.valueOf(record.get("body")));
            return ResponseEntity.status(status).headers(httpHeaders).body(body);
        } catch (Exception ex) {
            throw new IllegalStateException("Stored idempotency response is invalid", ex);
        }
    }

    private String serializeHeaders(HttpHeaders headers) {
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize idempotency response headers", ex);
        }
    }

    public static String fingerprint(String routeName, String method, String path, String query, byte[] body) {
        return sha256(routeName + "\n" + method + "\n" + path + "\n" + (query == null ? "" : query) + "\n"
                + Base64.getEncoder().encodeToString(body == null ? new byte[0] : body));
    }

    private static String sha256(String input) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
