package com.daodun.service.impl;

import com.daodun.dto.resume.EducationInfo;
import com.daodun.dto.resume.ProjectInfo;
import com.daodun.dto.resume.ResumeExtractionResponse;
import com.daodun.dto.resume.SkillInfo;
import com.daodun.service.ArkChatService;
import com.daodun.service.ResumeExtractionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeExtractionServiceImpl implements ResumeExtractionService {

    private static final int FALLBACK_TEXT_LEN = 1500;

    private static final String SYSTEM_PROMPT = """
            你是一个简历结构化信息提取器。请从用户给定的简历原文中抽取结构化字段，严格输出 JSON，不要输出任何解释文字。

            输出 JSON 必须符合以下结构：
            {
              "projects": [
                {
                  "name": "项目名称",
                  "timeRange": "时间范围，如 2022.03-2023.01",
                  "role": "职责/角色",
                  "techStack": "技术栈，逗号分隔",
                  "description": "项目描述与成果"
                }
              ],
              "skills": [
                {
                  "category": "技能分类，如 编程语言/框架/数据库/中间件/工具",
                  "items": ["Java", "Spring Boot"]
                }
              ],
              "education": [
                {
                  "school": "学校",
                  "degree": "学历",
                  "major": "专业",
                  "timeRange": "时间范围",
                  "description": "补充信息"
                }
              ]
            }

            规则：
            1) 仅根据简历原文提取，不要编造；
            2) 缺失字段使用空字符串或空数组；
            3) projects/skills/education 字段必须始终存在；
            4) 若无法识别某类信息，返回空数组；
            5) 禁止输出 markdown 包裹或注释。
            """;

    private final ArkChatService arkChatService;
    private final ObjectMapper objectMapper;

    @Override
    public ResumeExtractionResponse extractStructuredInfo(String resumeText) {
        if (resumeText == null || resumeText.isBlank()) {
            return ResumeExtractionResponse.builder().build();
        }
        try {
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content", "请提取以下简历内容：\n" + resumeText)
            );
            String raw = arkChatService.chatWithMessages(messages);
            String json = extractJson(raw);
            ResumeExtractionResponse parsed = objectMapper.readValue(json, ResumeExtractionResponse.class);
            return sanitize(parsed);
        } catch (Exception e) {
            log.warn("[ResumeExtraction] LLM 提取失败，降级为完整文本: {}", e.getMessage());
            return buildFallback(resumeText);
        }
    }

    private ResumeExtractionResponse sanitize(ResumeExtractionResponse data) {
        if (data == null) {
            return ResumeExtractionResponse.builder().build();
        }
        if (data.getProjects() == null) {
            data.setProjects(List.of());
        }
        if (data.getSkills() == null) {
            data.setSkills(List.of());
        }
        if (data.getEducation() == null) {
            data.setEducation(List.of());
        }
        return data;
    }

    private ResumeExtractionResponse buildFallback(String resumeText) {
        String snippet = resumeText.length() <= FALLBACK_TEXT_LEN
                ? resumeText
                : resumeText.substring(0, FALLBACK_TEXT_LEN);

        ProjectInfo fallbackProject = ProjectInfo.builder()
                .name("完整简历文本")
                .description(snippet)
                .build();
        SkillInfo fallbackSkill = SkillInfo.builder()
                .category("完整简历文本")
                .items(List.of(snippet))
                .build();
        EducationInfo fallbackEducation = EducationInfo.builder()
                .description(snippet)
                .build();

        return ResumeExtractionResponse.builder()
                .projects(List.of(fallbackProject))
                .skills(List.of(fallbackSkill))
                .education(List.of(fallbackEducation))
                .build();
    }

    private String extractJson(String text) {
        if (text == null || text.isBlank()) {
            return "{}";
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}
