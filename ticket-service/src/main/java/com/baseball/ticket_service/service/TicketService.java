package com.baseball.ticket_service.service;

import com.baseball.ticket_service.entity.Reservation;
import com.baseball.ticket_service.repository.ReservationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    private final ReservationRepository reservationRepository;
    private final SqsTemplate sqsTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String CONFIRM_QUEUE_NAME = "ticket-confirm-queue";

    /**
     * 외부에서 호출하는 엔트리 포인트 (트랜잭션을 걸지 않음)
     */
    public Reservation requestReservation(Long userId, Long gameId, Long seatId, String lockId) {
        // 1. DB 저장을 먼저 완벽히 끝내고 커밋까지 완료시킨다.
        Reservation savedReservation = saveReservationInTransaction(userId, gameId, seatId, lockId);

        // 2. 커밋이 확실히 끝난 후 SQS 메시지를 발송한다! (레이스 컨디션 완벽 방어)
        sendSqsMessage(savedReservation, userId, gameId, seatId, lockId);

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