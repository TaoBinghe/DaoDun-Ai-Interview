package com.daodun.dto.resume;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumeExtractionResponse {

    @Builder.Default
    private List<ProjectInfo> projects = new ArrayList<>();

    @Builder.Default
    private List<SkillInfo> skills = new ArrayList<>();

    @Builder.Default
    private List<EducationInfo> education = new ArrayList<>();
}
