package com.baseball.ticket_service.controller;

import com.baseball.ticket_service.entity.Reservation;
import com.baseball.ticket_service.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @PostMapping("/reserve")
    public ResponseEntity<Reservation> reserve(
            @RequestHeader(value = "X-User-Id", defaultValue = "100") Long userId,
            @RequestParam("gameId") Long gameId,
            @RequestParam("seatId") Long seatId,
            @RequestParam("lockId") String lockId) {
        
        // PENDING 상태로 DB 저장 및 SQS 메시지 발송
        Reservation reservation = ticketService.requestReservation(userId, gameId, seatId, lockId);
        
        return ResponseEntity.ok(reservation);
    }

    @GetMapping("/{reservationId}")
    public ResponseEntity<Reservation> getReservation(@PathVariable Long reservationId) {
        Reservation reservation = ticketService.getReservation(reservationId);
        return ResponseEntity.ok(reservation);
    }

    @GetMapping("/my")
    public ResponseEntity<List<Reservation>> getMyReservations(
            @RequestHeader(value = "X-User-Id", defaultValue = "100") Long userId) {
        List<Reservation> reservations = ticketService.getMyReservations(userId);
        return ResponseEntity.ok(reservations);
    }
}