package com.daodun.service.impl;

import com.daodun.common.BusinessException;
import com.daodun.dto.resume.EducationInfo;
import com.daodun.dto.resume.ProjectInfo;
import com.daodun.dto.resume.ResumeExtractionResponse;
import com.daodun.dto.resume.ResumeUploadResponse;
import com.daodun.dto.resume.SkillInfo;
import com.daodun.entity.UserResume;
import com.daodun.repository.UserResumeRepository;
import com.daodun.service.ResumeExtractionService;
import com.daodun.service.ResumeService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {

    private static final int MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final int PREVIEW_LEN = 200;
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final Pattern MULTI_SPACE = Pattern.compile("[\\t\\x0B\\f ]{2,}");
    private static final Pattern MULTI_EMPTY_LINE = Pattern.compile("\\n{3,}");

    private final UserResumeRepository userResumeRepository;
    private final ResumeExtractionService resumeExtractionService;
    private final ObjectMapper objectMapper;

    @Value("${resume.max-text-length:50000}")
    private int maxTextLength;
    @Value("${resume.max-page-count:15}")
    private int maxPageCount;

    @Override
    @Transactional
    public ResumeUploadResponse upload(Long userId, MultipartFile file) {
        String traceId = shortTraceId();
        long start = System.currentTimeMillis();
        String fileName = safeFileName(file != null ? file.getOriginalFilename() : null);
        try {
            validatePdfFile(file);

            ParsedResume parsedResume = parsePdf(file, traceId);
            if (parsedResume.text().isBlank()) {
                throw new BusinessException("简历解析结果为空，请上传可复制文本的 PDF 简历");
            }
            String normalizedText = normalizeText(parsedResume.text());
            String finalText = truncateIfTooLong(normalizedText);
            ResumeExtractionResponse structured = extractStructuredResume(finalText);

            UserResume resume = UserResume.builder()
                    .userId(userId)
                    .fileName(fileName)
                    .resumeText(finalText)
                    .projectSummary(toJson(structured.getProjects()))
                    .skillsSummary(toJson(structured.getSkills()))
                    .educationSummary(toJson(structured.getEducation()))
                    .charCount(finalText.length())
                    .pageCount(parsedResume.pageCount())
                    .build();
            UserResume saved = userResumeRepository.save(resume);

            log.info("[Resume][{}] 上传成功 userId={} resumeId={} fileName={} size={} pageCount={} chars={} costMs={}",
                    traceId, userId, saved.getId(), saved.getFileName(),
                    file.getSize(), saved.getPageCount(), saved.getCharCount(),
                    System.currentTimeMillis() - start);
            return toResponse(saved);
        } catch (BusinessException e) {
            log.warn("[Resume][{}] 上传失败 userId={} fileName={} reason={} costMs={}",
                    traceId, userId, fileName, e.getMessage(), System.currentTimeMillis() - start);
            throw e;
        } catch (Exception e) {
            log.error("[Resume][{}] 上传异常 userId={} fileName={} err={} costMs={}",
                    traceId, userId, fileName, e.getMessage(), System.currentTimeMillis() - start, e);
            throw new BusinessException(500, "简历处理失败，请稍后重试");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResumeUploadResponse> listByUser(Long userId) {
        return userResumeRepository.findByUserIdOrderByCreateTimeDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void deleteByUser(Long userId, Long resumeId) {
        UserResume resume = userResumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new BusinessException("简历不存在或无权限删除"));
        userResumeRepository.delete(resume);
        log.info("[Resume] 删除成功 userId={} resumeId={}", userId, resumeId);
    }

    private void validatePdfFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请先选择 PDF 简历文件");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException("简历文件不能超过 5MB");
        }
        String fileName = safeFileName(file.getOriginalFilename());
        String contentType = file.getContentType();
        boolean contentTypeOk = "application/pdf".equalsIgnoreCase(contentType);
        boolean suffixOk = fileName.toLowerCase().endsWith(".pdf");
        if (!contentTypeOk && !suffixOk) {
            throw new BusinessException("仅支持上传 PDF 格式的简历");
        }
        try {
            byte[] head = file.getBytes();
            int len = Math.min(head.length, PDF_MAGIC.length);
            if (len < PDF_MAGIC.length || !Arrays.equals(Arrays.copyOf(head, PDF_MAGIC.length), PDF_MAGIC)) {
                throw new BusinessException("文件头校验失败，请上传真实的 PDF 文件");
            }
        } catch (IOException e) {
            throw new BusinessException("读取文件失败，请重新上传");
        }
    }

    private ParsedResume parsePdf(MultipartFile file, String traceId) {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            if (document.isEncrypted()) {
                throw new BusinessException("暂不支持加密 PDF，请先解除加密后重试");
            }
            int pageCount = document.getNumberOfPages();
            if (pageCount > maxPageCount) {
                throw new BusinessException("简历页数过多，请控制在 " + maxPageCount + " 页以内");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            return new ParsedResume(stripper.getText(document), pageCount);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.warn("[Resume][{}] 解析 PDF 失败: {}", traceId, e.getMessage());
            throw new BusinessException("PDF 解析失败，请确认文件格式正确");
        }
    }

    private String normalizeText(String rawText) {
        String text = rawText.replace("\u0000", "").replace("\r\n", "\n");
        text = MULTI_SPACE.matcher(text).replaceAll(" ");
        // 把连续 3 行以上空行压缩为 2 行，保留段落结构且减少上下文浪费
        return MULTI_EMPTY_LINE.matcher(text).replaceAll("\n\n").trim();
    }

    private String truncateIfTooLong(String text) {
        if (text.length() <= maxTextLength) {
            return text;
        }
        return text.substring(0, maxTextLength);
    }

    private ResumeUploadResponse toResponse(UserResume resume) {
        List<ProjectInfo> projectDetails = parseProjects(resume.getProjectSummary());
        List<SkillInfo> skillDetails = parseSkills(resume.getSkillsSummary());
        List<EducationInfo> educationDetails = parseEducation(resume.getEducationSummary());

        String preview = resume.getResumeText().length() <= PREVIEW_LEN
                ? resume.getResumeText()
                : resume.getResumeText().substring(0, PREVIEW_LEN) + "...";
        return ResumeUploadResponse.builder()
                .resumeId(resume.getId())
                .fileName(resume.getFileName())
                .charCount(resume.getCharCount())
                .pageCount(resume.getPageCount())
                .previewText(preview)
                .projects(toProjectTexts(projectDetails))
                .skills(toSkillTexts(skillDetails))
                .education(toEducationTexts(educationDetails))
                .projectDetails(projectDetails)
                .skillDetails(skillDetails)
                .educationDetails(educationDetails)
                .uploadedAt(resume.getCreateTime())
                .build();
    }

    private ResumeExtractionResponse extractStructuredResume(String text) {
        return resumeExtractionService.extractStructuredInfo(text);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("[Resume] 结构化字段序列化失败: {}", e.getMessage());
            return "";
        }
    }

    private List<ProjectInfo> parseProjects(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        if (isJsonArray(raw)) {
            try {
                return objectMapper.readValue(raw, new TypeReference<List<ProjectInfo>>() {});
            } catch (Exception e) {
                log.warn("[Resume] projectSummary 反序列化失败: {}", e.getMessage());
            }
        }
        return splitSummary(raw).stream()
                .map(line -> ProjectInfo.builder().description(line).build())
                .toList();
    }

    private List<SkillInfo> parseSkills(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        if (isJsonArray(raw)) {
            try {
                return objectMapper.readValue(raw, new TypeReference<List<SkillInfo>>() {});
            } catch (Exception e) {
                log.warn("[Resume] skillsSummary 反序列化失败: {}", e.getMessage());
            }
        }
        return splitSummary(raw).stream()
                .map(line -> SkillInfo.builder().category("未分类").items(List.of(line)).build())
                .toList();
    }

    private List<EducationInfo> parseEducation(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        if (isJsonArray(raw)) {
            try {
                return objectMapper.readValue(raw, new TypeReference<List<EducationInfo>>() {});
            } catch (Exception e) {
                log.warn("[Resume] educationSummary 反序列化失败: {}", e.getMessage());
            }
        }
        return splitSummary(raw).stream()
                .map(line -> EducationInfo.builder().description(line).build())
                .toList();
    }

    private List<String> toProjectTexts(List<ProjectInfo> projects) {
        return projects.stream()
                .map(p -> joinParts(p.getName(), p.getTimeRange(), p.getRole(), p.getTechStack(), p.getDescription()))
                .filter(s -> !s.isBlank())
                .toList();
    }

    private List<String> toSkillTexts(List<SkillInfo> skills) {
        return skills.stream()
                .map(s -> {
                    String items = s.getItems() == null ? "" : String.join(", ", s.getItems());
                    return joinParts(s.getCategory(), items);
                })
                .filter(s -> !s.isBlank())
                .toList();
    }

    private List<String> toEducationTexts(List<EducationInfo> education) {
        return education.stream()
                .map(e -> joinParts(e.getSchool(), e.getDegree(), e.getMajor(), e.getTimeRange(), e.getDescription()))
                .filter(s -> !s.isBlank())
                .toList();
    }

    private String joinParts(String... parts) {
        return Arrays.stream(parts)
                .filter(p -> p != null && !p.isBlank())
                .reduce((a, b) -> a + " | " + b)
                .orElse("");
    }

    private boolean isJsonArray(String raw) {
        String text = raw.trim();
        return text.startsWith("[") && text.endsWith("]");
    }

    private List<String> splitSummary(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("\\n"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private String safeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "resume.pdf";
        }
        return originalName.trim();
    }

    private String shortTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record ParsedResume(String text, int pageCount) {}
}
