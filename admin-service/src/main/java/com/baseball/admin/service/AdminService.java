package com.baseball.admin.service;

import com.baseball.admin.common.BusinessException;
import com.baseball.admin.domain.*;
import com.baseball.admin.dto.*;
import com.baseball.admin.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final StadiumRepository stadiumRepository;
    private final GameRepository gameRepository;
    private final SeatSectionRepository seatSectionRepository;
    private final SeatRepository seatRepository;
    private final GameSeatRepository gameSeatRepository;
    private final WaitingRoomPolicyRepository waitingRoomPolicyRepository;
    private final AlcoholMenuRepository alcoholMenuRepository;
    private final FaqRepository faqRepository;

    @Transactional
    public Map<String, Object> createGame(CreateGameRequest req) {
        if (!stadiumRepository.existsById(req.stadiumId())) {
            throw new BusinessException("STADIUM_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "구장을 찾을 수 없습니다. stadiumId=" + req.stadiumId());
        }
        Game game = gameRepository.save(Game.builder()
                .homeTeamName(req.homeTeamName())
                .awayTeamName(req.awayTeamName())
                .stadiumId(req.stadiumId())
                .gameStartTime(req.gameStartTime())
                .ticketOpenTime(req.ticketOpenTime())
                .status(GameStatus.SCHEDULED)
                .build());
        return Map.<String, Object>of("gameId", game.getGameId(), "status", game.getStatus().name());
    }

    @Transactional
    public Map<String, Object> createSeatSection(CreateSeatSectionRequest req) {
        if (!stadiumRepository.existsById(req.stadiumId())) {
            throw new BusinessException("STADIUM_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "구장을 찾을 수 없습니다. stadiumId=" + req.stadiumId());
        }
        SeatSection section = seatSectionRepository.save(SeatSection.builder()
                .stadiumId(req.stadiumId())
                .sectionName(req.sectionName())
                .price(req.price())
                .build());
        return Map.<String, Object>of("sectionId", section.getSectionId());
    }

    @Transactional
    public Map<String, Object> createSeat(CreateSeatRequest req) {
        if (!stadiumRepository.existsById(req.stadiumId())) {
            throw new BusinessException("STADIUM_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "구장을 찾을 수 없습니다. stadiumId=" + req.stadiumId());
        }
        SeatSection section = seatSectionRepository.findById(req.sectionId())
                .orElseThrow(() -> new BusinessException("SECTION_NOT_FOUND",
                        HttpStatus.NOT_FOUND, "좌석 구역을 찾을 수 없습니다. sectionId=" + req.sectionId()));
        if (!section.getStadiumId().equals(req.stadiumId())) {
            throw new BusinessException("STADIUM_SECTION_MISMATCH", HttpStatus.BAD_REQUEST,
                    "해당 좌석 구역은 요청한 구장에 속하지 않습니다.");
        }
        Seat seat = seatRepository.save(Seat.builder()
                .stadiumId(req.stadiumId())
                .sectionId(req.sectionId())
                .seatRow(req.seatRow())
                .seatNumber(req.seatNumber())
                .build());
        return Map.<String, Object>of("seatId", seat.getSeatId());
    }

    @Transactional
    public Map<String, Object> createGameSeats(Long gameId, CreateGameSeatsRequest req) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new BusinessException("GAME_NOT_FOUND",
                        HttpStatus.NOT_FOUND, "경기를 찾을 수 없습니다. gameId=" + gameId));
        List<Seat> seats = seatRepository.findAllById(req.seatIds());
        if (seats.size() != req.seatIds().size()) {
            throw new BusinessException("SEAT_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "존재하지 않는 좌석이 포함되어 있습니다.");
        }
        boolean allSameStadium = seats.stream()
                .allMatch(s -> s.getStadiumId().equals(game.getStadiumId()));
        if (!allSameStadium) {
            throw new BusinessException("STADIUM_SEAT_MISMATCH", HttpStatus.BAD_REQUEST,
                    "경기 구장과 일치하지 않는 좌석이 포함되어 있습니다.");
        }
        List<GameSeat> gameSeats = req.seatIds().stream()
                .map(seatId -> GameSeat.builder()
                        .gameId(gameId)
                        .seatId(seatId)
                        .status(GameSeatStatus.AVAILABLE)
                        .price(req.price())
                        .build())
                .toList();
        gameSeatRepository.saveAll(gameSeats);
        return Map.<String, Object>of("gameId", gameId, "createdCount", gameSeats.size());
    }

    @Transactional
    public Map<String, Object> upsertWaitingRoomPolicy(Long gameId, UpsertWaitingRoomPolicyRequest req) {
        if (!gameRepository.existsById(gameId)) {
            throw new BusinessException("GAME_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "경기를 찾을 수 없습니다. gameId=" + gameId);
        }
        int maxEnter = req.maxEnterPerMinute() != null ? req.maxEnterPerMinute() : 100;
        int ttl = req.tokenTtlSeconds() != null ? req.tokenTtlSeconds() : 300;
        boolean enabled = req.enabled() != null ? req.enabled() : true;

        WaitingRoomPolicy policy = waitingRoomPolicyRepository.findByGameId(gameId)
                .orElse(null);
        if (policy == null) {
            policy = WaitingRoomPolicy.builder()
                    .gameId(gameId)
                    .maxEnterPerMinute(maxEnter)
                    .tokenTtlSeconds(ttl)
                    .enabled(enabled)
                    .build();
        } else {
            policy.update(maxEnter, ttl, enabled);
        }
        waitingRoomPolicyRepository.save(policy);
        return Map.<String, Object>of(
                "gameId", gameId,
                "maxEnterPerMinute", maxEnter,
                "tokenTtlSeconds", ttl,
                "enabled", enabled);
    }

    @Transactional
    public Map<String, Object> createMenu(CreateMenuRequest req) {
        boolean available = req.available() != null ? req.available() : true;
        AlcoholMenu menu = alcoholMenuRepository.save(AlcoholMenu.builder()
                .name(req.name())
                .price(req.price())
                .available(available)
                .build());
        return Map.<String, Object>of("menuId", menu.getMenuId());
    }

    @Transactional
    public Map<String, Object> createFaq(CreateFaqRequest req) {
        boolean enabled = req.enabled() != null ? req.enabled() : true;
        Faq faq = faqRepository.save(Faq.builder()
                .category(req.category())
                .question(req.question())
                .answer(req.answer())
                .enabled(enabled)
                .build());
        return Map.<String, Object>of("faqId", faq.getFaqId());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getWaitingRoomStatus(Long gameId) {
        WaitingRoomPolicy policy = waitingRoomPolicyRepository.findByGameId(gameId)
                .orElseThrow(() -> new BusinessException("POLICY_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "대기열 정책이 설정되지 않았습니다. gameId=" + gameId));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("gameId", gameId);
        // 현재 대기 인원/입장 허용 수는 Redis에서 관리 → 파트 B 통합 시 연동.
        // 로컬(파트 A 단독)에서는 0으로 반환한다.
        body.put("currentWaitingCount", 0);
        body.put("allowedCount", 0);
        body.put("maxEnterPerMinute", policy.getMaxEnterPerMinute());
        body.put("enabled", policy.getEnabled());
        return body;
    }

}
