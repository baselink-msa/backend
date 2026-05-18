package com.baseball.waiting_room_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingRoomService {

    private final StringRedisTemplate redisTemplate;

    private static final String QUEUE_KEY_PREFIX = "waiting:";
    private static final String TOKEN_KEY_PREFIX = "waiting:token:";

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

    /**
     * 3. 입장 허용 토큰 발급 (대기열을 통과한 사용자에게 부여)
     */
    public String issueAccessToken(Long gameId, Long userId) {
        String tokenId = UUID.randomUUID().toString();
        String tokenKey = TOKEN_KEY_PREFIX + tokenId;

        // Redis SET EX: 5분(300초) 동안 유효한 입장 토큰 발급
        redisTemplate.opsForValue().set(tokenKey, userId.toString(), 5, TimeUnit.MINUTES);
        
        // 토큰을 발급받은 사용자는 대기열(ZSET)에서 제거
        String queueKey = QUEUE_KEY_PREFIX + gameId + ":queue";
        redisTemplate.opsForZSet().remove(queueKey, userId.toString());

        log.info("입장 토큰 발급 완료: gameId={}, userId={}, tokenId={}", gameId, userId, tokenId);
        return tokenId;
    }
}