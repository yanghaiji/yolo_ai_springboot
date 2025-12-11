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
    /**
     * 模型路径
     */
    private String modelPath = "models/yolov11n.onnx";
    /**
     * 类别路径
     */
    private String classesPath = "models/coco.names";
    /**
     * 默认模型路径
     */
    private String defaultModelPath = "classpath:models/yolov11n.onnx";
    /**
     * 默认类别路径
     */
    private String defaultClassesPath = "classpath:models/coco.names";
    /**
     * 置信度阈值
     */
    private float confidenceThreshold = 0.8f;
    /**
     * NMS阈值
     */
    private float nmsThreshold = 0.45f;
    /**
     * 输入宽度
     */
    private int inputWidth = 640;
    /**
     * 输入高度
     */
    private int inputHeight = 640;
    /**
     * 保存路径
     */
    private String savePath = "detection_results/";

    /**
     * 线框粗细比例
     */
    private int lineThicknessRatio = 800;

}