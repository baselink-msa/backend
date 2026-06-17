package com.baseball.waiting_room_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baseball.waiting_room_service.dto.WaitingRoomPolicy;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

@Slf4j
@Service
public class WaitingRoomService {

    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    // Micrometer 메트릭
    private final AtomicLong activeUsersGauge;
    private final Counter passedCounter;

    private static final String QUEUE_KEY_PREFIX = "waiting:";
    private static final String HEARTBEAT_KEY_PREFIX = "waiting:heartbeat:";
    private static final String TOKEN_KEY_PREFIX = "waiting:token:";
    private static final String ACTIVE_TOKEN_KEY_PREFIX = "waiting:active:";
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

    public WaitingRoomService(StringRedisTemplate redisTemplate,
                              JdbcTemplate jdbcTemplate,
                              ObjectMapper objectMapper,
                              MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;

        // Gauge: 현재 대기 중인 유저 수
        this.activeUsersGauge = new AtomicLong(0);
        Gauge.builder("waiting_queue_active_users", activeUsersGauge, AtomicLong::doubleValue)
                .description("현재 대기열에서 대기 중인 유저 수")
                .register(meterRegistry);

        // Counter: 대기열 통과 수
        this.passedCounter = Counter.builder("waiting_queue_passed_total")
                .description("대기열을 통과하여 입장 토큰을 발급받은 횟수")
                .register(meterRegistry);
    }

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

    @Value("${app.waiting-room.admission.max-ticket-service-pods:10}")
    private int maxTicketServicePods;

    @Value("${app.waiting-room.admission.scale-target-waiting-users-per-pod:20}")
    private int scaleTargetWaitingUsersPerPod;

    @Value("${app.waiting-room.admission.scale-warmup-seconds:60}")
    private int scaleWarmupSeconds;

    @Value("${app.waiting-room.queue.stale-entry-ttl-ms:600000}")
    private long staleQueueEntryTtlMs;

    private volatile long readyPodCountCacheExpiresAt = 0L;
    private volatile int cachedReadyPodCount = 1;

    private static final DefaultRedisScript<Long> ISSUE_TOKEN_SCRIPT = new DefaultRedisScript<>("""
            local used = tonumber(redis.call('GET', KEYS[1]) or '0')
            local capacity = tonumber(ARGV[2])
            if used >= capacity then
                return 0
            end

            local new_used = redis.call('INCR', KEYS[1])
            if new_used == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[7])
            end
            if new_used > capacity then
                redis.call('DECR', KEYS[1])
                return 0
            end

            redis.call('ZREMRANGEBYSCORE', KEYS[2], 0, ARGV[1])
            redis.call('SET', KEYS[3], ARGV[5], 'EX', ARGV[6])
            redis.call('ZADD', KEYS[2], ARGV[4], ARGV[3])
            return 1
            """, Long.class);

    /**
     * 1. 대기열 진입 (Redis ZSET에 추가)
     */
    public Long enterWaitingRoom(Long gameId, Long userId) {
        String queueKey = queueKey(gameId);
        String heartbeatKey = heartbeatKey(gameId);
        long timestamp = Instant.now().toEpochMilli(); // 현재 시간을 Score로 사용

        pruneStaleQueueMembers(queueKey, heartbeatKey, timestamp);

        // ZADD: 이미 대기열에 있다면 갱신하지 않음 (addIfAbsent)
        redisTemplate.opsForZSet().addIfAbsent(queueKey, userId.toString(), timestamp);
        redisTemplate.opsForZSet().add(heartbeatKey, userId.toString(), timestamp);

        // Gauge 업데이트: 해당 게임 대기열 사이즈 반영
        Long size = redisTemplate.opsForZSet().zCard(queueKey);
        if (size != null) {
            activeUsersGauge.set(size);
        }

        // ZRANK: 내 순번 조회 (0부터 시작하므로 +1)
        Long rank = redisTemplate.opsForZSet().rank(queueKey, userId.toString());
        return (rank != null) ? rank + 1 : -1L;
    }

