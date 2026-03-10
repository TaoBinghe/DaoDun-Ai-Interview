package com.daodun.dto;

import com.daodun.entity.Question;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionResponse {

    private Long id;
    private Long positionId;
    private String content;
    private Question.QuestionType type;
    private Integer difficulty;
    private Integer sortOrder;
}
