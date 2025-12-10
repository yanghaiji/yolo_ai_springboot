package com.javayh.yolov.model;

public class Detection {
    public float x0, y0, x1, y1; // 左上右下（已转换为 [x0,y0,x1,y1]）
    public float confidence;
    public int classId;
    public String className;

    public Detection(float x0, float y0, float x1, float y1, float confidence, int classId, String className) {
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
        this.confidence = confidence;
        this.classId = classId;
        this.className = className;
    }

    // 计算 IoU（交并比）
    public float iou(Detection other) {
        float xA = Math.max(this.x0, other.x0);
        float yA = Math.max(this.y0, other.y0);
        float xB = Math.min(this.x1, other.x1);
        float yB = Math.min(this.y1, other.y1);

        float interArea = Math.max(0, xB - xA) * Math.max(0, yB - yA);
        float boxAArea = (this.x1 - this.x0) * (this.y1 - this.y0);
        float boxBArea = (other.x1 - other.x0) * (other.y1 - other.y0);
        float unionArea = boxAArea + boxBArea - interArea;

        return unionArea == 0 ? 0 : interArea / unionArea;
    }
}