    /**
     * 2. 내 대기 순번 조회
     */
    public Long getWaitingPosition(Long gameId, Long userId) {
        String queueKey = queueKey(gameId);
        String heartbeatKey = heartbeatKey(gameId);
        pruneStaleQueueMembers(queueKey, heartbeatKey, Instant.now().toEpochMilli());

        Long rank = redisTemplate.opsForZSet().rank(queueKey, userId.toString());
        if (rank != null) {
            redisTemplate.opsForZSet().add(heartbeatKey, userId.toString(), Instant.now().toEpochMilli());
        }
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

        return getCurrentMinuteRemainingSlots(gameId, effectiveEnterPerMinute) > 0;
    }

    public int getEffectiveEnterPerMinute(WaitingRoomPolicy policy) {
        if (!policy.enabled()) {
            return Integer.MAX_VALUE;
        }

        int policyLimit = Math.max(1, policy.maxEnterPerMinute());
        int readyPodCount = getTicketServiceReadyPodCount();
        return calculateEnterPerMinute(policyLimit, readyPodCount);
    }

    public AdmissionDecision evaluateAdmission(Long gameId, Long rank, WaitingRoomPolicy policy) {
        long nowMillis = Instant.now().toEpochMilli();
        if (!policy.enabled()) {
            return new AdmissionDecision(
                    rank != null && rank > 0,
                    rank != null && rank > 0,
                    true,
                    0L,
                    nowMillis,
                    1,
                    Integer.MAX_VALUE,
                    getTicketServiceReadyPodCount(),
                    getTicketServiceReadyPodCount(),
                    Integer.MAX_VALUE,
                    Integer.MAX_VALUE,
                    Long.MAX_VALUE);
        }

        int policyLimit = Math.max(1, policy.maxEnterPerMinute());
        int readyPodCount = getTicketServiceReadyPodCount();
        int projectedPodCount = getProjectedTicketServicePodCount(gameId, readyPodCount);
        int currentCapacityPerMinute = calculateEnterPerMinute(policyLimit, readyPodCount);
        int projectedCapacityPerMinute = calculateEnterPerMinute(policyLimit, projectedPodCount);
        long currentMinuteRemainingSlots = getCurrentMinuteRemainingSlots(gameId, currentCapacityPerMinute);
        boolean rankAllowed = rank != null && rank > 0 && rank <= currentCapacityPerMinute;
        boolean capacityAvailable = currentMinuteRemainingSlots > 0;
        boolean canEnter = rank != null && rank > 0 && rank <= currentMinuteRemainingSlots;
        long estimatedWaitSeconds = estimateWaitSeconds(
                rank,
                canEnter,
                currentCapacityPerMinute,
                projectedCapacityPerMinute,
                currentMinuteRemainingSlots);
        int nextCheckAfterSeconds = estimateNextCheckAfterSeconds(canEnter, estimatedWaitSeconds);

        return new AdmissionDecision(
                rankAllowed,
                capacityAvailable,
                canEnter,
                estimatedWaitSeconds,
                nowMillis,
                nextCheckAfterSeconds,
                policyLimit,
                readyPodCount,
                projectedPodCount,
                currentCapacityPerMinute,
                projectedCapacityPerMinute,
                currentMinuteRemainingSlots);
    }

