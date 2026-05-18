package com.baseball.game.dto;

import com.baseball.game.domain.GameSeat;
import com.baseball.game.domain.Seat;

public record GameSeatResponse(
        Long seatId,
        Long gameSeatId,
        Long sectionId,
        String sectionName,
        String seatRow,
        String seatNumber,
        String status,
        Integer price
) {
    public static GameSeatResponse from(GameSeat gameSeat) {
        Seat seat = gameSeat.getSeat();
        return new GameSeatResponse(
                seat.getSeatId(),
                gameSeat.getGameSeatId(),
                seat.getSection().getSectionId(),
                seat.getSection().getSectionName(),
                seat.getSeatRow(),
                seat.getSeatNumber(),
                gameSeat.getStatus().name(),
                gameSeat.getPrice()
        );
    }
}
