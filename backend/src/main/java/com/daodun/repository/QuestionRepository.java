package com.daodun.repository;

import com.daodun.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByPositionIdOrderBySortOrderAscIdAsc(Long positionId);

    List<Question> findByPositionIdAndTypeOrderBySortOrderAscIdAsc(Long positionId, Question.QuestionType type);

    List<Question> findByPositionIdAndDifficultyOrderBySortOrderAscIdAsc(Long positionId, Integer difficulty);

    List<Question> findByPositionIdAndTypeAndDifficultyOrderBySortOrderAscIdAsc(Long positionId, Question.QuestionType type, Integer difficulty);
}
