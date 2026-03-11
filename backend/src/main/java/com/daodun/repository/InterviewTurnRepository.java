package com.daodun.repository;

import com.daodun.entity.InterviewTurn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewTurnRepository extends JpaRepository<InterviewTurn, Long> {

    List<InterviewTurn> findBySessionIdOrderByTurnIndexAsc(Long sessionId);

    /** 幂等查询：查找同一会话内相同 clientTurnId 的用户 turn */
    Optional<InterviewTurn> findBySessionIdAndClientTurnId(Long sessionId, String clientTurnId);
}
