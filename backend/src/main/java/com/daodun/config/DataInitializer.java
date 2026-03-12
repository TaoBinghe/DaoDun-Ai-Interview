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

        // Java后端开发：题目来源于 QAdocs/java后端开发.md，按难度分级，题干统一为面试问法
        seedQuestions.add(buildQuestion(javaBackend.getId(), "请说明 JVM、JDK、JRE 三者的关系与区别。", Question.QuestionType.TECHNICAL, 1, 1));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "JVM 和 Java 有什么区别？请从语言与运行平台的角度说明。", Question.QuestionType.TECHNICAL, 1, 2));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "Python 和 Java 有什么区别？请从类型系统、运行方式、应用场景等方面比较。", Question.QuestionType.TECHNICAL, 1, 3));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "Java 里是值传递还是引用传递？请结合例子说明。", Question.QuestionType.TECHNICAL, 2, 4));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "Java 的数据类型转换方式有哪些？请分别说明适用场景与注意点。", Question.QuestionType.TECHNICAL, 1, 5));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "为什么金融计算常用 BigDecimal 而不用 double？", Question.QuestionType.TECHNICAL, 1, 6));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "为什么 Java 要有 Integer？包装类在泛型、集合等场景下的作用是什么？", Question.QuestionType.TECHNICAL, 1, 7));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "请说一下 Integer 的缓存机制，以及使用时的注意点。", Question.QuestionType.TECHNICAL, 2, 8));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "抽象类和普通类有什么区别？", Question.QuestionType.TECHNICAL, 1, 9));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "抽象类和接口的区别是什么？在设计中如何选择？", Question.QuestionType.TECHNICAL, 2, 10));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "请说明深拷贝和浅拷贝的区别，以及各自适用场景。", Question.QuestionType.TECHNICAL, 2, 11));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "什么是泛型？Java 泛型的实现机制是什么？", Question.QuestionType.TECHNICAL, 2, 12));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "new 出来的对象什么时候会被回收？请结合 GC 机制说明。", Question.QuestionType.TECHNICAL, 2, 13));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "什么是反射？反射的典型应用场景和代价是什么？", Question.QuestionType.TECHNICAL, 2, 14));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "能讲一讲 Java 注解的原理吗？", Question.QuestionType.TECHNICAL, 3, 15));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "Java 注解的作用域有哪些？@Target 和 @Retention 分别表示什么？", Question.QuestionType.TECHNICAL, 2, 16));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "什么情况下可以不在方法签名上写 throws？", Question.QuestionType.TECHNICAL, 2, 17));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "请说明 try-catch-finally 的执行流程，以及 finally 与 return 的关系。", Question.QuestionType.TECHNICAL, 2, 18));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "== 与 equals() 有什么区别？重写 equals() 时要注意什么？", Question.QuestionType.TECHNICAL, 1, 19));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "hashCode() 和 equals() 有什么关系？为什么重写 equals() 通常要重写 hashCode()？", Question.QuestionType.TECHNICAL, 2, 20));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "String 的常用方法有哪些？请结合后端开发场景举例。", Question.QuestionType.TECHNICAL, 1, 21));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "String、StringBuilder、StringBuffer 的区别和适用场景是什么？", Question.QuestionType.TECHNICAL, 1, 22));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "Stream 流的并行 API 是什么？适合什么场景？有什么注意点？", Question.QuestionType.TECHNICAL, 2, 23));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "Java 21 新特性你知道哪些？请挑几个说说对后端开发的价值。", Question.QuestionType.TECHNICAL, 3, 24));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "怎么把一个对象从一个 JVM 转移到另一个 JVM？有哪些常见方式？", Question.QuestionType.TECHNICAL, 2, 25));
        seedQuestions.add(buildQuestion(javaBackend.getId(), "如果让你自己设计序列化和反序列化方案，你会怎么做？", Question.QuestionType.TECHNICAL, 3, 26));

        // Web前端工程师（保留原示例题目）
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
        log.info("岗位与题库种子数据初始化完成：岗位 2 个，Java后端 26 题（来自知识库）、Web前端 9 题，共 {} 道", seedQuestions.size());
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
