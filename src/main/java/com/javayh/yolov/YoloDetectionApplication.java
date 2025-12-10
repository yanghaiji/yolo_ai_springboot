package com.javayh.yolov;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class YoloDetectionApplication {

    static {
        // 必须在任何 OpenCV 调用之前加载 native 库
        nu.pattern.OpenCV.loadLocally();
        System.out.println("OpenCV loaded: " + org.opencv.core.Core.VERSION);
    }


    public static void main(String[] args) {
        SpringApplication.run(YoloDetectionApplication.class, args);
    }

}