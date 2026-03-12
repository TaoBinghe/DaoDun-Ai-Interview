package com.daodun.service;

import com.daodun.dto.voice.TtsResult;

public interface VoiceSynthesisService {

    TtsResult synthesize(String text);
}
