package com.baseball.ticket_worker_service.service;

import com.baseball.ticket_worker_service.dto.TicketConfirmMessage;
import com.baseball.ticket_worker_service.entity.Reservation;
import com.baseball.ticket_worker_service.repository.ReservationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketWorkerListener {

    private final ReservationRepository reservationRepository;
    private final ObjectMapper objectMapper = new ObjectMapper(); // JSON 파싱을 위한 매퍼 추가

    @Transactional
    @SqsListener("ticket-confirm-queue")
    public void processTicketConfirmation(String messageJson) { // String으로 안전하게 수신
        try {
            // 1. JSON 문자열을 DTO 객체로 수동 변환
            TicketConfirmMessage message = objectMapper.readValue(messageJson, TicketConfirmMessage.class);
            log.info("SQS 메시지 파싱 완료: reservationId={}", message.getReservationId());

            // 2. DB에서 예약 정보 조회
            Reservation reservation = reservationRepository.findById(message.getReservationId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약입니다. ID: " + message.getReservationId()));

            // 3. 멱등성 검증 (이미 확정/취소된 예약이면 중복 처리 방지)
            if (reservation.getStatus() != Reservation.ReservationStatus.PENDING) {
                log.warn("PENDING이 아닌 예약입니다. 메시지 무시. reservationId={}, status={}", 
                    message.getReservationId(), reservation.getStatus());
                return; 
            }

            // 4. 좌석 유효성 검증 완료 로그 (확정은 사용자가 직접 수행)
            log.info("예매 요청 검증 완료 (사용자 확정 대기): reservationId={}, userId={}", 
                message.getReservationId(), message.getUserId());

        } catch (Exception e) {
            log.error("메시지 처리 중 에러 발생: {}", e.getMessage(), e);
            // 중요: 런타임 예외를 던져야 SQS가 실패를 인지하고 메시지를 큐에 남기거나 DLQ로 보냅니다.
            throw new RuntimeException("메시지 처리 실패로 인한 롤백", e);
        }
    }
}