package com.baseball.seat_lock_service.service;

import com.baseball.seat_lock_service.entity.GameSeat;
import com.baseball.seat_lock_service.repository.GameSeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatLockService {

    private final RedissonClient redissonClient;
    private final GameSeatRepository gameSeatRepository;

    @Transactional
    public String lockSeat(Long gameId, Long seatId, Long userId) {
        String lockId = UUID.randomUUID().toString();
        String key = String.format("seat:%d:%d:lock", gameId, seatId);

        RBucket<String> bucket = redissonClient.getBucket(key);
        boolean isLocked = bucket.setIfAbsent(lockId, Duration.ofMinutes(5));

        if (!isLocked) {
            throw new IllegalStateException("이미 선점된 좌석입니다.");
        }

        // DB의 game_seats 상태도 LOCKED로 업데이트
        gameSeatRepository.findByGameIdAndSeatId(gameId, seatId)
                .ifPresent(gameSeat -> {
                    gameSeat.lock();
                    log.info("좌석 잠금 DB 반영: gameId={}, seatId={}", gameId, seatId);
                });

        return lockId;
    }

    @Transactional
    public void unlockSeat(Long gameId, Long seatId, String lockId) {
        String key = String.format("seat:%d:%d:lock", gameId, seatId);
        RBucket<String> bucket = redissonClient.getBucket(key);

        if (lockId.equals(bucket.get())) {
            bucket.delete();

            // DB의 game_seats 상태도 AVAILABLE로 복구
            gameSeatRepository.findByGameIdAndSeatId(gameId, seatId)
                    .ifPresent(gameSeat -> {
                        gameSeat.unlock();
                        log.info("좌석 잠금 해제 DB 반영: gameId={}, seatId={}", gameId, seatId);
                    });
        }
    }
}
