package com.baseball.ticket_worker_service.dto;

import lombok.Data;

@Data
public class TicketConfirmMessage {
    private Long reservationId;
    private Long userId;
    private Long gameId;
    private Long seatId;
    private String lockId;
}