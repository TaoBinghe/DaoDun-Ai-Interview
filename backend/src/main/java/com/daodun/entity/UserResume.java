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
@Table(name = "user_resumes", indexes = {
        @Index(name = "idx_resume_user_create", columnList = "user_id, create_time DESC")
})
public class UserResume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "resume_text", nullable = false, columnDefinition = "TEXT")
    private String resumeText;

    @Column(name = "project_summary", columnDefinition = "TEXT")
    private String projectSummary;

    @Column(name = "skills_summary", columnDefinition = "TEXT")
    private String skillsSummary;

    @Column(name = "education_summary", columnDefinition = "TEXT")
    private String educationSummary;

    @Column(name = "char_count", nullable = false)
    private Integer charCount;

    @Column(name = "page_count", nullable = false)
    @Builder.Default
    private Integer pageCount = 0;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
