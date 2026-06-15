package com.baseball.ticket_service.service;

import com.baseball.ticket_service.entity.GameSeat;
import com.baseball.ticket_service.entity.Reservation;
import com.baseball.ticket_service.repository.GameSeatRepository;
import com.baseball.ticket_service.repository.ReservationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TicketService {

    private final ReservationRepository reservationRepository;
    private final GameSeatRepository gameSeatRepository;
    private final SqsTemplate sqsTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Micrometer 메트릭
    private final Counter bookingRequestedCounter;
    private final Counter bookingConfirmedCounter;
    private final Counter bookingCanceledCounter;
    private final Timer bookingDurationTimer;

    private static final String CONFIRM_QUEUE_NAME = "ticket-confirm-queue";

    public TicketService(ReservationRepository reservationRepository,
                         GameSeatRepository gameSeatRepository,
                         SqsTemplate sqsTemplate,
                         MeterRegistry meterRegistry) {
        this.reservationRepository = reservationRepository;
        this.gameSeatRepository = gameSeatRepository;
        this.sqsTemplate = sqsTemplate;

        // 메트릭 정의
        this.bookingRequestedCounter = Counter.builder("ticket_booking_total")
                .tag("status", "requested")
                .description("예매 요청 수")
                .register(meterRegistry);

        this.bookingConfirmedCounter = Counter.builder("ticket_booking_total")
                .tag("status", "confirmed")
                .description("예매 확정 수")
                .register(meterRegistry);

        this.bookingCanceledCounter = Counter.builder("ticket_booking_total")
                .tag("status", "canceled")
                .description("예매 취소 수")
                .register(meterRegistry);

        this.bookingDurationTimer = Timer.builder("ticket_booking_duration_seconds")
                .description("예매 요청(PENDING)부터 확정(CONFIRMED)까지 소요 시간")
                .register(meterRegistry);
    }

    /**
     * 외부에서 호출하는 엔트리 포인트 (트랜잭션을 걸지 않음)
     */
    public Reservation requestReservation(Long userId, Long gameId, Long seatId, String lockId) {
        // 1. DB 저장을 먼저 완벽히 끝내고 커밋까지 완료시킨다.
        Reservation savedReservation = saveReservationInTransaction(userId, gameId, seatId, lockId);

        // 2. 커밋이 확실히 끝난 후 SQS 메시지를 발송한다! (레이스 컨디션 완벽 방어)
        sendSqsMessage(savedReservation, userId, gameId, seatId, lockId);

        // 메트릭: 예매 요청 카운트
        bookingRequestedCounter.increment();

        return savedReservation;
    }

    /**
     * DB 저장 및 커밋만 담당하는 독립된 트랜잭션 메서드
     */
    @Transactional
    public Reservation saveReservationInTransaction(Long userId, Long gameId, Long seatId, String lockId) {
        String idempotencyKey = String.format("ticket:%d:%d:%d", userId, gameId, seatId);

        Reservation reservation = Reservation.builder()
                .userId(userId)
                .gameId(gameId)
                .seatId(seatId)
                .status(Reservation.ReservationStatus.PENDING)
                .lockId(lockId)
                .idempotencyKey(idempotencyKey)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return reservationRepository.save(reservation);
    }

    /**
     * 예매 상세 조회
     */
    @Transactional(readOnly = true)
    public Reservation getReservation(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예매 정보를 찾을 수 없습니다. id=" + reservationId));
    }

    /**
     * 내 예매 목록 조회
     */
    @Transactional(readOnly = true)
    public List<Reservation> getMyReservations(Long userId) {
        return reservationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 예매 확정 (사용자가 직접 확정 버튼을 누를 때)
     */
    @Transactional
    public Reservation confirmReservation(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예매 정보를 찾을 수 없습니다. id=" + reservationId));
        if (!reservation.getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인의 예매만 확정할 수 있습니다.");
        }
        if (reservation.getStatus() != Reservation.ReservationStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태의 예매만 확정할 수 있습니다. 현재: " + reservation.getStatus());
        }
        reservation.confirm();
        reservationRepository.save(reservation);

        // game_seats 상태를 SOLD로 변경
        gameSeatRepository.findByGameIdAndSeatId(reservation.getGameId(), reservation.getSeatId())
                .ifPresent(GameSeat::markSold);

        // 메트릭: 예매 확정 카운트
        bookingConfirmedCounter.increment();

        // 메트릭: PENDING → CONFIRMED 소요 시간 기록 (createdAt 기준)
        if (reservation.getCreatedAt() != null) {
            Duration duration = Duration.between(reservation.getCreatedAt(), LocalDateTime.now());
            bookingDurationTimer.record(duration);
        }

        return reservation;
    }

    /**
     * 예매 취소
     */
    @Transactional
    public Reservation cancelReservation(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예매 정보를 찾을 수 없습니다. id=" + reservationId));
        if (!reservation.getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인의 예매만 취소할 수 있습니다.");
        }
        if (reservation.getStatus() == Reservation.ReservationStatus.CANCELED) {
            throw new IllegalStateException("이미 취소된 예매입니다.");
        }
        reservation.cancel();
        reservationRepository.save(reservation);

        // game_seats 상태를 AVAILABLE로 복구
        gameSeatRepository.findByGameIdAndSeatId(reservation.getGameId(), reservation.getSeatId())
                .ifPresent(GameSeat::markAvailable);

        // 메트릭: 예매 취소 카운트
        bookingCanceledCounter.increment();

        return reservation;
    }

    /**
     * SQS 메시지 발송 메서드
     */
    private void sendSqsMessage(Reservation savedReservation, Long userId, Long gameId, Long seatId, String lockId) {
        try {
            Map<String, Object> messagePayload = new HashMap<>();
            messagePayload.put("reservationId", savedReservation.getReservationId());
            messagePayload.put("userId", userId);
            messagePayload.put("gameId", gameId);
            messagePayload.put("seatId", seatId);
            messagePayload.put("lockId", lockId);

            String jsonMessage = objectMapper.writeValueAsString(messagePayload);

            sqsTemplate.send(to -> to.queue(CONFIRM_QUEUE_NAME).payload(jsonMessage));
            log.info("예매 확정 요청 메시지 발송 완료: reservationId={}", savedReservation.getReservationId());
        } catch (Exception e) {
            log.error("SQS 메시지 발송 실패. reservationId={}. 사유: {}", savedReservation.getReservationId(), e.getMessage());
        }
    }
}
