package com.daodun.dto.interview;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SessionSummaryResponse {

    private Long sessionId;
    private Long positionId;
    private String positionName;
    private String status;
    private Integer currentTurnIndex;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createTime;
}
