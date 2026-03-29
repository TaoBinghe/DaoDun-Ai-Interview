package com.daodun.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "interview_turns",
        indexes = @Index(name = "idx_turns_session_index", columnList = "session_id, turn_index"),
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_turns_session_index", columnNames = {"session_id", "turn_index"}),
                @UniqueConstraint(name = "uq_turns_session_client_turn", columnNames = {"session_id", "client_turn_id"})
        })
public class InterviewTurn {

    public enum Role {
        INTERVIEWER,
        USER
    }

    /** 消息类型：新题目 / 追问 / 候选人回答 */
    public enum MessageType {
        QUESTION,
        FOLLOW_UP,
        ANSWER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "turn_index", nullable = false)
    private Integer turnIndex;

    @Column(name = "question_id")
    private Long questionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", length = 20)
    private MessageType messageType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 算法题完整题干（LeetCode 风格正文），由 LLM 根据题库主题生成。
     * 仅 MESSAGE_TYPE=QUESTION 且对应算法题时有值；对话气泡/TTS 只用 {@link #content}（简短过渡）。
     */
    @Column(name = "coding_problem_content", columnDefinition = "TEXT")
    private String codingProblemContent;

    /** 模型响应耗时（毫秒），仅 INTERVIEWER 类型的 turn 有值 */
    @Column(name = "latency_ms")
    private Long latencyMs;

    /** 前端幂等键，防止重复提交，(session_id, client_turn_id) 唯一 */
    @Column(name = "client_turn_id", length = 64)
    private String clientTurnId;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}
