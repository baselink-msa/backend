package com.baseball.seat_lock_service.service;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class SeatLockService {

    private final RedissonClient redissonClient;

    public String lockSeat(Long gameId, Long seatId, Long userId) {
        String lockId = UUID.randomUUID().toString();
        String key = String.format("seat:%d:%d:lock", gameId, seatId);

        RBucket<String> bucket = redissonClient.getBucket(key);
        // trySet 대신 최신 문법인 setIfAbsent 사용
        boolean isLocked = bucket.setIfAbsent(lockId, Duration.ofMinutes(5));

        if (!isLocked) {
            throw new IllegalStateException("이미 선점된 좌석입니다.");
        }

        return lockId;
    }

    public void unlockSeat(Long gameId, Long seatId, String lockId) {
        String key = String.format("seat:%d:%d:lock", gameId, seatId);
        RBucket<String> bucket = redissonClient.getBucket(key);
        
        if (lockId.equals(bucket.get())) {
            bucket.delete();
        }
    }
}
