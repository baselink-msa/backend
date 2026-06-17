package com.baseball.waiting_room_service.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class WaitingRoomService {

    private final StringRedisTemplate redisTemplate;

    // Micrometer 메트릭
    private final AtomicLong activeUsersGauge;
    private final Counter passedCounter;

    private static final String QUEUE_KEY_PREFIX = "waiting:";
    private static final String TOKEN_KEY_PREFIX = "waiting:token:";

    public WaitingRoomService(StringRedisTemplate redisTemplate, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;

        // Gauge: 현재 대기 중인 유저 수 (전체 게임 대기열의 합산 또는 대표 큐 사이즈)
        this.activeUsersGauge = new AtomicLong(0);
        Gauge.builder("waiting_queue_active_users", activeUsersGauge, AtomicLong::doubleValue)
                .description("현재 대기열에서 대기 중인 유저 수")
                .register(meterRegistry);

        // Counter: 대기열 통과 수
        this.passedCounter = Counter.builder("waiting_queue_passed_total")
                .description("대기열을 통과하여 입장 토큰을 발급받은 횟수")
                .register(meterRegistry);
    }

    /**
     * 1. 대기열 진입 (Redis ZSET에 추가)
     */
    public Long enterWaitingRoom(Long gameId, Long userId) {
        String queueKey = QUEUE_KEY_PREFIX + gameId + ":queue";
        long timestamp = Instant.now().toEpochMilli();

        redisTemplate.opsForZSet().addIfAbsent(queueKey, userId.toString(), timestamp);

        // Gauge 업데이트: 해당 게임 대기열 사이즈 반영
        Long size = redisTemplate.opsForZSet().zCard(queueKey);
        if (size != null) {
            activeUsersGauge.set(size);
        }

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

    /**
     * 3. 입장 허용 토큰 발급 (대기열을 통과한 사용자에게 부여)
     */
    public String issueAccessToken(Long gameId, Long userId) {
        String tokenId = UUID.randomUUID().toString();
        String tokenKey = TOKEN_KEY_PREFIX + tokenId;

        redisTemplate.opsForValue().set(tokenKey, userId.toString(), 5, TimeUnit.MINUTES);

        // 대기열에서 제거
        String queueKey = QUEUE_KEY_PREFIX + gameId + ":queue";
        redisTemplate.opsForZSet().remove(queueKey, userId.toString());

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
}
