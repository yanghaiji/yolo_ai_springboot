package com.javayh.yolov.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.javayh.yolov.config.YoloConfig;
import com.javayh.yolov.model.BoundingBox;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * YOLOv11服务类
 * 负责加载模型、执行推理和处理输出
 */
@Service
@Slf4j
public class YoloService {

    @Autowired
    private YoloConfig yoloConfig;

    @Autowired
    private ResourceLoader resourceLoader;

    private OrtEnvironment env;
    private OrtSession session;
    private List<String> classes;
    private double[] colors;
    private int inputWidth;
    private int inputHeight;
    private float confidenceThreshold;
    private float nmsThreshold;

    private File modelTempFile = null;
    
    /**
     * 初始化YOLOv11服务
     */
    @PostConstruct
    public void init() {
        try {
            log.info("正在初始化YOLO服务...");
            reloadModelAndClasses();
            log.info("YOLO服务初始化完成");
        } catch (Exception e) {
            log.error("Failed to initialize YOLOv11 service", e);
            // 不抛出异常，允许应用启动但功能不可用
            log.warn("YOLOv11 detection service is unavailable");
        }
    }
    /**
     * 重新加载模型和类别文件
     * 当用户上传新模型时调用此方法
     */
    public void reloadModelAndClasses() throws Exception {
        log.info("正在重新加载YOLO模型和类别...");
        // 初始化ONNX Runtime环境
        env = OrtEnvironment.getEnvironment();
        
        // 首先尝试加载自定义上传的模型文件
        String customModelPath = yoloConfig.getModelPath();
        File customModelFile = new File(customModelPath);
        
        if (customModelFile.exists() && customModelFile.length() > 0) {
            // 使用自定义上传的模型文件
            log.info("使用自定义模型文件: {}", customModelPath);
            session = env.createSession(customModelFile.getAbsolutePath());
        } else {
            // 如果自定义文件不存在，使用默认模型路径
            String defaultModelPath = yoloConfig.getDefaultModelPath();
            Resource modelResource = resourceLoader.getResource(defaultModelPath);
            log.info("使用默认模型文件: {}", defaultModelPath);
            
            // 将资源复制到临时文件
            modelTempFile = File.createTempFile("model", ".onnx");
            modelTempFile.deleteOnExit();
            try (InputStream in = modelResource.getInputStream();
                 FileOutputStream out = new FileOutputStream(modelTempFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            
            // 使用临时文件创建会话
            session = env.createSession(modelTempFile.getAbsolutePath());
        }
        
        // 加载类别名称
        loadClasses();
        
        // 生成颜色
        generateColors();
        
        // 加载配置参数
        inputWidth = yoloConfig.getInputWidth();
        inputHeight = yoloConfig.getInputHeight();
        confidenceThreshold = yoloConfig.getConfidenceThreshold();
        nmsThreshold = yoloConfig.getNmsThreshold();
        
        log.info("YOLOv11 model loaded successfully");
    }

    private void loadClasses() throws IOException {
        // 首先尝试加载自定义上传的类别文件
        String customClassesPath = yoloConfig.getClassesPath();
        File customClassesFile = new File(customClassesPath);
        
        if (customClassesFile.exists() && customClassesFile.length() > 0) {
            // 使用自定义上传的类别文件
            log.info("使用自定义类别文件: {}", customClassesPath);
            try (BufferedReader reader = new BufferedReader(new FileReader(customClassesFile))) {
                classes = reader.lines().toList();
            }
        } else {
            // 如果自定义文件不存在，使用默认类别路径
            String defaultClassesPath = yoloConfig.getDefaultClassesPath();
            Resource classesResource = resourceLoader.getResource(defaultClassesPath);
            log.info("使用默认类别文件: {}", defaultClassesPath);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(classesResource.getInputStream()))) {
                classes = reader.lines().toList();
            }
        }
        
        log.info("加载类别数量: {}", classes.size());
        log.info("前10个类别: {}", classes.subList(0, Math.min(10, classes.size())));
    }

