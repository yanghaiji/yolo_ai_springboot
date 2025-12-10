package com.javayh.yolov.model;

import lombok.Data;

@Data
public class BoundingBox {
    private int x1;
    private int y1;
    private int x2;
    private int y2;
    private double confidence;
    private String className;
    private int classId;
    private double[] color;
}