package com.daodun.controller;

import com.daodun.common.R;
import com.daodun.dto.resume.ResumeUploadResponse;
import com.daodun.service.ResumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/resume")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    @PostMapping("/upload")
    public R<ResumeUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        Long userId = currentUserId();
        ResumeUploadResponse response = resumeService.upload(userId, file);
        return R.ok("简历上传并解析成功", response);
    }

    @GetMapping("/me")
    public R<List<ResumeUploadResponse>> listMine() {
        Long userId = currentUserId();
        return R.ok(resumeService.listByUser(userId));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Long userId = currentUserId();
        resumeService.deleteByUser(userId, id);
        log.info("[ResumeController] 删除简历 userId={} resumeId={}", userId, id);
        return R.ok("简历删除成功", null);
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