    private void generateColors() {
        // 使用固定的颜色表，确保不同类别有明显区分的颜色
        String[] colorHex = {
            "#FF0000", "#00FF00", "#0000FF", "#FFFF00", "#00FFFF", "#FF00FF",
            "#FFA500", "#FFC0CB", "#800080", "#008000", "#808000", "#008080",
            "#800000", "#000080", "#C0C0C0", "#808080", "#FFD700", "#FF6347",
            "#4682B4", "#90EE90", "#FF7F50", "#DDA0DD", "#98FB98", "#F08080",
            "#20B2AA", "#FFB6C1", "#87CEFA", "#9370DB", "#3CB371", "#7B68EE"
        };
        
        colors = new double[classes.size() * 3];
        for (int i = 0; i < classes.size(); i++) {
            // 循环使用颜色表中的颜色
            String hex = colorHex[i % colorHex.length];
            int r = Integer.parseInt(hex.substring(1, 3), 16);
            int g = Integer.parseInt(hex.substring(3, 5), 16);
            int b = Integer.parseInt(hex.substring(5, 7), 16);
            
            colors[i * 3] = r / 255.0;
            colors[i * 3 + 1] = g / 255.0;
            colors[i * 3 + 2] = b / 255.0;
        }
    }

    /**
     * 处理图像
     * @param image 输入图像
     * @return 带有边界框的输出图像
     */
    public BufferedImage processImage(BufferedImage image) throws OrtException {
        log.info("开始处理图像，原始尺寸: {}x{}", image.getWidth(), image.getHeight());

        // 图像预处理
        BufferedImage resized = preprocess(image);
        log.info("图像预处理完成，调整后尺寸: {}x{}", resized.getWidth(), resized.getHeight());

        // 执行推理
        float[] outputs = infer(resized);
        log.info("推理完成，输出数组长度: {}", outputs.length);

        // 后处理
        List<BoundingBox> boxes = postprocess(outputs, image.getWidth(), image.getHeight());
        log.info("后处理完成，检测到 {} 个边界框", boxes.size());

        // 绘制边界框
        return drawBoxes(image, boxes);
    }

    /**
     * 图像预处理 - 保持原始宽高比并进行填充
     * 使用与 Letterbox.java 相同的逻辑
     */
    private BufferedImage preprocess(BufferedImage image) {
        // 创建黑色背景图像
        BufferedImage resized = new BufferedImage(inputWidth, inputHeight, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = resized.createGraphics();
        
        // 设置高质量的缩放渲染提示
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 设置黑色背景
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, inputWidth, inputHeight);
        
        // 计算保持宽高比的缩放尺寸
        int originalWidth = image.getWidth();
        int originalHeight = image.getHeight();
        
        // 使用与 Letterbox.java 相同的逻辑计算缩放比例
        double ratio = Math.min((double) inputWidth / originalWidth, (double) inputHeight / originalHeight);
        int newWidth = (int) Math.round(originalWidth * ratio);
        int newHeight = (int) Math.round(originalHeight * ratio);
        
        // 计算居中位置
        int xOffset = (inputWidth - newWidth) / 2;
        int yOffset = (inputHeight - newHeight) / 2;
        
        // 绘制图像，保持宽高比
        g.drawImage(image, xOffset, yOffset, newWidth, newHeight, null);
        g.dispose();
        
        return resized;
    }

    /**
     * 执行推理
     */
    private float[] infer(BufferedImage image) throws OrtException {
        float[] data = prepareImageData(image);

        // 创建ONNX输入
        OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(data), new long[]{1, 3, inputHeight, inputWidth});

        // 执行推理
        Map<String, OnnxTensor> inputMap = new HashMap<>();
        inputMap.put(session.getInputNames().iterator().next(), tensor);
        OrtSession.Result result = session.run(inputMap);

        // 获取输出
        Object output = result.get(0).getValue();
        float[] outputs;

