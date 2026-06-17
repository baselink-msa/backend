package com.baseball.seat_lock_service.service;

import com.baseball.seat_lock_service.entity.GameSeat;
import com.baseball.seat_lock_service.repository.GameSeatRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
public class SeatLockService {

    private final RedissonClient redissonClient;
    private final GameSeatRepository gameSeatRepository;

    // Micrometer 메트릭
    private final Counter seatLockSuccessCounter;
    private final Counter seatLockFailCounter;

    public SeatLockService(RedissonClient redissonClient,
                           GameSeatRepository gameSeatRepository,
                           MeterRegistry meterRegistry) {
        this.redissonClient = redissonClient;
        this.gameSeatRepository = gameSeatRepository;

        // Counter: 좌석 선점 성공
        this.seatLockSuccessCounter = Counter.builder("seat_lock_attempts_total")
                .tag("result", "success")
                .description("좌석 선점 시도 결과별 카운트")
                .register(meterRegistry);

        // Counter: 좌석 선점 실패
        this.seatLockFailCounter = Counter.builder("seat_lock_attempts_total")
                .tag("result", "fail")
                .description("좌석 선점 시도 결과별 카운트")
                .register(meterRegistry);
    }

    @Transactional
    public String lockSeat(Long gameId, Long seatId, Long userId) {
        try {
            String lockId = UUID.randomUUID().toString();
            String key = String.format("seat:%d:%d:lock", gameId, seatId);

            RBucket<String> bucket = redissonClient.getBucket(key);
            boolean isLocked = bucket.setIfAbsent(lockId, Duration.ofMinutes(5));

            if (!isLocked) {
                gameSeatRepository.findByGameIdAndSeatId(gameId, seatId)
                        .filter(gameSeat -> "AVAILABLE".equals(gameSeat.getStatus()))
                        .ifPresent(gameSeat -> {
                            bucket.delete();
                            log.info("만료/취소 후 남은 Redis 좌석 잠금 정리: gameId={}, seatId={}", gameId, seatId);
                        });

                isLocked = bucket.setIfAbsent(lockId, Duration.ofMinutes(5));
            }

            if (!isLocked) {
                // 메트릭: 선점 실패
                seatLockFailCounter.increment();
                throw new IllegalStateException("이미 선점된 좌석입니다.");
            }

            // DB의 game_seats 상태도 LOCKED로 업데이트
            gameSeatRepository.findByGameIdAndSeatId(gameId, seatId)
                    .ifPresent(gameSeat -> {
                        gameSeat.lock();
                        log.info("좌석 잠금 DB 반영: gameId={}, seatId={}", gameId, seatId);
                    });

            // 메트릭: 선점 성공
            seatLockSuccessCounter.increment();

            return lockId;
        } catch (IllegalStateException e) {
            // 이미 위에서 fail 카운트를 올렸으므로 그대로 rethrow
            throw e;
        } catch (Exception e) {
            // 예상치 못한 예외도 fail로 기록
            seatLockFailCounter.increment();
            log.error("좌석 선점 중 예외 발생: gameId={}, seatId={}, error={}", gameId, seatId, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void unlockSeat(Long gameId, Long seatId, String lockId) {
        String key = String.format("seat:%d:%d:lock", gameId, seatId);
        RBucket<String> bucket = redissonClient.getBucket(key);

        if (lockId.equals(bucket.get())) {
            bucket.delete();

            gameSeatRepository.findByGameIdAndSeatId(gameId, seatId)
                    .ifPresent(gameSeat -> {
                        gameSeat.unlock();
                        log.info("좌석 잠금 해제 DB 반영: gameId={}, seatId={}", gameId, seatId);
                    });
        }
    }
}
