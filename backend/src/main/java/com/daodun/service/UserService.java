package com.daodun.service;

import com.daodun.dto.UserInfoResponse;

public interface UserService {

    /**
     * 根据用户 ID 获取用户信息（不包含敏感信息）
     */
    UserInfoResponse getUserInfo(Long userId);
}