        // 正确处理YOLOv11的输出格式
        // YOLOv11的输出通常是 [batch, num_boxes, 5 + num_classes]
        // 我们需要将其展平为一维数组
        if (output instanceof float[][][]) {
            // 处理三维数组输出格式 [[[F]]] - 这是YOLOv11常见的输出格式
            float[][][] outputArray = (float[][][]) output;
            int batchSize = outputArray.length;
            int numBoxes = outputArray[0].length;
            int numElementsPerBox = outputArray[0][0].length;

            log.info("YOLO输出格式: float[][][], 批次大小: {}, 边界框数量: {}, 每个边界框元素数: {}", batchSize, numBoxes, numElementsPerBox);

            outputs = new float[batchSize * numBoxes * numElementsPerBox];
            int index = 0;

            // 遍历所有批次
            for (int b = 0; b < batchSize; b++) {
                // 遍历所有边界框
                for (int i = 0; i < numBoxes; i++) {
                    // 遍历边界框的所有元素
                    for (int j = 0; j < numElementsPerBox; j++) {
                        outputs[index++] = outputArray[b][i][j];
                    }
                }
            }
        } else if (output instanceof float[][][][]) {
            // 处理四维数组输出格式 [[[[F]]]]
            float[][][][] outputArray = (float[][][][]) output;
            int batchSize = outputArray.length;
            int numBoxes = outputArray[0][0].length;
            int numElementsPerBox = outputArray[0][0][0].length;

            outputs = new float[batchSize * numBoxes * numElementsPerBox];
            int index = 0;

            // 遍历所有批次
            for (int b = 0; b < batchSize; b++) {
                // 遍历所有通道（通常只有1个）
                for (int c = 0; c < outputArray[0].length; c++) {
                    // 遍历所有边界框
                    for (int i = 0; i < numBoxes; i++) {
                        // 遍历边界框的所有元素
                        for (int j = 0; j < numElementsPerBox; j++) {
                            outputs[index++] = outputArray[b][c][i][j];
                        }
                    }
                }
            }
        } else if (output instanceof float[]) {
            // 直接处理一维数组
            outputs = (float[]) output;
        } else {
            // 默认处理
            log.warn("Unexpected output type: {}", output.getClass().getName());
            // 直接返回空数组避免后续错误
            outputs = new float[0];
        }

        // 释放资源
        tensor.close();
        result.close();

