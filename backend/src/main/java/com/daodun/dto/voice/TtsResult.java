package com.daodun.dto.voice;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TtsResult {
    private String audioBase64;
    private String mimeType;
    private String subtitle;
}