    private long estimateWaitSeconds(Long rank, boolean canEnter, int currentCapacityPerMinute,
                                     int projectedCapacityPerMinute, long currentMinuteRemainingSlots) {
        if (canEnter) {
            return 0L;
        }
        if (rank == null || rank <= 0 || currentCapacityPerMinute <= 0) {
            return 60L;
        }

        long peopleAhead = Math.max(0, rank - 1);
        if (currentCapacityPerMinute <= 0) {
            return 60L;
        }

        if (peopleAhead < currentMinuteRemainingSlots) {
            return 0L;
        }

        long remainingAhead = peopleAhead - currentMinuteRemainingSlots;
        long seconds = secondsUntilNextAdmissionWindow();
        if (remainingAhead <= 0) {
            return seconds;
        }

        if (projectedCapacityPerMinute <= currentCapacityPerMinute) {
            return seconds + minutesToDrain(remainingAhead, currentCapacityPerMinute) * 60L;
        }

        long warmupSeconds = Math.max(0, scaleWarmupSeconds);
        long minutesBeforeScale = warmupSeconds == 0 ? 0 : (long) Math.ceil(warmupSeconds / 60.0);
        long handledBeforeScale = minutesBeforeScale * currentCapacityPerMinute;
        if (remainingAhead <= handledBeforeScale) {
            return seconds + minutesToDrain(remainingAhead, currentCapacityPerMinute) * 60L;
        }

        remainingAhead -= handledBeforeScale;
        return seconds + minutesBeforeScale * 60L
                + minutesToDrain(remainingAhead, projectedCapacityPerMinute) * 60L;
    }

    public record AdmissionDecision(
            boolean rankAllowed,
            boolean capacityAvailable,
            boolean canEnter,
            long estimatedWaitSeconds,
            long serverTimeEpochMillis,
            int nextCheckAfterSeconds,
            int policyMaxEnterPerMinute,
            int currentReadyPodCount,
            int projectedReadyPodCount,
            int effectiveEnterPerMinute,
            int projectedEnterPerMinute,
            long currentMinuteRemainingSlots) {
    }

    /**
     * 3. 입장 허용 토큰 발급 (대기열을 통과한 사용자에게 부여)
     */
    public String issueAccessToken(Long gameId, Long userId, int tokenTtlSeconds) {
        String tokenId = UUID.randomUUID().toString();
        String tokenKey = TOKEN_KEY_PREFIX + tokenId;
        WaitingRoomPolicy policy = getWaitingRoomPolicy(gameId);
        int effectiveEnterPerMinute = getEffectiveEnterPerMinute(policy);
        long nowMillis = Instant.now().toEpochMilli();
        long expiresAtMillis = nowMillis + tokenTtlSeconds * 1000L;
        Long acquired = redisTemplate.execute(
                ISSUE_TOKEN_SCRIPT,
                List.of(entryCounterKey(gameId, currentEpochMinute()), activeTokenKey(gameId), tokenKey),
                String.valueOf(nowMillis),
                String.valueOf(effectiveEnterPerMinute),
                tokenId,
                String.valueOf(expiresAtMillis),
                userId.toString(),
                String.valueOf(tokenTtlSeconds),
                String.valueOf(ENTRY_COUNTER_TTL_SECONDS));
        if (acquired == null || acquired != 1L) {
            return null;
        }
        
        // 토큰을 발급받은 사용자는 대기열(ZSET)에서 제거
        String queueKey = queueKey(gameId);
        redisTemplate.opsForZSet().remove(queueKey, userId.toString());
        redisTemplate.opsForZSet().remove(heartbeatKey(gameId), userId.toString());

        // 메트릭: 대기열 통과 카운트 증가
        passedCounter.increment();

        // Gauge 업데이트: 대기열 사이즈 갱신
        Long size = redisTemplate.opsForZSet().zCard(queueKey);
        if (size != null) {
            activeUsersGauge.set(size);
        }

        log.info("입장 토큰 발급 완료: gameId={}, userId={}, tokenId={}", gameId, userId, tokenId);
        return tokenId;
    }

    public boolean releaseAccessToken(Long gameId, Long userId, String tokenId) {
        if (tokenId == null || tokenId.isBlank()) {
            return false;
        }

        String tokenKey = TOKEN_KEY_PREFIX + tokenId;
        String storedUserId = redisTemplate.opsForValue().get(tokenKey);
        if (storedUserId != null && !storedUserId.equals(userId.toString())) {
            return false;
        }

        redisTemplate.delete(tokenKey);
        redisTemplate.opsForZSet().remove(activeTokenKey(gameId), tokenId);
        log.info("좌석선택 세션 슬롯 반납: gameId={}, userId={}, tokenId={}", gameId, userId, tokenId);
        return true;
    }

    private String queueKey(Long gameId) {
        return QUEUE_KEY_PREFIX + gameId + ":queue";
    }

