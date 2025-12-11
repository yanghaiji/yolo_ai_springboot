package com.javayh.yolov;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class YoloDetectionApplication {

    static {
        // 必须在任何 OpenCV 调用之前加载 native 库
        nu.pattern.OpenCV.loadLocally();
        log.info("OpenCV loaded: {}", org.opencv.core.Core.VERSION);
    }

    public static void main(String[] args) {
        SpringApplication.run(YoloDetectionApplication.class, args);
    }

}