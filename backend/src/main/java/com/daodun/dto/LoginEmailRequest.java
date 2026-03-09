package com.daodun.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class LoginEmailRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码必须为6位数字")
    private String code;

    /**
     * 是否勾选记住我，控制 Refresh Token 有效期
     */
    private boolean rememberMe = false;
}
