package com.javayh.yolov.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.javayh.yolov.config.YoloConfig;
import com.javayh.yolov.model.Detection;
import com.javayh.yolov.model.Letterbox;
import com.javayh.yolov.model.ODConfig;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 检测服务类
 * @author haiji
 */
@Slf4j
@Service
public class DetectionService {

    @Autowired
    private ODConfig odConfig;

    @Autowired
    private YoloConfig yoloConfig;

    @Autowired
    private YoloService yoloService;



    /**
     * 检测图像中的物体
     * @param bytes 图像字节数组
     * @return 检测结果字节数组
     * @throws IOException 如果读取图像失败
     * @throws OrtException 如果 ONNX 推理失败
     */
    public byte[] detect(byte[] bytes ) throws IOException, OrtException {
        // 1. 读取图像
        Mat img = Imgcodecs.imdecode(new MatOfByte(bytes), Imgcodecs.IMREAD_COLOR);
        if (img.empty()) {
            throw new IllegalArgumentException("Invalid or unsupported image format");
        }
        log.info("Input image size: {} , X : {}" , img.cols(), img.rows());

        Mat image = img.clone();
        Imgproc.cvtColor(image, image, Imgproc.COLOR_BGR2RGB);

        int minDwDh = Math.min(img.width(), img.height());
        int thickness = Math.max(1, minDwDh / yoloConfig.getLineThicknessRatio());

        // 2. Letterbox 预处理（默认 640x640）
        Letterbox letterbox = new Letterbox();
        image = letterbox.letterbox(image);
        double ratio = letterbox.getRatio();
        double dw = letterbox.getDw();
        double dh = letterbox.getDh();
        int rows = letterbox.getHeight();
        int cols = letterbox.getWidth();
        int channels = image.channels();

        // 3. 转为 CHW float 数组 [C, H, W]
        float[] pixels = new float[channels * rows * cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                // 注意: get(row=y, col=x)
                double[] pixel = image.get(j, i);
                for (int k = 0; k < channels; k++) {
                    pixels[rows * cols * k + j * cols + i] = (float) pixel[k] / 255.0f;
                }
            }
        }

        // 4. 推理
        long[] shape = {1L, (long) channels, (long) rows, (long) cols};
        try (OnnxTensor tensor = OnnxTensor.createTensor(yoloService.getEnv(), FloatBuffer.wrap(pixels), shape)) {
            Map<String, OnnxTensor> inputMap = new HashMap<>(16);
            inputMap.put(yoloService.getSession().getInputInfo().keySet().iterator().next(), tensor);

            try (OrtSession.Result result = yoloService.getSession().run(inputMap)) {
                Object value = result.get(0).getValue();

                // 5. 动态解析 YOLOv8/v11 输出 [1, 84, 8400] → [8400, 84]
                float[][] detections;
                if (value instanceof float[][][]) {
                    float[][][] raw = (float[][][]) value;
                    int dim1 = raw[0].length;
                    int dim2 = raw[0][0].length;

                    if (dim1 >= 4 && dim2 > 1000) {
                        // YOLOv8/v11 格式: [1, 84, 8400]
                        detections = new float[dim2][dim1];
                        for (int i = 0; i < dim2; i++) {
                            for (int j = 0; j < dim1; j++) {
                                detections[i][j] = raw[0][j][i];
                            }
                        }
                    } else if (dim2 >= 4 && dim1 > 1000) {
                        // YOLOv7 格式: [1, 25200, 84]
                        detections = raw[0];
                    } else {
                        throw new RuntimeException("Unsupported output shape: [1, " + dim1 + ", " + dim2 + "]");
                    }
                } else {
                    throw new RuntimeException("Unsupported ONNX output type: " + value.getClass());
                }

                // 6. 绘制检测框（COCO 80 类）
                //===== 收集所有检测结果 =====
                List<Detection> detectionsList = new ArrayList<>();
                int numClasses = odConfig.getNumClasses();

                for (float[] det : detections) {
                    if (det.length < 4 + numClasses) {
                        continue;
                    }
                    // 解析边界框的中心点 (x, y) 和宽高 (w, h)
                    float x = det[0], y = det[1], w = det[2], h = det[3];
                    // 将 (x, y, w, h) 转换为左上角 (x0, y0) 和右下角 (x1, y1)
                    float x0 = x - w * 0.5f;
                    float y0 = y - h * 0.5f;
                    float x1 = x + w * 0.5f;
                    float y1 = y + h * 0.5f;

                    // 在类别置信度部分 [det[4] 到 det[4 + numClasses - 1]] 中找出最大值及其类别 ID
                    float maxConf = 0;
                    int clsId = -1;
                    for (int c = 0; c < numClasses; c++) {
                        if (det[4 + c] > maxConf) {
                            maxConf = det[4 + c];
                            clsId = c;
                        }
                    }

                    if (clsId == -1 || maxConf < yoloConfig.getConfidenceThreshold()) {
                        continue;
                    }

                    // 反 Letterbox 到原图坐标
                    float finalX0 = (float) ((x0 - dw) / ratio);
                    float finalY0 = (float) ((y0 - dh) / ratio);
                    float finalX1 = (float) ((x1 - dw) / ratio);
                    float finalY1 = (float) ((y1 - dh) / ratio);

                    String className = odConfig.getName(clsId);
                    detectionsList.add(new Detection(finalX0, finalY0, finalX1, finalY1, maxConf, clsId, className));
                }

                // ===== 执行 NMS =====
                // IoU 阈值 0.45
                List<Detection> nmsDetections = nms(detectionsList, yoloConfig.getNmsThreshold());

                // ===== 绘图 =====
                double fontScale = 0.5;

                for (Detection det : nmsDetections) {
                    Point topLeft = new Point(det.getX0(), det.getY0());
                    Point bottomRight = new Point(det.getX1(), det.getY1());
                    double[] colorArr = odConfig.getOtherColor(det.getClassId());
                    Scalar color = new Scalar(colorArr[0], colorArr[1], colorArr[2]);

                    // 画细框
                    Imgproc.rectangle(img, topLeft, bottomRight, color, thickness);

                    // 写小字
                    String label = det.getClassName() + " " + String.format("%.2f", det.getConfidence());
                    Point textLoc = new Point(det.getX0(), Math.max(10, det.getY0() - 5));
                    Imgproc.putText(img, label, textLoc, Imgproc.FONT_HERSHEY_SIMPLEX, fontScale, color, thickness);
                    log.info("DetectionInfo: {}", det);
                }

            }
        }

