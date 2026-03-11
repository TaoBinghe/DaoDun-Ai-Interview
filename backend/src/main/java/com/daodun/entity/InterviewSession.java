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
@Table(name = "interview_sessions", indexes = {
        @Index(name = "idx_sessions_user_start", columnList = "user_id, start_time DESC")
})
public class InterviewSession {

    public enum Status {
        IN_PROGRESS,
        COMPLETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "position_id", nullable = false)
    private Long positionId;

    /** 绑定的简历 ID（可空，不绑定则按通用题库面试） */
    @Column(name = "resume_id")
    private Long resumeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.IN_PROGRESS;

    /** 当前最新 turnIndex，避免每次扫描 MAX(turn_index) */
    @Column(name = "current_turn_index", nullable = false)
    @Builder.Default
    private Integer currentTurnIndex = 0;

    /** 当前正在问的题目 ID，便于追问关联与已用题去重 */
    @Column(name = "last_question_id")
    private Long lastQuestionId;

    /** 乐观锁版本号，防止并发双写 */
    @Version
    @Column(name = "version")
    @Builder.Default
    private Long version = 0L;

    @Column(name = "start_time", nullable = false, updatable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) {
            createTime = now;
        }
        if (startTime == null) {
            startTime = now;
        }
        updateTime = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
