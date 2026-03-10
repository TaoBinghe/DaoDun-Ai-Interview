package com.daodun.config;

import com.daodun.entity.Position;
import com.daodun.entity.Question;
import com.daodun.repository.PositionRepository;
import com.daodun.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final PositionRepository positionRepository;
    private final QuestionRepository questionRepository;

    @Override
    public void run(String... args) {
        if (positionRepository.count() > 0 || questionRepository.count() > 0) {
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

        // Java后端开发（9题）
        seedQuestions.add(buildQuestion(javaBackend.getId(), "请你说明 HashMap 和 HashTable 的主要区别，以及在并发场景下你会如何选择。", Question.QuestionType.TECHNICAL, 1, 1));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "请结合你做过的项目，解释一下 Spring IOC 和 AOP 在项目中的落地方式。", Question.QuestionType.TECHNICAL, 2, 2));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "MySQL 的 MVCC 是如何工作的？请说明它解决了什么问题。", Question.QuestionType.TECHNICAL, 3, 3));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "请介绍一个你负责的后端项目，并重点说一个技术难点是如何攻克的。", Question.QuestionType.PROJECT, 1, 4));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "你在项目中做过性能排查吗？请描述一次完整的定位与优化过程。", Question.QuestionType.PROJECT, 2, 5));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "线上某接口响应时间突然变慢，你会如何分层排查并快速止损？", Question.QuestionType.SCENARIO, 2, 6));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "如果让你设计一个高并发秒杀系统，你会如何保证高可用和数据一致性？", Question.QuestionType.SCENARIO, 3, 7));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "当你和同事在技术方案上出现分歧时，你通常如何推进达成共识？", Question.QuestionType.BEHAVIOR, 1, 8));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "临近上线时发现严重 bug，你会如何沟通、决策和处理？", Question.QuestionType.BEHAVIOR, 2, 9));

        // Web前端工程师（9题）
        seedQuestions.add(buildQuestion(webFrontend.getId(), "请你比较 Vue3 Composition API 与 Options API 的差异及适用场景。", Question.QuestionType.TECHNICAL, 1, 1));
        seedQuestions.add(buildQuestion(webFrontend.getId(), "浏览器渲染流程是什么？重排（回流）和重绘有何区别？", Question.QuestionType.TECHNICAL, 2, 2));
        seedQuestions.add(buildQuestion(webFrontend.getId(), "前端性能优化常见手段有哪些？请从网络、渲染、资源管理三个维度说明。", Question.QuestionType.TECHNICAL, 3, 3));
        seedQuestions.add(buildQuestion(webFrontend.getId(), "请介绍你做过的一个前端项目，以及你在技术选型中的关键判断。", Question.QuestionType.PROJECT, 1, 4));
        seedQuestions.add(buildQuestion(webFrontend.getId(), "你做过前端性能优化吗？请给出一次优化前后数据对比。", Question.QuestionType.PROJECT, 2, 5));
        seedQuestions.add(buildQuestion(webFrontend.getId(), "如果要实现一个可拖拽排序列表，你会如何设计状态管理与性能策略？", Question.QuestionType.SCENARIO, 2, 6));
        seedQuestions.add(buildQuestion(webFrontend.getId(), "首屏加载过慢时，你会采用哪些策略并如何评估效果？", Question.QuestionType.SCENARIO, 3, 7));
        seedQuestions.add(buildQuestion(webFrontend.getId(), "你平时如何持续跟进前端新技术并判断是否适合团队引入？", Question.QuestionType.BEHAVIOR, 1, 8));
        seedQuestions.add(buildQuestion(webFrontend.getId(), "跨团队协作中遇到需求反复变更时，你如何推动项目按时交付？", Question.QuestionType.BEHAVIOR, 2, 9));

        questionRepository.saveAll(seedQuestions);
        log.info("岗位与题库种子数据初始化完成：岗位 {} 个，题目 {} 道", 2, seedQuestions.size());
    }

    private Question buildQuestion(Long positionId,
                                   String content,
                                   Question.QuestionType type,
                                   Integer difficulty,
                                   Integer sortOrder) {
        return Question.builder()
                .positionId(positionId)
                .content(content)
                .type(type)
                .difficulty(difficulty)
                .sortOrder(sortOrder)
                .build();
    }
}
