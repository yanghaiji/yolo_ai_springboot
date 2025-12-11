package com.javayh.yolov.service;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.javayh.yolov.config.YoloConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.List;


/**
 * YOLOv11服务类
 * 负责加载模型、执行推理和处理输出
 * @author haiji
 */
@Data
@Service
@Slf4j
public class YoloService {

    @Autowired
    private YoloConfig yoloConfig;

    @Autowired
    private ResourceLoader resourceLoader;

    private OrtEnvironment env;
    private OrtSession session;
    private List<String> classesName;
    private double[] colors;

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
                classesName = reader.lines().toList();
            }
        } else {
            // 如果自定义文件不存在，使用默认类别路径
            String defaultClassesPath = yoloConfig.getDefaultClassesPath();
            Resource classesResource = resourceLoader.getResource(defaultClassesPath);
            log.info("使用默认类别文件: {}", defaultClassesPath);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(classesResource.getInputStream()))) {
                classesName = reader.lines().toList();
            }
        }

        log.info("加载类别数量: {}", classesName.size());
        log.info("前10个类别: {}", classesName.subList(0, Math.min(10, classesName.size())));
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

        colors = new double[classesName.size() * 3];
        for (int i = 0; i < classesName.size(); i++) {
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