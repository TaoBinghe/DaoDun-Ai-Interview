package com.daodun.service;

import com.daodun.dto.*;

public interface AuthService {

    /**
     * 发送注册验证码（同一邮箱60s内只能发一次）
     */
    void sendRegisterCode(String email);

    /**
     * 发送登录验证码（邮箱必须已注册）
     */
    void sendLoginCode(String email);

    /**
     * 用户注册
     */
    void register(RegisterRequest request);

    /**
     * 账号密码登录
     */
    LoginResponse loginByPassword(LoginPasswordRequest request);

    /**
     * 邮箱验证码登录
     */
    LoginResponse loginByEmail(LoginEmailRequest request);
}
