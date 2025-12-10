package com.javayh.yolov.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * YOLO配置类
 */
@Data
@Component
@ConfigurationProperties(prefix = "yolo")
public class YoloConfig {
    private String modelPath = "models/yolov11n.onnx";
    private String classesPath = "models/coco.names";
    private String defaultModelPath = "classpath:models/yolov11n.onnx";
    private String defaultClassesPath = "classpath:models/coco.names";
    private float confidenceThreshold = 0.1f;
    private float nmsThreshold = 0.45f;
    private int inputWidth = 640;
    private int inputHeight = 640;
    private String savePath = "detection_results/";

}