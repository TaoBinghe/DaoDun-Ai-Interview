package com.daodun.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码必须为6位数字")
    private String code;

    @NotBlank(message = "用户名不能为空")
    @Size(min = 5, max = 10, message = "用户名长度必须在5-10个字符之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 5, max = 16, message = "密码长度必须在5-16位之间")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
        message = "密码必须包含大写字母、小写字母和数字的组合"
    )
    private String password;
}
