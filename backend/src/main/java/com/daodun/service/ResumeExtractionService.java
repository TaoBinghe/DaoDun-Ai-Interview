package com.daodun.service;

import com.daodun.dto.resume.ResumeExtractionResponse;

public interface ResumeExtractionService {

    ResumeExtractionResponse extractStructuredInfo(String resumeText);
}
