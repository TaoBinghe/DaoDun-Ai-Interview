package com.dogdoing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    /**
     * 异步发送验证码邮件
     */
    @Async("mailTaskExecutor")
    public void sendVerificationCode(String toEmail, String code, String purpose) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(toEmail);
            message.setSubject("【AI模拟面试平台】" + purpose + "验证码");
            message.setText(buildEmailContent(code, purpose));
            mailSender.send(message);
            log.info("验证码邮件发送成功: {}", toEmail);
        } catch (Exception e) {
            log.error("验证码邮件发送失败: {}, error: {}", toEmail, e.getMessage());
        }
    }

    private String buildEmailContent(String code, String purpose) {
        return String.format("""
                您好！
                
                您正在进行【AI模拟面试平台】账号%s操作。
                
                您的验证码为：%s
                
                验证码有效期为 5 分钟，请尽快使用。
                
                如非本人操作，请忽略本邮件。
                
                —— AI模拟面试平台
                """, purpose, code);
    }
}
