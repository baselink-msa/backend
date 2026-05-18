package com.baseball.game.dto;

import com.baseball.game.domain.SeatSection;

public record SeatSectionResponse(
        Long sectionId,
        String sectionName,
        Integer price
) {
    public static SeatSectionResponse from(SeatSection section) {
        return new SeatSectionResponse(
                section.getSectionId(),
                section.getSectionName(),
                section.getPrice()
        );
    }
}
