package com.daodun.dto.voice;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VoiceOutboundMessage {

    private String type;
    private String content;
    private String data;
    private String mimeType;
    private Boolean isFinal;
}
