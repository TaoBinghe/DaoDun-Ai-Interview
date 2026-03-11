package com.daodun.dto.interview;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PostTurnResponse {

    private TurnDto userTurn;
    private TurnDto interviewerTurn;
    private Integer currentTurnIndex;
}
