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
        body.put("currentWaitingCount", 0);
        body.put("allowedCount", 0);
        body.put("maxEnterPerMinute", policy.getMaxEnterPerMinute());
        body.put("enabled", policy.getEnabled());
        return body;
    }

    // ==================== 경기 수정/상태 변경/삭제 ====================

    @Transactional
    public Map<String, Object> updateGame(Long gameId, CreateGameRequest req) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new BusinessException("GAME_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "경기를 찾을 수 없습니다. gameId=" + gameId));
        game.update(req.homeTeamName(), req.awayTeamName(), req.stadiumId(),
                req.gameStartTime(), req.ticketOpenTime());
        return Map.<String, Object>of("gameId", gameId);
    }

    @Transactional
    public Map<String, Object> changeGameStatus(Long gameId, String status) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new BusinessException("GAME_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "경기를 찾을 수 없습니다. gameId=" + gameId));
        game.changeStatus(GameStatus.valueOf(status));
        return Map.<String, Object>of("gameId", gameId, "status", status);
    }

    @Transactional
    public void deleteGame(Long gameId) {
        if (!gameRepository.existsById(gameId)) {
            throw new BusinessException("GAME_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "경기를 찾을 수 없습니다. gameId=" + gameId);
        }
        gameSeatRepository.deleteByGameId(gameId);
        gameRepository.deleteById(gameId);
    }

    // ==================== 좌석 구역 수정/삭제 ====================

    @Transactional
    public Map<String, Object> updateSeatSection(Long sectionId, CreateSeatSectionRequest req) {
        SeatSection section = seatSectionRepository.findById(sectionId)
                .orElseThrow(() -> new BusinessException("SECTION_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "좌석 구역을 찾을 수 없습니다. sectionId=" + sectionId));
        section.update(req.sectionName(), req.price());
        return Map.<String, Object>of("sectionId", sectionId);
    }

    @Transactional
    public void deleteSeatSection(Long sectionId) {
        if (!seatSectionRepository.existsById(sectionId)) {
            throw new BusinessException("SECTION_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "좌석 구역을 찾을 수 없습니다. sectionId=" + sectionId);
        }
        seatSectionRepository.deleteById(sectionId);
    }

    // ==================== 좌석 조회/수정/삭제 ====================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSeats(Long stadiumId) {
        List<Seat> seats = seatRepository.findByStadiumId(stadiumId);
        return seats.stream().map(s -> Map.<String, Object>of(
                "seatId", s.getSeatId(),
                "stadiumId", s.getStadiumId(),
                "sectionId", s.getSectionId(),
                "seatRow", s.getSeatRow(),
                "seatNumber", s.getSeatNumber()
        )).toList();
    }

    @Transactional
    public Map<String, Object> updateSeat(Long seatId, CreateSeatRequest req) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new BusinessException("SEAT_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "좌석을 찾을 수 없습니다. seatId=" + seatId));
        seat.update(req.sectionId(), req.seatRow(), req.seatNumber());
        return Map.<String, Object>of("seatId", seatId);
    }

    @Transactional
    public void deleteSeat(Long seatId) {
        if (!seatRepository.existsById(seatId)) {
            throw new BusinessException("SEAT_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "좌석을 찾을 수 없습니다. seatId=" + seatId);
        }
        seatRepository.deleteById(seatId);
    }

    // ==================== 경기 좌석 조회/가격 변경/삭제 ====================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getGameSeats(Long gameId) {
        List<GameSeat> gameSeats = gameSeatRepository.findByGameId(gameId);
        return gameSeats.stream().map(gs -> Map.<String, Object>of(
                "gameSeatId", gs.getGameSeatId(),
                "gameId", gs.getGameId(),
                "seatId", gs.getSeatId(),
                "status", gs.getStatus().name(),
                "price", gs.getPrice()
        )).toList();
    }

    @Transactional
    public Map<String, Object> updateGameSeatPrice(Long gameSeatId, Integer price) {
        GameSeat gameSeat = gameSeatRepository.findById(gameSeatId)
                .orElseThrow(() -> new BusinessException("GAME_SEAT_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "경기 좌석을 찾을 수 없습니다. gameSeatId=" + gameSeatId));
        gameSeat.changePrice(price);
        return Map.<String, Object>of("gameSeatId", gameSeatId, "price", price);
    }

    @Transactional
    public void deleteGameSeat(Long gameSeatId) {
        if (!gameSeatRepository.existsById(gameSeatId)) {
            throw new BusinessException("GAME_SEAT_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "경기 좌석을 찾을 수 없습니다. gameSeatId=" + gameSeatId);
        }
        gameSeatRepository.deleteById(gameSeatId);
    }

    // ==================== 메뉴 수정/삭제 ====================

    @Transactional
    public Map<String, Object> updateMenu(Long menuId, CreateMenuRequest req) {
        AlcoholMenu menu = alcoholMenuRepository.findById(menuId)
                .orElseThrow(() -> new BusinessException("MENU_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "메뉴를 찾을 수 없습니다. menuId=" + menuId));
        boolean available = req.available() != null ? req.available() : true;
        menu.update(req.name(), req.price(), available);
        return Map.<String, Object>of("menuId", menuId);
    }

    @Transactional
    public void deleteMenu(Long menuId) {
        if (!alcoholMenuRepository.existsById(menuId)) {
            throw new BusinessException("MENU_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "메뉴를 찾을 수 없습니다. menuId=" + menuId);
        }
        alcoholMenuRepository.deleteById(menuId);
    }

    // ==================== FAQ 수정/삭제 ====================

    @Transactional
    public Map<String, Object> updateFaq(Long faqId, CreateFaqRequest req) {
        Faq faq = faqRepository.findById(faqId)
                .orElseThrow(() -> new BusinessException("FAQ_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "FAQ를 찾을 수 없습니다. faqId=" + faqId));
        boolean enabled = req.enabled() != null ? req.enabled() : true;
        faq.update(req.category(), req.question(), req.answer(), enabled);
        return Map.<String, Object>of("faqId", faqId);
    }

    @Transactional
    public void deleteFaq(Long faqId) {
        if (!faqRepository.existsById(faqId)) {
            throw new BusinessException("FAQ_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "FAQ를 찾을 수 없습니다. faqId=" + faqId);
        }
        faqRepository.deleteById(faqId);
    }

    // ==================== 구장 등록/수정/삭제 ====================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getStadiums() {
        return stadiumRepository.findAll().stream().map(s -> Map.<String, Object>of(
                "stadiumId", s.getStadiumId(),
                "name", s.getName(),
                "location", s.getLocation(),
                "capacity", s.getCapacity()
        )).toList();
    }

    @Transactional
    public Map<String, Object> createStadium(CreateStadiumRequest req) {
        Stadium stadium = stadiumRepository.save(Stadium.builder()
                .name(req.name())
                .location(req.location())
                .capacity(req.capacity())
                .build());
        return Map.<String, Object>of("stadiumId", stadium.getStadiumId());
    }

    @Transactional
    public Map<String, Object> updateStadium(Long stadiumId, CreateStadiumRequest req) {
        Stadium stadium = stadiumRepository.findById(stadiumId)
                .orElseThrow(() -> new BusinessException("STADIUM_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "구장을 찾을 수 없습니다. stadiumId=" + stadiumId));
        stadium.update(req.name(), req.location(), req.capacity());
        return Map.<String, Object>of("stadiumId", stadiumId);
    }

    @Transactional
    public void deleteStadium(Long stadiumId) {
        if (!stadiumRepository.existsById(stadiumId)) {
            throw new BusinessException("STADIUM_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "구장을 찾을 수 없습니다. stadiumId=" + stadiumId);
        }
        stadiumRepository.deleteById(stadiumId);
    }

}
