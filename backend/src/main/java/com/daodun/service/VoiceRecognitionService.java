package com.daodun.service;

public interface VoiceRecognitionService {

    String transcribe(byte[] audioBytes, String format, Integer sampleRate);
}
