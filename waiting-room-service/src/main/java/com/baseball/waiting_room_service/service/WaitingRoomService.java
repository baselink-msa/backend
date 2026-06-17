package com.baseball.waiting_room_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baseball.waiting_room_service.dto.WaitingRoomPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingRoomService {

    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private static final String QUEUE_KEY_PREFIX = "waiting:";
    private static final String TOKEN_KEY_PREFIX = "waiting:token:";
    private static final String ENTRY_COUNTER_KEY_PREFIX = "waiting:entry:";
    private static final int DEFAULT_MAX_ENTER_PER_MINUTE = 100;
    private static final int DEFAULT_TOKEN_TTL_SECONDS = 300;
    private static final int ENTRY_COUNTER_TTL_SECONDS = 90;
    private static final Path SERVICE_ACCOUNT_TOKEN =
            Path.of("/var/run/secrets/kubernetes.io/serviceaccount/token");
    private static final Path SERVICE_ACCOUNT_NAMESPACE =
            Path.of("/var/run/secrets/kubernetes.io/serviceaccount/namespace");
    private static final Path SERVICE_ACCOUNT_CA =
            Path.of("/var/run/secrets/kubernetes.io/serviceaccount/ca.crt");

    @Value("${app.waiting-room.admission.ticket-service-name:ticket-service}")
    private String ticketServiceName;

    @Value("${app.waiting-room.admission.capacity-per-pod-per-minute:20}")
    private int capacityPerPodPerMinute;

    @Value("${app.waiting-room.admission.fallback-ready-pods:1}")
    private int fallbackReadyPods;

    @Value("${app.waiting-room.admission.kubernetes-enabled:true}")
    private boolean kubernetesCapacityEnabled;

    @Value("${app.waiting-room.admission.cache-ttl-ms:5000}")
    private long capacityCacheTtlMs;

    private volatile long readyPodCountCacheExpiresAt = 0L;
    private volatile int cachedReadyPodCount = 1;

    private static final DefaultRedisScript<Long> CONSUME_ENTRY_SLOT_SCRIPT = new DefaultRedisScript<>("""
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            local max = tonumber(ARGV[1])
            if current >= max then
                return 0
            end

            current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[2])
            end

            if current <= max then
                return 1
            end
            return 0
            """, Long.class);

    /**
     * 1. 대기열 진입 (Redis ZSET에 추가)
     */
    public Long enterWaitingRoom(Long gameId, Long userId) {
        String queueKey = QUEUE_KEY_PREFIX + gameId + ":queue";
        long timestamp = Instant.now().toEpochMilli(); // 현재 시간을 Score로 사용

        // ZADD: 이미 대기열에 있다면 갱신하지 않음 (addIfAbsent)
        redisTemplate.opsForZSet().addIfAbsent(queueKey, userId.toString(), timestamp);

        // ZRANK: 내 순번 조회 (0부터 시작하므로 +1)
        Long rank = redisTemplate.opsForZSet().rank(queueKey, userId.toString());
        return (rank != null) ? rank + 1 : -1L;
    }

    /**
     * 2. 내 대기 순번 조회
     */
    public Long getWaitingPosition(Long gameId, Long userId) {
        String queueKey = QUEUE_KEY_PREFIX + gameId + ":queue";
        Long rank = redisTemplate.opsForZSet().rank(queueKey, userId.toString());
        return (rank != null) ? rank + 1 : -1L;
    }

    public WaitingRoomPolicy getWaitingRoomPolicy(Long gameId) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT max_enter_per_minute, token_ttl_seconds, enabled
                    FROM ticket_schema.waiting_room_policies
                    WHERE game_id = ?
                    """,
                    (rs, rowNum) -> new WaitingRoomPolicy(
                            sanitizePositive(rs.getObject("max_enter_per_minute", Integer.class),
                                    DEFAULT_MAX_ENTER_PER_MINUTE),
                            sanitizePositive(rs.getObject("token_ttl_seconds", Integer.class),
                                    DEFAULT_TOKEN_TTL_SECONDS),
                            sanitizeEnabled(rs.getObject("enabled", Boolean.class))),
                    gameId);
        } catch (EmptyResultDataAccessException e) {
            log.warn("대기열 정책이 없어 기본값을 사용합니다: gameId={}", gameId);
            return defaultPolicy();
        }
    }

    public boolean isRankAllowed(Long rank, WaitingRoomPolicy policy) {
        if (rank == null || rank <= 0) {
            return false;
        }
        if (!policy.enabled()) {
            return true;
        }
        int effectiveEnterPerMinute = getEffectiveEnterPerMinute(policy);
        return effectiveEnterPerMinute > 0 && rank <= effectiveEnterPerMinute;
    }

    public boolean hasEntryCapacity(Long gameId, WaitingRoomPolicy policy) {
        if (!policy.enabled()) {
            return true;
        }

        int effectiveEnterPerMinute = getEffectiveEnterPerMinute(policy);
        if (effectiveEnterPerMinute <= 0) {
            return false;
        }

        String counterKey = currentEntryCounterKey(gameId);
        String currentValue = redisTemplate.opsForValue().get(counterKey);
        long currentCount = parseLongOrDefault(currentValue, 0L);
        return currentCount < effectiveEnterPerMinute;
    }

    public boolean consumeEntrySlot(Long gameId, WaitingRoomPolicy policy) {
        if (!policy.enabled()) {
            return true;
        }

        int effectiveEnterPerMinute = getEffectiveEnterPerMinute(policy);
        if (effectiveEnterPerMinute <= 0) {
            return false;
        }

        Long consumed = redisTemplate.execute(
                CONSUME_ENTRY_SLOT_SCRIPT,
                List.of(currentEntryCounterKey(gameId)),
                String.valueOf(effectiveEnterPerMinute),
                String.valueOf(ENTRY_COUNTER_TTL_SECONDS));
        return consumed != null && consumed == 1L;
    }

    public int getEffectiveEnterPerMinute(WaitingRoomPolicy policy) {
        if (!policy.enabled()) {
            return Integer.MAX_VALUE;
        }

        int policyLimit = Math.max(1, policy.maxEnterPerMinute());
        int readyPodCount = getTicketServiceReadyPodCount();
        int serviceCapacity = readyPodCount <= 0 ? 0 : readyPodCount * Math.max(1, capacityPerPodPerMinute);
        return Math.min(policyLimit, serviceCapacity);
    }

    /**
     * 3. 입장 허용 토큰 발급 (대기열을 통과한 사용자에게 부여)
     */
    public String issueAccessToken(Long gameId, Long userId, int tokenTtlSeconds) {
        String tokenId = UUID.randomUUID().toString();
        String tokenKey = TOKEN_KEY_PREFIX + tokenId;

        // Redis SET EX: 정책에 설정된 시간 동안 유효한 입장 토큰 발급
        redisTemplate.opsForValue().set(tokenKey, userId.toString(), tokenTtlSeconds, TimeUnit.SECONDS);
        
        // 토큰을 발급받은 사용자는 대기열(ZSET)에서 제거
        String queueKey = QUEUE_KEY_PREFIX + gameId + ":queue";
        redisTemplate.opsForZSet().remove(queueKey, userId.toString());

        log.info("입장 토큰 발급 완료: gameId={}, userId={}, tokenId={}", gameId, userId, tokenId);
        return tokenId;
    }

    private String currentEntryCounterKey(Long gameId) {
        long epochMinute = Instant.now().getEpochSecond() / 60;
        return ENTRY_COUNTER_KEY_PREFIX + gameId + ":" + epochMinute;
    }

    private int getTicketServiceReadyPodCount() {
        long now = System.currentTimeMillis();
        if (now < readyPodCountCacheExpiresAt) {
            return cachedReadyPodCount;
        }

        synchronized (this) {
            now = System.currentTimeMillis();
            if (now < readyPodCountCacheExpiresAt) {
                return cachedReadyPodCount;
            }

            cachedReadyPodCount = resolveTicketServiceReadyPodCount();
            readyPodCountCacheExpiresAt = now + Math.max(1000L, capacityCacheTtlMs);
            return cachedReadyPodCount;
        }
    }

    private int resolveTicketServiceReadyPodCount() {
        if (!kubernetesCapacityEnabled || !Files.exists(SERVICE_ACCOUNT_TOKEN)) {
            return Math.max(1, fallbackReadyPods);
        }

        try {
            String host = System.getenv("KUBERNETES_SERVICE_HOST");
            String port = System.getenv().getOrDefault("KUBERNETES_SERVICE_PORT", "443");
            if (host == null || host.isBlank()) {
                return Math.max(1, fallbackReadyPods);
            }

            String namespace = Files.readString(SERVICE_ACCOUNT_NAMESPACE).trim();
            String token = Files.readString(SERVICE_ACCOUNT_TOKEN).trim();
            URI endpointSliceUri = URI.create("https://" + host + ":" + port
                    + "/apis/discovery.k8s.io/v1/namespaces/" + namespace
                    + "/endpointslices?labelSelector=kubernetes.io/service-name%3D" + ticketServiceName);

            HttpRequest request = HttpRequest.newBuilder(endpointSliceUri)
                    .timeout(Duration.ofSeconds(2))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> response = kubernetesHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("ticket-service EndpointSlice 조회 실패: status={}", response.statusCode());
                return Math.max(1, fallbackReadyPods);
            }

            int readyCount = countReadyEndpoints(response.body());
            return readyCount;
        } catch (Exception e) {
            log.warn("ticket-service Ready Pod 수 조회 실패. fallback 값을 사용합니다.", e);
            return Math.max(1, fallbackReadyPods);
        }
    }

    private HttpClient kubernetesHttpClient() throws Exception {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2));

        if (!Files.exists(SERVICE_ACCOUNT_CA)) {
            return builder.build();
        }

        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        Certificate certificate;
        try (InputStream inputStream = Files.newInputStream(SERVICE_ACCOUNT_CA)) {
            certificate = certificateFactory.generateCertificate(inputStream);
        }

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setCertificateEntry("kubernetes", certificate);

        TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
        return builder.sslContext(sslContext).build();
    }

    private int countReadyEndpoints(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        int readyCount = 0;
        for (JsonNode item : root.path("items")) {
            for (JsonNode endpoint : item.path("endpoints")) {
                JsonNode ready = endpoint.path("conditions").path("ready");
                if (!ready.isMissingNode() && !ready.asBoolean(true)) {
                    continue;
                }
                if (endpoint.path("addresses").size() > 0) {
                    readyCount++;
                }
            }
        }
        return readyCount;
    }

    private WaitingRoomPolicy defaultPolicy() {
        return new WaitingRoomPolicy(DEFAULT_MAX_ENTER_PER_MINUTE, DEFAULT_TOKEN_TTL_SECONDS, true);
    }

    private int sanitizePositive(Integer value, int defaultValue) {
        return value != null && value > 0 ? value : defaultValue;
    }

    private boolean sanitizeEnabled(Boolean value) {
        return value == null || value;
    }

    private long parseLongOrDefault(String value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
