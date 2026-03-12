package com.daodun.dto.voice;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class VoiceTtsDiagnosticResponse {
    private Map<String, Object> config;
    private Integer httpStatus;
    private Boolean success;
    private Integer audioBase64Length;
    private String mimeType;
    private String responseSnippet;
    private String errorMessage;
}
