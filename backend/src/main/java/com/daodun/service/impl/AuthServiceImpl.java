package com.daodun.service;

import com.daodun.common.BusinessException;
import com.daodun.dto.*;
import com.daodun.entity.User;
import com.daodun.repository.UserRepository;
import com.daodun.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String REGISTER_CODE_PREFIX = "auth:register:code:";
    private static final String LOGIN_CODE_PREFIX = "auth:login:code:";
    private static final String CODE_SEND_LOCK_PREFIX = "auth:code:lock:";
    private static final long CODE_TTL_SECONDS = 300L;      // 验证码有效期 5 分钟
    private static final long SEND_LOCK_SECONDS = 60L;       // 发送冷却 60 秒

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;
    private final EmailService emailService;
    private final JwtUtil jwtUtil;

    @Override
    public void sendRegisterCode(String email) {
        // 防刷：同一邮箱60s内只能请求一次
        String lockKey = CODE_SEND_LOCK_PREFIX + email;
        Boolean isLocked = redisTemplate.hasKey(lockKey);
        if (Boolean.TRUE.equals(isLocked)) {
            throw new BusinessException("发送过于频繁，请 60 秒后再试");
        }

        // 邮箱已注册则不允许再注册
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("该邮箱已注册，请直接登录");
        }

        String code = generateCode();
        redisTemplate.opsForValue().set(REGISTER_CODE_PREFIX + email, code, CODE_TTL_SECONDS, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(lockKey, "1", SEND_LOCK_SECONDS, TimeUnit.SECONDS);

        emailService.sendVerificationCode(email, code, "注册");
    }

    @Override
    public void sendLoginCode(String email) {
        // 防刷
        String lockKey = CODE_SEND_LOCK_PREFIX + "login:" + email;
        Boolean isLocked = redisTemplate.hasKey(lockKey);
        if (Boolean.TRUE.equals(isLocked)) {
            throw new BusinessException("发送过于频繁，请 60 秒后再试");
        }

        // 邮箱未注册
        if (!userRepository.existsByEmail(email)) {
            throw new BusinessException("该邮箱未注册");
        }

        String code = generateCode();
        redisTemplate.opsForValue().set(LOGIN_CODE_PREFIX + email, code, CODE_TTL_SECONDS, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(lockKey, "1", SEND_LOCK_SECONDS, TimeUnit.SECONDS);

        emailService.sendVerificationCode(email, code, "登录");
    }

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        // 验证验证码
        verifyCode(REGISTER_CODE_PREFIX + request.getEmail(), request.getCode());

        // 用户名唯一性校验
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("该用户名已被占用，请尝试其他名称");
        }

        // 邮箱唯一性校验（双重保险）
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("该邮箱已注册，请直接登录");
        }

        User user = User.builder()
                .email(request.getEmail())
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .status(1)
                .build();

        userRepository.save(user);

        // 注册成功后删除验证码
        redisTemplate.delete(REGISTER_CODE_PREFIX + request.getEmail());
        log.info("新用户注册成功: {}", request.getUsername());
    }

    @Override
    public LoginResponse loginByPassword(LoginPasswordRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException("用户名或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        checkUserStatus(user);
        return buildLoginResponse(user, request.isRememberMe());
    }

    @Override
    public LoginResponse loginByEmail(LoginEmailRequest request) {
        // 验证验证码
        verifyCode(LOGIN_CODE_PREFIX + request.getEmail(), request.getCode());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException("该邮箱未注册"));

        checkUserStatus(user);

        // 登录成功后删除验证码
        redisTemplate.delete(LOGIN_CODE_PREFIX + request.getEmail());
        return buildLoginResponse(user, request.isRememberMe());
    }

    // ---- 私有辅助方法 ----

    private String generateCode() {
        SecureRandom random = new SecureRandom();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    private void verifyCode(String redisKey, String inputCode) {
        Object storedCode = redisTemplate.opsForValue().get(redisKey);
        if (storedCode == null) {
            throw new BusinessException("验证码已过期，请重新获取");
        }
        if (!storedCode.toString().equals(inputCode)) {
            throw new BusinessException("验证码错误");
        }
    }

    private void checkUserStatus(User user) {
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }
    }

    private LoginResponse buildLoginResponse(User user, boolean rememberMe) {
        String accessToken = jwtUtil.generateAccessToken(user.getId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), rememberMe);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .build();
    }
}
