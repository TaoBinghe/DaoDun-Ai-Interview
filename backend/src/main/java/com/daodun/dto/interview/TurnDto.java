package com.daodun.dto.interview;

import com.daodun.entity.InterviewTurn;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TurnDto {

    private Long id;
    private Integer turnIndex;
    private InterviewTurn.Role role;
    private InterviewTurn.MessageType messageType;
    private String content;
    private Long questionId;
    private Long latencyMs;
    private LocalDateTime createTime;
}
