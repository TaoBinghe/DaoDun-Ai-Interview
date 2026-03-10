package com.daodun.controller;

import com.daodun.common.R;
import com.daodun.dto.QuestionResponse;
import com.daodun.entity.Question;
import com.daodun.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/question")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    /**
     * 按岗位查题目列表（管理/调试）
     */
    @GetMapping("/list")
    public R<List<QuestionResponse>> list(@RequestParam Long positionId) {
        return R.ok(questionService.listByPositionId(positionId));
    }

    /**
     * 按岗位 + 题型 + 难度随机抽题（供面试流程使用）。难度不传则不限难度；type 不传则不限题型。
     */
    @GetMapping("/draw")
    public R<List<QuestionResponse>> draw(@RequestParam Long positionId,
                                          @RequestParam(required = false) Question.QuestionType type,
                                          @RequestParam(required = false) Integer difficulty,
                                          @RequestParam(required = false, defaultValue = "1") Integer count) {
        return R.ok(questionService.drawQuestions(positionId, type, difficulty, count));
    }
}
