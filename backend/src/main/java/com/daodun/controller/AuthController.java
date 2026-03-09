package com.daodun.controller;

import com.daodun.common.R;
import com.daodun.dto.*;
import com.daodun.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 发送注册验证码
     * POST /api/auth/send-register-code
     */
    @PostMapping("/send-register-code")
    public R<Void> sendRegisterCode(@Valid @RequestBody SendCodeRequest request) {
        authService.sendRegisterCode(request.getEmail());
        return R.ok("验证码已发送，请查收邮件", null);
    }

    /**
     * 发送登录验证码
     * POST /api/auth/send-login-code
     */
    @PostMapping("/send-login-code")
    public R<Void> sendLoginCode(@Valid @RequestBody SendCodeRequest request) {
        authService.sendLoginCode(request.getEmail());
        return R.ok("验证码已发送，请查收邮件", null);
    }

    /**
     * 用户注册
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public R<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return R.ok("注册成功", null);
    }

    /**
     * 账号密码登录
     * POST /api/auth/login/password
     */
    @PostMapping("/login/password")
    public R<LoginResponse> loginByPassword(@Valid @RequestBody LoginPasswordRequest request) {
        LoginResponse response = authService.loginByPassword(request);
        return R.ok("登录成功", response);
    }

    /**
     * 邮箱验证码登录
     * POST /api/auth/login/email
     */
    @PostMapping("/login/email")
    public R<LoginResponse> loginByEmail(@Valid @RequestBody LoginEmailRequest request) {
        LoginResponse response = authService.loginByEmail(request);
        return R.ok("登录成功", response);
    }
}
