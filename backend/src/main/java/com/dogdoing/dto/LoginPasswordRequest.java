package com.dogdoing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginPasswordRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 是否勾选记住我，控制 Refresh Token 有效期
     */
    private boolean rememberMe = false;
}