    private String heartbeatKey(Long gameId) {
        return HEARTBEAT_KEY_PREFIX + gameId + ":queue";
    }

    private String activeTokenKey(Long gameId) {
        return ACTIVE_TOKEN_KEY_PREFIX + gameId + ":tokens";
    }

    private String entryCounterKey(Long gameId, long epochMinute) {
        return ENTRY_COUNTER_KEY_PREFIX + gameId + ":" + epochMinute;
    }

    private void pruneStaleQueueMembers(String queueKey, String heartbeatKey, long nowMillis) {
        if (staleQueueEntryTtlMs <= 0) {
            return;
        }

        long cutoffMillis = nowMillis - staleQueueEntryTtlMs;
        Set<String> staleMembers = new HashSet<>();

        Set<String> staleHeartbeatMembers = redisTemplate.opsForZSet()
                .rangeByScore(heartbeatKey, 0, cutoffMillis);
        if (staleHeartbeatMembers != null) {
            staleMembers.addAll(staleHeartbeatMembers);
        }

        // 이전 버전에서 heartbeat 없이 쌓인 대기열 데이터 정리.
        Set<String> oldQueueMembers = redisTemplate.opsForZSet()
                .rangeByScore(queueKey, 0, cutoffMillis);
        if (oldQueueMembers != null) {
            for (String member : oldQueueMembers) {
                if (redisTemplate.opsForZSet().score(heartbeatKey, member) == null) {
                    staleMembers.add(member);
                }
            }
        }

        if (staleMembers.isEmpty()) {
            return;
        }

        Object[] members = staleMembers.toArray();
        redisTemplate.opsForZSet().remove(queueKey, members);
        redisTemplate.opsForZSet().remove(heartbeatKey, members);
        log.info("만료된 대기열 사용자 정리: queueKey={}, count={}", queueKey, staleMembers.size());
    }

    private long getCurrentMinuteRemainingSlots(Long gameId, int currentCapacityPerMinute) {
        String counterValue = redisTemplate.opsForValue().get(entryCounterKey(gameId, currentEpochMinute()));
        long usedThisMinute = parseLongOrDefault(counterValue, 0L);
        return Math.max(0L, currentCapacityPerMinute - usedThisMinute);
    }

    private int calculateEnterPerMinute(int policyLimit, int podCount) {
        int serviceCapacity = podCount <= 0 ? 0 : podCount * Math.max(1, capacityPerPodPerMinute);
        return Math.min(Math.max(1, policyLimit), serviceCapacity);
    }

    private int getProjectedTicketServicePodCount(Long gameId, int currentReadyPodCount) {
        long waitingCount = getWaitingQueueSize(gameId);
        int demandBasedPodCount = (int) Math.ceil(
                (double) waitingCount / Math.max(1, scaleTargetWaitingUsersPerPod));
        return Math.min(
                Math.max(1, maxTicketServicePods),
                Math.max(currentReadyPodCount, demandBasedPodCount));
    }

    private long getWaitingQueueSize(Long gameId) {
        Long size = redisTemplate.opsForZSet().zCard(queueKey(gameId));
        return size == null ? 0L : size;
    }

    private long secondsUntilNextAdmissionWindow() {
        long secondOfMinute = Instant.now().getEpochSecond() % 60;
        return secondOfMinute == 0 ? 60L : 60L - secondOfMinute;
    }

    private long currentEpochMinute() {
        return Instant.now().getEpochSecond() / 60;
    }

    private long minutesToDrain(long waitingUsers, int capacityPerMinute) {
        if (waitingUsers <= 0) {
            return 0L;
        }
        return (long) Math.ceil((double) waitingUsers / Math.max(1, capacityPerMinute));
    }

    private int estimateNextCheckAfterSeconds(boolean canEnter, long estimatedWaitSeconds) {
        if (canEnter || estimatedWaitSeconds <= 10) {
            return 1;
        }
        if (estimatedWaitSeconds <= 60) {
            return 2;
        }
        return 3;
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
