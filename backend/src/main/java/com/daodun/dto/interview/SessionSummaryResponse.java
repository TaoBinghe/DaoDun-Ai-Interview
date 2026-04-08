package com.daodun.dto.interview;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 会话摘要列表项（个人中心面试历史等）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionSummaryResponse {

    private Long sessionId;
    private Long positionId;
    private String positionName;
    private String status;
    private Integer currentTurnIndex;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createTime;

    /**
     * 评估报告状态，与 {@link EvaluationReportResponse.Status} 同名：
     * NOT_STARTED / GENERATING / READY / FAILED / INSUFFICIENT_DATA
     */
    private String evaluationStatus;
}
