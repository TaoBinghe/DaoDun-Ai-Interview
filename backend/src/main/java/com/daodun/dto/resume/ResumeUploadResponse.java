package com.daodun.dto.resume;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ResumeUploadResponse {

    private Long resumeId;
    private String fileName;
    private Integer charCount;
    private Integer pageCount;
    private String previewText;
    private List<String> projects;
    private List<String> skills;
    private List<String> education;
    private List<ProjectInfo> projectDetails;
    private List<SkillInfo> skillDetails;
    private List<EducationInfo> educationDetails;
    private LocalDateTime uploadedAt;
}
