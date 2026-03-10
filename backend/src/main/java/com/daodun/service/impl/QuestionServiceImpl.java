package com.daodun.service.impl;

import com.daodun.common.BusinessException;
import com.daodun.dto.QuestionResponse;
import com.daodun.entity.Question;
import com.daodun.repository.PositionRepository;
import com.daodun.repository.QuestionRepository;
import com.daodun.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {

    private final PositionRepository positionRepository;
    private final QuestionRepository questionRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public List<QuestionResponse> listByPositionId(Long positionId) {
        validatePositionExists(positionId);
        return questionRepository.findByPositionIdOrderBySortOrderAscIdAsc(positionId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<QuestionResponse> drawQuestions(Long positionId, Question.QuestionType type, Integer difficulty, Integer count) {
        validatePositionExists(positionId);
        if (difficulty != null && (difficulty < 1 || difficulty > 3)) {
            throw new BusinessException("难度必须在 1（简单）、2（中等）、3（困难）之间");
        }
        int drawCount = (count == null || count <= 0) ? 1 : count;

        List<Question> source;
        if (difficulty != null) {
            source = type == null
                    ? questionRepository.findByPositionIdAndDifficultyOrderBySortOrderAscIdAsc(positionId, difficulty)
                    : questionRepository.findByPositionIdAndTypeAndDifficultyOrderBySortOrderAscIdAsc(positionId, type, difficulty);
        } else {
            source = type == null
                    ? questionRepository.findByPositionIdOrderBySortOrderAscIdAsc(positionId)
                    : questionRepository.findByPositionIdAndTypeOrderBySortOrderAscIdAsc(positionId, type);
        }

        if (source.isEmpty()) {
            throw new BusinessException("当前条件下没有可用题目");
        }

        List<Question> mutable = new ArrayList<>(source);
        Collections.shuffle(mutable, secureRandom);
        int endIndex = Math.min(drawCount, mutable.size());

        return mutable.subList(0, endIndex).stream()
                .map(this::toResponse)
                .toList();
    }

    private void validatePositionExists(Long positionId) {
        if (positionId == null) {
            throw new BusinessException("positionId 不能为空");
        }
        if (!positionRepository.existsById(positionId)) {
            throw new BusinessException("岗位不存在");
        }
    }

    private QuestionResponse toResponse(Question question) {
        return QuestionResponse.builder()
                .id(question.getId())
                .positionId(question.getPositionId())
                .content(question.getContent())
                .type(question.getType())
                .difficulty(question.getDifficulty())
                .sortOrder(question.getSortOrder())
                .build();
    }
}
