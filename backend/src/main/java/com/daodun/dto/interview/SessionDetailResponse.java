package com.daodun.dto.interview;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SessionDetailResponse {

    private Long sessionId;
    private Long positionId;
    private String positionName;
    private String status;
    private Integer currentTurnIndex;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createTime;
    private List<TurnDto> turns;
}
