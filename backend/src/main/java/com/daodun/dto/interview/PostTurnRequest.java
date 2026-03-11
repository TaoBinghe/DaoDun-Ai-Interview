package com.daodun.dto.interview;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PostTurnRequest {

    @NotBlank(message = "回答内容不能为空")
    @Size(max = 2000, message = "回答内容不能超过 2000 字")
    private String content;

    /** 前端生成的幂等键，防止重复提交；建议使用 UUID */
    private String clientTurnId;
}
