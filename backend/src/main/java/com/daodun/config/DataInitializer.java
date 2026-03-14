package com.daodun.config;

import com.daodun.entity.Position;
import com.daodun.entity.Question;
import com.daodun.repository.PositionRepository;
import com.daodun.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final PositionRepository positionRepository;
    private final QuestionRepository questionRepository;

    @Override
    public void run(String... args) {
        if (positionRepository.count() > 0 || questionRepository.count() > 0) {
            log.info("[题库初始化] 已有岗位或题目数据，跳过种子数据初始化（若需重新灌题请清空 positions/questions 表后重启）");
            return;
        }

        Position javaBackend = positionRepository.save(Position.builder()
                .name("Java后端开发")
                .description("面向 Java / Spring Boot / MySQL / Redis 技术栈")
                .sortOrder(1)
                .build());

        Position webFrontend = positionRepository.save(Position.builder()
                .name("Web前端工程师")
                .description("面向 Vue / TypeScript / 浏览器性能优化技术栈")
                .sortOrder(2)
                .build());

        List<Question> seedQuestions = new ArrayList<>();
        int order = 1;

        // 1) Java 岗位：题目文件 QAdocs/questions/java后端.md（题干 + 面试官定义难度）
        for (QuestionWithDifficulty q : loadQuestionsFromJavaBackendMarkdown()) {
            seedQuestions.add(buildQuestion(javaBackend.getId(), q.content, Question.QuestionType.TECHNICAL, q.difficulty, order++));
        }

        // 2) Java 岗位：知识库 QAdocs/knowledge/http面试.md 提取条目标题 + 难度
        for (QuestionWithDifficulty q : loadQuestionsFromKnowledgeMarkdown("http面试.md")) {
            seedQuestions.add(buildQuestion(javaBackend.getId(), q.content, Question.QuestionType.TECHNICAL, q.difficulty, order++));
        }

        // 3) Web 前端岗位：知识库 QAdocs/knowledge/vue面试.md 提取条目标题 + 难度
        for (QuestionWithDifficulty q : loadQuestionsFromKnowledgeMarkdown("vue面试.md")) {
            seedQuestions.add(buildQuestion(webFrontend.getId(), q.content, Question.QuestionType.TECHNICAL, q.difficulty, order++));
        }

        // 4) Web 前端岗位：知识库 QAdocs/knowledge/移动端面试.md 提取条目标题 + 难度（React/Flutter，难度>3 会夹为 3）
        for (QuestionWithDifficulty q : loadQuestionsFromKnowledgeMarkdown("移动端面试.md")) {
            seedQuestions.add(buildQuestion(webFrontend.getId(), q.content, Question.QuestionType.TECHNICAL, q.difficulty, order++));
        }

        // 5) Web 前端岗位：知识库 QAdocs/knowledge/前端工具链工程化专家.md 提取条目标题 + 难度（webpack/Vite/CI-CD/Monorepo）
        for (QuestionWithDifficulty q : loadQuestionsFromKnowledgeMarkdown("前端工具链工程化专家.md")) {
            seedQuestions.add(buildQuestion(webFrontend.getId(), q.content, Question.QuestionType.TECHNICAL, q.difficulty, order++));
        }

        if (seedQuestions.isEmpty()) {
            seedQuestions.add(buildQuestion(javaBackend.getId(), "Q:JVM、JDK、JRE三者关系？", Question.QuestionType.TECHNICAL, 1, 1));
            log.warn("未找到题目文件，已使用默认题目");
        }

        questionRepository.saveAll(seedQuestions);
        log.info("[题库初始化] 岗位与题库种子数据初始化完成：岗位 2 个，题库共 {} 题（Java：questions+http面试；Web前端：vue+移动端+工具链工程化）", seedQuestions.size());
    }

    /**
     * 从 QAdocs/questions/java后端.md 解析所有以 "Q:" 开头的行为题目，并按面试官标准赋予难度后入库。
     * 难度由面试官定义：1=简单（基础概念/入门）、2=中等（理解+应用）、3=困难（原理/设计/深度）。
     */
    private List<QuestionWithDifficulty> loadQuestionsFromJavaBackendMarkdown() {
        List<String> raw = loadQuestionsFromMarkdownRaw();
        // 面试官定义难度（与 raw 顺序一一对应）：1=简单 2=中等 3=困难
        int[] difficulties = {
                1, 1, 1, 2, 1, 2, 2, 2, 1, 2,  // 1-10: JVM/JDK/JRE、Java vs JVM、Python vs Java、值传递、类型转换、BigDecimal、Integer、缓存、抽象类vs普通类、抽象类vs接口
                2, 2, 2, 2, 3, 2, 2, 1, 1, 2,  // 11-20: 深拷贝浅拷贝、泛型、GC回收、反射、注解原理、注解作用域、throws、try-catch、==与equals、hashCode与equals
                1, 2, 2, 3, 2, 3               // 21-26: String方法、String三兄弟、Stream并行、Java21、JVM间传输、手写序列化
        };
        List<QuestionWithDifficulty> list = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            int d = i < difficulties.length ? difficulties[i] : 1;
            list.add(new QuestionWithDifficulty(raw.get(i), clampDifficulty(d)));
        }
        return list;
    }

    /** 从 QAdocs/questions/java后端.md 解析所有以 "Q:" 开头的行为题目内容（仅题干，不包含难度）。 */
    private List<String> loadQuestionsFromMarkdownRaw() {
        List<String> list = new ArrayList<>();
        Path cwd = Paths.get("").toAbsolutePath();
        Path[] candidates = {
                cwd.resolve("QAdocs").resolve("questions").resolve("java后端.md"),
                cwd.getParent().resolve("QAdocs").resolve("questions").resolve("java后端.md")
        };
        InputStream in = null;
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                try {
                    in = Files.newInputStream(p);
                    break;
                } catch (Exception e) {
                    log.debug("无法读取题目文件 {}: {}", p, e.getMessage());
                }
            }
        }
        if (in == null) {
            ClassPathResource res = new ClassPathResource("questions/java后端.md");
            if (res.exists()) {
                try {
                    in = res.getInputStream();
                } catch (Exception e) {
                    log.debug("无法从 classpath 读取题目文件: {}", e.getMessage());
                }
            }
        }
        if (in == null) {
            return list;
        }
        try (Scanner sc = new Scanner(in, StandardCharsets.UTF_8)) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                if (line.startsWith("Q:")) {
                    list.add(line.trim());
                }
            }
        } catch (Exception e) {
            log.warn("解析题目文件失败: {}", e.getMessage());
        }
        return list;
    }

    /**
     * 从知识库 Markdown（如 QAdocs/knowledge/http面试.md、vue面试.md）提取题目：条目标题 + 元数据中的难度。
     * 格式：## 条目 N：标题，下一段中 - 难度：1|2|3
     * @param fileName 文件名，如 "http面试.md"、"vue面试.md"
     */
    private List<QuestionWithDifficulty> loadQuestionsFromKnowledgeMarkdown(String fileName) {
        List<QuestionWithDifficulty> list = new ArrayList<>();
        Path cwd = Paths.get("").toAbsolutePath();
        Path[] candidates = {
                cwd.resolve("QAdocs").resolve("knowledge").resolve(fileName),
                cwd.getParent().resolve("QAdocs").resolve("knowledge").resolve(fileName)
        };
        Path path = null;
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                path = p;
                break;
            }
        }
        InputStream streamFromCp = null;
        if (path == null) {
            ClassPathResource res = new ClassPathResource("knowledge/" + fileName);
            if (res.exists()) {
                try {
                    streamFromCp = res.getInputStream();
                } catch (Exception ignored) {}
            }
        }
        Pattern entryHeader = Pattern.compile("^##\\s+条目\\s*\\d+[：:]\\s*(.+)$");
        Pattern difficultyMeta = Pattern.compile("^-\\s*难度[：:]\\s*(\\d+)\\s*$");
        String currentTitle = null;
        int currentDifficulty = 1;
        try {
            List<String> lines;
            if (path != null) {
                lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            } else if (streamFromCp != null) {
                lines = new ArrayList<>();
                try (Scanner sc = new Scanner(streamFromCp, StandardCharsets.UTF_8)) {
                    while (sc.hasNextLine()) lines.add(sc.nextLine());
                }
            } else {
                return list;
            }
            for (String line : lines) {
                Matcher entryMatcher = entryHeader.matcher(line.trim());
                Matcher diffMatcher = difficultyMeta.matcher(line.trim());
                if (entryMatcher.matches()) {
                    if (currentTitle != null) {
                        list.add(new QuestionWithDifficulty(currentTitle.trim(), clampDifficulty(currentDifficulty)));
                    }
                    currentTitle = entryMatcher.group(1);
                    currentDifficulty = 1;
                } else if (diffMatcher.matches() && currentTitle != null) {
                    try {
                        currentDifficulty = Integer.parseInt(diffMatcher.group(1));
                    } catch (NumberFormatException ignored) {}
                }
            }
            if (currentTitle != null) {
                list.add(new QuestionWithDifficulty(currentTitle.trim(), clampDifficulty(currentDifficulty)));
            }
        } catch (Exception e) {
            log.warn("解析知识库题目文件失败 {}: {}", fileName, e.getMessage());
        }
        return list;
    }

    private static int clampDifficulty(int d) {
        if (d < 1) return 1;
        if (d > 3) return 3;
        return d;
    }

    private static class QuestionWithDifficulty {
        final String content;
        final int difficulty;
        QuestionWithDifficulty(String content, int difficulty) {
            this.content = content;
            this.difficulty = difficulty;
        }
    }

    /**
     * 题目内容格式统一：去掉开头的 "Q:" 或 "Q："，避免题库中混用带/不带前缀的格式。
     */
    private static String normalizeQuestionContent(String content) {
        if (content == null) return "";
        String s = content.trim();
        if (s.startsWith("Q:")) return s.substring(2).trim();
        if (s.startsWith("Q：")) return s.substring(2).trim();
        return s;
    }

    private Question buildQuestion(Long positionId,
                                   String content,
                                   Question.QuestionType type,
                                   Integer difficulty,
                                   Integer sortOrder) {
        return Question.builder()
                .positionId(positionId)
                .content(normalizeQuestionContent(content))
                .type(type)
                .difficulty(difficulty)
                .sortOrder(sortOrder)
                .build();
    }
}
