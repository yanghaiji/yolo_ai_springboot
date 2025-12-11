package com.javayh.yolov.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class VideoStreamService {

    private volatile boolean isRunning;

    public void startStream(String source) {
        log.warn("Video stream functionality is not available in this version");
        isRunning = false;
    }

    public void stopStream() {
        isRunning = false;
        log.info("Video stream stopped");
    }

}