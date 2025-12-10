package com.javayh.yolov.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 模型管理服务类
 * 负责自定义模型的上传、保存和恢复默认模型功能
 */
@Service
public class ModelService {
    
    @Autowired
    private ResourceLoader resourceLoader;
    
    @Value("${yolo.model-path}")
    private String modelPath;
    
    @Value("${yolo.classes-path}")
    private String classesPath;
    
    @Value("${yolo.default-model-path}")
    private String defaultModelPath;
    
    @Value("${yolo.default-classes-path}")
    private String defaultClassesPath;
    
    /**
     * 上传自定义模型
     * @param modelFile ONNX模型文件
     * @param classesFile 类别名称文件
     * @return 操作结果
     * @throws IOException 文件操作异常
     */
    public String uploadModel(MultipartFile modelFile, MultipartFile classesFile) throws IOException {
        // 验证模型文件
        if (!isValidModelFile(modelFile)) {
            return "模型文件格式错误，请上传有效的.onnx文件";
        }
        
        // 验证类别文件
        if (!isValidClassesFile(classesFile)) {
            return "类别文件格式错误，请上传有效的.names文件";
        }
        
        // 保存模型文件
        saveFile(modelFile, modelPath);
        
        // 保存类别文件
        saveFile(classesFile, classesPath);
        
        return "模型上传成功，请重启应用以加载新模型";
    }
    
    /**
     * 恢复默认模型
     * @return 操作结果
     * @throws IOException 文件操作异常
     */
    public String resetModel() throws IOException {
        // 复制默认模型文件
        copyDefaultFile(defaultModelPath, modelPath);
        
        // 复制默认类别文件
        copyDefaultFile(defaultClassesPath, classesPath);
        
        return "默认模型恢复成功，请重启应用以加载默认模型";
    }
    
    /**
     * 验证模型文件
     * @param file 上传的文件
     * @return 是否为有效的模型文件
     */
    private boolean isValidModelFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        return originalFilename != null && originalFilename.toLowerCase().endsWith(".onnx");
    }
    
    /**
     * 验证类别文件
     * @param file 上传的文件
     * @return 是否为有效的类别文件
     */
    private boolean isValidClassesFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        return originalFilename != null && originalFilename.toLowerCase().endsWith(".names");
    }
    
    /**
     * 保存上传的文件
     * @param file 上传的文件
     * @param targetPath 目标路径
     * @throws IOException 文件操作异常
     */
    private void saveFile(MultipartFile file, String targetPath) throws IOException {
        Path path = Paths.get(targetPath);
        
        // 创建目录（如果不存在）
        Files.createDirectories(path.getParent());
        
        // 保存文件
        file.transferTo(path);
    }
    
    /**
     * 复制默认文件
     * @param defaultResourcePath 默认资源路径
     * @param targetPath 目标路径
     * @throws IOException 文件操作异常
     */
    private void copyDefaultFile(String defaultResourcePath, String targetPath) throws IOException {
        Resource resource = resourceLoader.getResource(defaultResourcePath);
        try (InputStream inputStream = resource.getInputStream()) {
            Path target = Paths.get(targetPath);
            Files.copy(inputStream, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
    
    /**
     * 获取当前模型信息
     * @return 模型信息
     */
    public String getCurrentModelInfo() {
        File modelFile = new File(modelPath);
        File classesFile = new File(classesPath);
        
        StringBuilder info = new StringBuilder();
        
        if (modelFile.exists()) {
            info.append("当前模型: ").append(modelFile.getName()).append("\n");
            info.append("模型大小: ").append(modelFile.length() / 1024 / 1024).append(" MB\n");
        } else {
            info.append("当前模型不存在\n");
        }
        
        if (classesFile.exists()) {
            info.append("当前类别文件: ").append(classesFile.getName()).append("\n");
        } else {
            info.append("当前类别文件不存在\n");
        }
        
        return info.toString();
    }
}