package com.daodun.dto.interview;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 在每轮回复中返回的结构化决策。
 * action:
 *   "follow_up"    - 继续追问当前题目
 *   "next_question" - 进入下一道题目
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmDecision {

    /** 面试官对候选人说的话（评价 + 追问 或 过渡语） */
    @JsonProperty("reply")
    private String reply = "";

    /** 决策动作：follow_up | next_question */
    @JsonProperty("action")
    private String action = "follow_up";

    /** 下一题难度（仅 next_question 时有效）：1=简单 2=中等 3=困难 */
    @JsonProperty("next_difficulty")
    private Integer nextDifficulty = 1;
}
