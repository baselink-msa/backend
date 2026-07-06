package com.baseball.ticket_service.service;

import com.baseball.ticket_service.entity.GameSeat;
import com.baseball.ticket_service.entity.Reservation;
import com.baseball.ticket_service.event.TicketEventEnvelope;
import com.baseball.ticket_service.event.TicketEventType;
import com.baseball.ticket_service.repository.GameSeatRepository;
import com.baseball.ticket_service.repository.ReservationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class TicketService {

    private final ReservationRepository reservationRepository;
    private final GameSeatRepository gameSeatRepository;
    private final TicketEventOutboxService ticketEventOutboxService;
    private final JdbcTemplate jdbcTemplate;

    // Micrometer 메트릭
    private final Counter bookingRequestedCounter;
    private final Counter bookingConfirmedCounter;
    private final Counter bookingCanceledCounter;
    private final Timer bookingDurationTimer;

    public TicketService(ReservationRepository reservationRepository,
                         GameSeatRepository gameSeatRepository,
                         TicketEventOutboxService ticketEventOutboxService,
                         JdbcTemplate jdbcTemplate,
                         MeterRegistry meterRegistry) {
        this.reservationRepository = reservationRepository;
        this.gameSeatRepository = gameSeatRepository;
        this.ticketEventOutboxService = ticketEventOutboxService;
        this.jdbcTemplate = jdbcTemplate;

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

    @Transactional
    public Reservation requestReservation(Long userId, Long gameId, Long seatId, String lockId) {
        validateReservableGame(gameId);

        String idempotencyKey = String.format("ticket:%d:%d:%d", userId, gameId, seatId);
        Reservation existingReservation = reservationRepository.findByIdempotencyKey(idempotencyKey)
                .orElse(null);
        if (existingReservation != null) {
            return existingReservation;
        }

        Reservation savedReservation = reservationRepository.save(Reservation.builder()
                .userId(userId)
                .gameId(gameId)
                .seatId(seatId)
                .status(Reservation.ReservationStatus.PENDING)
                .lockId(lockId)
                .idempotencyKey(idempotencyKey)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        ticketEventOutboxService.appendDomainEvent(TicketEventEnvelope.reservation(
                TicketEventType.RESERVATION_REQUESTED,
                savedReservation.getReservationId(),
                gameId,
                null,
                Map.of(
                        "reservationId", savedReservation.getReservationId(),
                        "seatId", seatId,
                        "status", savedReservation.getStatus().name())));

        ticketEventOutboxService.appendTicketConfirmationCommand(
                savedReservation.getReservationId(),
                userId,
                gameId,
                seatId,
                lockId);
        bookingRequestedCounter.increment();
        return savedReservation;
    }

    private void validateReservableGame(Long gameId) {
        LocalDateTime gameStartTime;
        try {
            gameStartTime = jdbcTemplate.queryForObject(
                    "SELECT game_start_time FROM game_schema.games WHERE game_id = ?",
                    (rs, rowNum) -> {
                        Timestamp timestamp = rs.getTimestamp("game_start_time");
                        return timestamp == null ? null : timestamp.toLocalDateTime();
                    },
                    gameId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "경기를 찾을 수 없습니다. gameId=" + gameId);
        }

        if (gameStartTime == null || !gameStartTime.isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "이미 시작했거나 종료된 경기는 예매할 수 없습니다.");
        }
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

        long pendingDurationSeconds = reservation.getCreatedAt() == null
                ? 0
                : Math.max(0, Duration.between(reservation.getCreatedAt(), LocalDateTime.now()).toSeconds());
        ticketEventOutboxService.appendDomainEvent(TicketEventEnvelope.reservation(
                TicketEventType.RESERVATION_CONFIRMED,
                reservation.getReservationId(),
                reservation.getGameId(),
                null,
                Map.of(
                        "reservationId", reservation.getReservationId(),
                        "seatId", reservation.getSeatId(),
                        "status", reservation.getStatus().name(),
                        "pendingDurationSeconds", pendingDurationSeconds)));

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

}