        // 7. 保存检测结果图像到指定路径
//        String savePath = yoloConfig.getSavePath();
//        File saveDir = new File(savePath);
//        if (!saveDir.exists()) {
//            saveDir.mkdirs(); // 创建目录结构
//        }
//
//        // 使用时间戳和原始文件名创建保存文件名
//        String timestamp = String.valueOf(System.currentTimeMillis());
//        String originalFilename = file.getOriginalFilename();
//        String extension = originalFilename != null && originalFilename.contains(".")
//                ? originalFilename.substring(originalFilename.lastIndexOf("."))
//                : ".jpg";
//        String saveFilename = timestamp + "_" + originalFilename.replace(extension, "") + "_detection" + extension;
//        String fullSavePath = savePath + saveFilename;
//
//        // 保存图像
//        Imgcodecs.imwrite(fullSavePath, img);
//        log.info("Detection result saved to: {}", fullSavePath);

        // 8. 返回 JPEG 字节数组
        MatOfByte buf = new MatOfByte();
        Imgcodecs.imencode(".jpg", img, buf);
        return buf.toArray();
    }

    /**
     * 非极大值抑制（NMS）
     * @param detections 检测结果列表
     * @param iouThreshold IoU 阈值
     * @return 保留的检测结果列表
     */
    private List<Detection> nms(List<Detection> detections, float iouThreshold) {
        // 按类别分组
        Map<Integer, List<Detection>> grouped = new HashMap<>();
        for (Detection d : detections) {
            grouped.computeIfAbsent(d.getClassId(), k -> new ArrayList<>()).add(d);
        }

        List<Detection> result = new ArrayList<>();
        for (List<Detection> group : grouped.values()) {
            // 按置信度降序排序
            group.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));

            boolean[] suppressed = new boolean[group.size()];
            for (int i = 0; i < group.size(); i++) {
                if (suppressed[i]) {
                    continue;
                }
                result.add(group.get(i));
                for (int j = i + 1; j < group.size(); j++) {
                    if (suppressed[j]) {
                        continue;
                    }
                    float iou = group.get(i).iou(group.get(j));
                    if (iou > iouThreshold) {
                        suppressed[j] = true;
                    }
                }
            }
        }
        return result;
    }
}