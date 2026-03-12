package com.daodun.service.impl;

import com.daodun.config.RagProperties;
import com.daodun.entity.KnowledgeChunk;
import com.daodun.entity.KnowledgeDocument;
import com.daodun.repository.KnowledgeChunkRepository;
import com.daodun.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIngestionServiceImpl implements com.daodun.service.KnowledgeIngestionService {

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final RagProperties ragProperties;

    private static final Pattern ENTRY_HEADER = Pattern.compile("^##\\s+条目\\s*\\d+[：:]\\s*(.+)$");
    private static final Pattern META_LINE = Pattern.compile("^-\\s*(.+?)[：:]\\s*(.+)$");

    @Override
    @Transactional
    public int ingestAll() {
        Path sourceDir = resolveSourceDir();
        if (!Files.isDirectory(sourceDir)) {
            log.warn("[KnowledgeIngestion] 知识源目录不存在: {}", sourceDir);
            return 0;
        }
        int total = 0;
        try (Stream<Path> files = Files.list(sourceDir)) {
            List<Path> mdFiles = files
                    .filter(p -> p.toString().endsWith(".md"))
                    .toList();
            for (Path md : mdFiles) {
                total += ingestMarkdownFile(md.toString());
            }
        } catch (IOException e) {
            log.error("[KnowledgeIngestion] 扫描知识源目录失败: {}", e.getMessage());
        }
        return total;
    }

    @Override
    @Transactional
    public int ingestMarkdownFile(String filePath) {
        Path path = Paths.get(filePath);
        if (!Files.isRegularFile(path)) {
            log.warn("[KnowledgeIngestion] 文件不存在: {}", filePath);
            return 0;
        }

        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[KnowledgeIngestion] 读取文件失败: {}", e.getMessage());
            return 0;
        }

        String hash = sha256(content);
        String normalizedPath = path.toAbsolutePath().toString();

        var existingDoc = documentRepository.findBySourcePath(normalizedPath);
        if (existingDoc.isPresent() && hash.equals(existingDoc.get().getContentHash())) {
            log.info("[KnowledgeIngestion] 文件未变化，跳过: {}", normalizedPath);
            return 0;
        }

        String positionName = guessPositionName(path.getFileName().toString());
        List<ParsedEntry> entries = parseMarkdown(content);

        if (entries.isEmpty()) {
            log.warn("[KnowledgeIngestion] 未解析到有效条目: {}", filePath);
            return 0;
        }

        KnowledgeDocument doc;
        if (existingDoc.isPresent()) {
            doc = existingDoc.get();
            chunkRepository.deleteByDocumentId(doc.getId());
        } else {
            doc = new KnowledgeDocument();
            doc.setSourcePath(normalizedPath);
        }
        doc.setPositionName(positionName);
        doc.setContentHash(hash);
        doc.setChunkCount(entries.size());
        doc = documentRepository.save(doc);

        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            ParsedEntry entry = entries.get(i);
            KnowledgeChunk chunk = KnowledgeChunk.builder()
                    .documentId(doc.getId())
                    .positionName(positionName)
                    .title(entry.title)
                    .categoryLevel1(entry.categoryLevel1)
                    .categoryLevel2(entry.categoryLevel2)
                    .keywords(entry.keywords)
                    .difficulty(entry.difficulty)
                    .canonicalQuestion(entry.title)
                    .answerKeyPoints(entry.answerKeyPoints)
                    .exampleAnswer(entry.exampleAnswer)
                    .followUps(entry.followUps)
                    .scoringPoints(entry.scoringPoints)
                    .pitfalls(entry.pitfalls)
                    .searchText(buildSearchText(positionName, entry))
                    .sourceOrder(i + 1)
                    .build();
            chunks.add(chunk);
        }
        chunkRepository.saveAll(chunks);

        log.info("[KnowledgeIngestion] 入库完成: {} -> {} 条", normalizedPath, entries.size());
        return entries.size();
    }

    // ── Markdown 解析 ────────────────────────────────────────────

    List<ParsedEntry> parseMarkdown(String content) {
        String[] lines = content.split("\n");
        List<ParsedEntry> entries = new ArrayList<>();
        ParsedEntry current = null;
        String currentSection = null;
        StringBuilder sectionBuf = new StringBuilder();

        for (String rawLine : lines) {
            String line = rawLine.stripTrailing();
            Matcher headerMatch = ENTRY_HEADER.matcher(line);

            if (headerMatch.matches()) {
                if (current != null) {
                    flushSection(current, currentSection, sectionBuf);
                    entries.add(current);
                }
                current = new ParsedEntry();
                current.title = headerMatch.group(1).trim();
                currentSection = "meta";
                sectionBuf.setLength(0);
                continue;
            }

            if (current == null) continue;

            if (line.startsWith("### ")) {
                flushSection(current, currentSection, sectionBuf);
                String sectionTitle = line.substring(4).trim();
                currentSection = normalizeSectionName(sectionTitle);
                sectionBuf.setLength(0);
                continue;
            }

            if ("meta".equals(currentSection)) {
                Matcher meta = META_LINE.matcher(line);
                if (meta.matches()) {
                    applyMeta(current, meta.group(1).trim(), meta.group(2).trim());
                }
            } else {
                if (!line.isBlank() || sectionBuf.length() > 0) {
                    sectionBuf.append(line).append("\n");
                }
            }
        }
        if (current != null) {
            flushSection(current, currentSection, sectionBuf);
            entries.add(current);
        }

        return entries;
    }

    private void flushSection(ParsedEntry entry, String section, StringBuilder buf) {
        if (section == null || buf.isEmpty()) return;
        String text = buf.toString().strip();
        switch (section) {
            case "answer_key_points" -> entry.answerKeyPoints = text;
            case "example_answer" -> entry.exampleAnswer = text;
            case "follow_ups" -> entry.followUps = text;
            case "scoring_points" -> entry.scoringPoints = text;
            case "pitfalls" -> entry.pitfalls = text;
            default -> { /* ignore other sections */ }
        }
    }

    private String normalizeSectionName(String name) {
        return switch (name) {
            case "标准答案要点" -> "answer_key_points";
            case "优秀回答范例" -> "example_answer";
            case "常见追问" -> "follow_ups";
            case "评分要点" -> "scoring_points";
            case "易错点" -> "pitfalls";
            default -> name;
        };
    }

    private void applyMeta(ParsedEntry entry, String key, String value) {
        switch (key) {
            case "一级考点" -> entry.categoryLevel1 = value;
            case "二级考点" -> entry.categoryLevel2 = value;
            case "关键词" -> entry.keywords = value;
            case "难度" -> {
                try { entry.difficulty = Integer.parseInt(value); }
                catch (NumberFormatException ignored) {}
            }
        }
    }

    private String guessPositionName(String fileName) {
        String name = fileName.replace(".md", "").trim();
        if (name.contains("java") || name.contains("Java") || name.contains("后端")) {
            return "Java后端开发";
        }
        if (name.contains("前端") || name.contains("front") || name.contains("web") || name.contains("Web")) {
            return "Web前端工程师";
        }
        return name;
    }

    // ── 构建检索用文本 ────────────────────────────────────────────

    private String buildSearchText(String positionName, ParsedEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(positionName);
        if (entry.categoryLevel1 != null) sb.append("-").append(entry.categoryLevel1);
        if (entry.categoryLevel2 != null) sb.append("-").append(entry.categoryLevel2);
        sb.append("】").append(entry.title).append("。");
        if (entry.keywords != null) sb.append("关键词：").append(entry.keywords).append("。");
        if (entry.answerKeyPoints != null) {
            String brief = entry.answerKeyPoints.length() > 500
                    ? entry.answerKeyPoints.substring(0, 500) : entry.answerKeyPoints;
            sb.append("要点：").append(brief);
        }
        return sb.toString();
    }

    // ── 工具 ────────────────────────────────────────────────────

    private Path resolveSourceDir() {
        String dir = ragProperties.getIngestion().getSourceDir();
        Path path = Paths.get(dir);
        if (path.isAbsolute()) return path;
        return Paths.get(System.getProperty("user.dir")).resolve(dir);
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // ── 内部解析结构 ────────────────────────────────────────────

    static class ParsedEntry {
        String title;
        String categoryLevel1;
        String categoryLevel2;
        String keywords;
        Integer difficulty;
        String answerKeyPoints;
        String exampleAnswer;
        String followUps;
        String scoringPoints;
        String pitfalls;
    }
}
