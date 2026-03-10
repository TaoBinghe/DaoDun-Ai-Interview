package com.daodun.service;

import com.daodun.dto.QuestionResponse;
import com.daodun.entity.Question;

import java.util.List;

public interface QuestionService {

    List<QuestionResponse> listByPositionId(Long positionId);

    List<QuestionResponse> drawQuestions(Long positionId, Question.QuestionType type, Integer difficulty, Integer count);
}
