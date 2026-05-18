package com.baseball.game.dto;

import com.baseball.game.domain.Stadium;

public record StadiumResponse(
        Long stadiumId,
        String name,
        String location,
        Integer capacity
) {
    public static StadiumResponse from(Stadium stadium) {
        return new StadiumResponse(
                stadium.getStadiumId(),
                stadium.getName(),
                stadium.getLocation(),
                stadium.getCapacity()
        );
    }
}
