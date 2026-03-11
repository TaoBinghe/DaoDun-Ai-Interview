package com.daodun.dto.interview;

import com.daodun.entity.Question;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateSessionRequest {

    @NotNull(message = "positionId 不能为空")
    private Long positionId;

    /** 可选：限定首问题型，不传则不限 */
    private Question.QuestionType type;
}
