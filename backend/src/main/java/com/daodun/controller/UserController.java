package com.daodun.controller;

import com.daodun.common.R;
import com.daodun.dto.UserInfoResponse;
import com.daodun.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前登录用户相关接口（需携带有效 Access Token）
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 获取当前登录用户信息
     * GET /api/user/me
     */
    @GetMapping("/me")
    public R<UserInfoResponse> getCurrentUser(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        UserInfoResponse user = userService.getUserInfo(userId);
        return R.ok(user);
    }
}
