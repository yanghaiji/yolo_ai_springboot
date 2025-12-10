package com.javayh.yolov.model;


import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class ODConfig {

    public static final int lineThicknessRatio = 200;

    // COCO 80 classes
    private static final List<String> COCO_CLASSES = Arrays.asList(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
            "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse",
            "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie",
            "suitcase", "frisbee", "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove",
            "skateboard", "surfboard", "tennis racket", "bottle", "wine glass", "cup", "fork", "knife", "spoon",
            "bowl", "banana", "apple", "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut",
            "cake", "chair", "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
            "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator", "book",
            "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
    );

    // 随机颜色（或按类别固定）
    public double[] getOtherColor(int clsId) {
        // 使用类别 ID 生成稳定颜色
        int r = (clsId * 37) % 255;
        int g = (clsId * 57) % 255;
        int b = (clsId * 79) % 255;
        return new double[]{b, g, r}; // OpenCV 是 BGR
    }

    public String getName(int clsId) {
        if (clsId >= 0 && clsId < COCO_CLASSES.size()) {
            return COCO_CLASSES.get(clsId);
        }
        return "unknown";
    }

    public int getNumClasses() {
        return COCO_CLASSES.size(); // 80
    }
}