        return outputs;
    }

    /**
     * 准备图像数据
     */
    private float[] prepareImageData(BufferedImage image) {
        float[] data = new float[3 * inputHeight * inputWidth];
        int index = 0;
        
        for (int y = 0; y < inputHeight; y++) {
            for (int x = 0; x < inputWidth; x++) {
                int rgb = image.getRGB(x, y);
                Color color = new Color(rgb);
                
                // YOLO模型通常期望BGR格式，而不是RGB
                // 归一化到0-1范围
                data[index++] = color.getBlue() / 255.0f;     // B
                data[index++] = color.getGreen() / 255.0f;   // G
                data[index++] = color.getRed() / 255.0f;     // R
            }
        }
        
        return data;
    }

    /**
     * 后处理 - 处理YOLOv11的输出格式
     */
    private List<BoundingBox> postprocess(float[] outputs, int originalWidth, int originalHeight) {
        List<BoundingBox> boxes = new ArrayList<>();
        List<float[]> bboxData = new ArrayList<>();

        // 计算保持宽高比时的缩放因子和偏移量（与preprocess保持完全一致的逻辑）
        double ratio = Math.min((double) inputWidth / originalWidth, (double) inputHeight / originalHeight);
        int newWidth = (int) Math.round(originalWidth * ratio);
        int newHeight = (int) Math.round(originalHeight * ratio);
        
        // 计算填充的偏移量
        int dw = (inputWidth - newWidth) / 2;
        int dh = (inputHeight - newHeight) / 2;

        log.info("开始后处理，总输出长度: {}", outputs.length);
        log.info("使用的置信度阈值: {}, NMS阈值: {}", confidenceThreshold, nmsThreshold);
        log.info("宽高比缩放因子: {}, 输入尺寸: {}x{}, 填充偏移量: ({}, {})", ratio, inputWidth, inputHeight, dw, dh);

        // YOLOv11的输出格式：[batch, num_boxes, 5 + num_classes]
        // 我们使用的是batch=1，所以输出是[num_boxes, 5 + num_classes]
        int numClasses = classes.size();
        int elementsPerBox = 5 + numClasses; // 4个坐标 + 1个置信度 + 类别概率
        int numBoxes = outputs.length / elementsPerBox;

        log.info("检测到YOLOv11输出格式: {}个边界框, 每个边界框{}个元素 (4坐标 + 1置信度 + {}类别)", 
                 numBoxes, elementsPerBox, numClasses);

        for (int i = 0; i < numBoxes; i++) {
            int offset = i * elementsPerBox;
            
            // 1. 处理边界框坐标和置信度
            // 应用sigmoid激活函数
            float centerX = sigmoid(outputs[offset]);
            float centerY = sigmoid(outputs[offset + 1]);
            float width = sigmoid(outputs[offset + 2]);
            float height = sigmoid(outputs[offset + 3]);
            float objectConfidence = sigmoid(outputs[offset + 4]);
            
            // 2. 处理类别概率
            float maxClassProbability = 0;
            int maxClassId = -1;
            for (int j = 0; j < numClasses; j++) {
                float classProbability = sigmoid(outputs[offset + 5 + j]);
                if (classProbability > maxClassProbability) {
                    maxClassProbability = classProbability;
                    maxClassId = j;
                }
            }
            
            // 3. 计算最终置信度：object_confidence * class_probability
            float finalConfidence = objectConfidence * maxClassProbability;
            
            // 4. 置信度阈值过滤
            if (finalConfidence > confidenceThreshold && maxClassId >= 0 && maxClassId < classes.size()) {
                // 5. 转换中心坐标和宽高为边界框坐标
                // 将sigmoid值转换回实际坐标
                // centerX和centerY是相对于inputWidth/inputHeight的比例
                float cx = centerX * inputWidth;
                float cy = centerY * inputHeight;
                float w = width * inputWidth;
                float h = height * inputHeight;
                
                // 计算左上角和右下角坐标
                float x1 = cx - w / 2;
                float y1 = cy - h / 2;
                float x2 = cx + w / 2;
                float y2 = cy + h / 2;
                
                // 6. 转换回原始图像尺寸
                // 减去填充偏移量，然后除以缩放因子
                float x1Original = (x1 - dw) / (float) ratio;
                float y1Original = (y1 - dh) / (float) ratio;
                float x2Original = (x2 - dw) / (float) ratio;
                float y2Original = (y2 - dh) / (float) ratio;

                // 7. 确保坐标在原始图像范围内
                float boxX1 = Math.max(0, Math.min(originalWidth - 1, x1Original));
                float boxY1 = Math.max(0, Math.min(originalHeight - 1, y1Original));
                float boxX2 = Math.max(0, Math.min(originalWidth - 1, x2Original));
                float boxY2 = Math.max(0, Math.min(originalHeight - 1, y2Original));

                // 8. 创建边界框对象
                BoundingBox box = new BoundingBox();
                box.setX1((int) boxX1);
                box.setY1((int) boxY1);
                box.setX2((int) boxX2);
                box.setY2((int) boxY2);
                box.setConfidence(finalConfidence);
                box.setClassName(classes.get(maxClassId));
                box.setClassId(maxClassId);
                box.setColor(new double[]{colors[maxClassId * 3], colors[maxClassId * 3 + 1], colors[maxClassId * 3 + 2]});

                boxes.add(box);
                bboxData.add(new float[]{boxX1, boxY1, boxX2, boxY2, finalConfidence});
            }
        }

        log.info("后处理阶段统计：检测到的边界框: {}, 最终边界框: {}", numBoxes, boxes.size());

        // 应用非极大值抑制
        List<BoundingBox> finalBoxes = applyNMS(boxes, bboxData);
        log.info("应用NMS后，最终边界框数量: {}", finalBoxes.size());

        return finalBoxes;
    }

    /**
     * Sigmoid激活函数
     */
    private float sigmoid(float x) {
        return (float) (1.0 / (1.0 + Math.exp(-x)));
    }

    /**
     * 非极大值抑制
     */
    private List<BoundingBox> applyNMS(List<BoundingBox> boxes, List<float[]> bboxData) {
        List<BoundingBox> result = new ArrayList<>();
        boolean[] suppressed = new boolean[boxes.size()];
        
        // 按置信度降序排序
        boxes.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));
        
        for (int i = 0; i < boxes.size(); i++) {
            if (suppressed[i]) continue;
            
            result.add(boxes.get(i));
            
            for (int j = i + 1; j < boxes.size(); j++) {
                if (suppressed[j]) continue;
                
                double iou = calculateIoU(boxes.get(i), boxes.get(j));
                if (iou > nmsThreshold) {
                    suppressed[j] = true;
                }
            }
        }
        
        return result;
    }

    /**
     * 计算交并比
     */
    private double calculateIoU(BoundingBox box1, BoundingBox box2) {
        float x1 = Math.max(box1.getX1(), box2.getX1());
        float y1 = Math.max(box1.getY1(), box2.getY1());
        float x2 = Math.min(box1.getX2(), box2.getX2());
        float y2 = Math.min(box1.getY2(), box2.getY2());
        
        float intersection = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        float area1 = (box1.getX2() - box1.getX1()) * (box1.getY2() - box1.getY1());
        float area2 = (box2.getX2() - box2.getX1()) * (box2.getY2() - box2.getY1());
        
        float union = area1 + area2 - intersection;
        
        return union > 0 ? (double) intersection / union : 0;
    }

    /**
     * 绘制边界框
     */
    private BufferedImage drawBoxes(BufferedImage image, List<BoundingBox> boxes) {
        BufferedImage output = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = output.createGraphics();
        
        // 设置高质量的渲染提示
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 绘制原始图像
        g.drawImage(image, 0, 0, null);
        
        // 设置绘图属性
        g.setStroke(new BasicStroke(2));
        g.setFont(new Font("Arial", Font.BOLD, 14));
        
        for (BoundingBox box : boxes) {
            // 获取类别颜色
            Color color = new Color(
                    (float) colors[box.getClassId() * 3],
                    (float) colors[box.getClassId() * 3 + 1],
                    (float) colors[box.getClassId() * 3 + 2]
            );
            
            // 计算边界框坐标
            int x1 = Math.max(0, (int) box.getX1());
            int y1 = Math.max(0, (int) box.getY1());
            int x2 = Math.min(image.getWidth() - 1, (int) box.getX2());
            int y2 = Math.min(image.getHeight() - 1, (int) box.getY2());
            
            // 绘制边界框
            g.setColor(color);
            g.drawRect(x1, y1, x2 - x1, y2 - y1);
            
            // 绘制标签
            String label = String.format("%s: %.2f%%", box.getClassName(), box.getConfidence() * 100);
            FontMetrics metrics = g.getFontMetrics();
            int labelWidth = metrics.stringWidth(label);
            int labelHeight = metrics.getHeight();
            
            // 计算标签位置，确保在图像范围内
            int labelX = x1;
            int labelY = y1 - labelHeight - 5;
            if (labelY < 0) {
                labelY = y1 + labelHeight + 5;
            }
            
            // 绘制标签背景
            g.fillRect(labelX, labelY, labelWidth + 4, labelHeight + 4);
            
            // 绘制标签文本
            g.setColor(Color.BLACK);
            g.drawString(label, labelX + 2, labelY + metrics.getAscent() + 2);
        }
        
        g.dispose();
        return output;
    }

    /**
     * 销毁资源
     */
    @PreDestroy
    public void destroy() {
        try {
            if (session != null) {
                session.close();
            }
            if (env != null) {
                env.close();
            }
        } catch (Exception e) {
            log.error("Error closing ONNX Runtime resources", e);
        }
    }
}