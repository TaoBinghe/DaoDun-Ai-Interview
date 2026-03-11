package com.daodun.service;

import com.daodun.dto.resume.ResumeUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ResumeService {

    ResumeUploadResponse upload(Long userId, MultipartFile file);

    List<ResumeUploadResponse> listByUser(Long userId);

    void deleteByUser(Long userId, Long resumeId);
}
