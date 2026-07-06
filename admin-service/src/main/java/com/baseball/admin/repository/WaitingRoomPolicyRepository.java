package com.baseball.admin.repository;

import com.baseball.admin.domain.WaitingRoomPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WaitingRoomPolicyRepository extends JpaRepository<WaitingRoomPolicy, Long> {

    Optional<WaitingRoomPolicy> findByGameId(Long gameId);

    void deleteByGameId(Long gameId);
}
