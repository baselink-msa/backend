package com.baseball.ticket_service.service;

import com.baseball.ticket_service.entity.GameSeat;
import com.baseball.ticket_service.entity.Reservation;
import com.baseball.ticket_service.event.TicketEventEnvelope;
import com.baseball.ticket_service.event.TicketEventType;
import com.baseball.ticket_service.repository.GameSeatRepository;
import com.baseball.ticket_service.repository.ReservationRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private GameSeatRepository gameSeatRepository;

    @Mock
    private TicketEventOutboxService ticketEventOutboxService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(
                reservationRepository,
                gameSeatRepository,
                ticketEventOutboxService,
                jdbcTemplate,
                new SimpleMeterRegistry());
    }

    @Test
    void requestReservationSavesReservationAndRequestedEvent() {
        Reservation saved = reservation(101L, Reservation.ReservationStatus.PENDING);
        mockReservableGame(1L);
        when(reservationRepository.findByIdempotencyKey("ticket:7:1:12"))
                .thenReturn(Optional.empty());
        when(reservationRepository.save(any(Reservation.class))).thenReturn(saved);

        Reservation result = ticketService.requestReservation(7L, 1L, 12L, "lock-1");

        assertThat(result.getReservationId()).isEqualTo(101L);
        ArgumentCaptor<TicketEventEnvelope> eventCaptor =
                ArgumentCaptor.forClass(TicketEventEnvelope.class);
        verify(ticketEventOutboxService).appendDomainEvent(eventCaptor.capture());
        verify(ticketEventOutboxService).appendTicketConfirmationCommand(
                101L, 7L, 1L, 12L, "lock-1");

        TicketEventEnvelope event = eventCaptor.getValue();
        assertThat(event.eventType()).isEqualTo(TicketEventType.RESERVATION_REQUESTED);
        assertThat(event.aggregateId()).isEqualTo("101");
        assertThat(event.gameId()).isEqualTo(1L);
        assertThat(event.userKey()).isNull();
        assertThat(event.payload())
                .containsEntry("reservationId", 101L)
                .containsEntry("seatId", 12L)
                .containsEntry("status", "PENDING");
    }

    @Test
    void duplicateRequestReturnsExistingReservationWithoutNewEvent() {
        Reservation existing = reservation(101L, Reservation.ReservationStatus.PENDING);
        mockReservableGame(1L);
        when(reservationRepository.findByIdempotencyKey("ticket:7:1:12"))
                .thenReturn(Optional.of(existing));

        Reservation result = ticketService.requestReservation(7L, 1L, 12L, "lock-1");

        assertThat(result).isSameAs(existing);
        verify(reservationRepository, never()).save(any(Reservation.class));
        verify(ticketEventOutboxService, never()).appendDomainEvent(any(TicketEventEnvelope.class));
        verify(ticketEventOutboxService, never()).appendTicketConfirmationCommand(
                any(), any(), any(), any(), any());
    }

    @Test
    void confirmReservationSavesConfirmedEvent() {
        Reservation pending = reservation(101L, Reservation.ReservationStatus.PENDING);
        GameSeat gameSeat = GameSeat.builder()
                .gameSeatId(11L)
                .gameId(1L)
                .seatId(12L)
                .status(GameSeat.GameSeatStatus.LOCKED)
                .price(20000)
                .build();
        when(reservationRepository.findById(101L)).thenReturn(Optional.of(pending));
        when(gameSeatRepository.findByGameIdAndSeatId(1L, 12L))
                .thenReturn(Optional.of(gameSeat));

        Reservation result = ticketService.confirmReservation(101L, 7L);

        assertThat(result.getStatus()).isEqualTo(Reservation.ReservationStatus.CONFIRMED);
        assertThat(gameSeat.getStatus()).isEqualTo(GameSeat.GameSeatStatus.SOLD);

        ArgumentCaptor<TicketEventEnvelope> eventCaptor =
                ArgumentCaptor.forClass(TicketEventEnvelope.class);
        verify(ticketEventOutboxService).appendDomainEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType())
                .isEqualTo(TicketEventType.RESERVATION_CONFIRMED);
        assertThat(eventCaptor.getValue().payload())
                .containsEntry("status", "CONFIRMED");
    }

    private Reservation reservation(Long reservationId, Reservation.ReservationStatus status) {
        return Reservation.builder()
                .reservationId(reservationId)
                .userId(7L)
                .gameId(1L)
                .seatId(12L)
                .status(status)
                .lockId("lock-1")
                .idempotencyKey("ticket:7:1:12")
                .createdAt(LocalDateTime.now().minusSeconds(5))
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private void mockReservableGame(Long gameId) {
        when(jdbcTemplate.queryForObject(
                anyString(),
                any(RowMapper.class),
                eq(gameId)))
                .thenReturn(LocalDateTime.now().plusDays(1));
    }
}
