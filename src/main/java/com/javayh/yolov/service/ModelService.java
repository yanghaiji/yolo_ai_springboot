package com.javayh.yolov.service;

import lombok.extern.slf4j.Slf4j;
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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * 模型管理服务类
 * 负责自定义模型的上传、保存和恢复默认模型功能
 */
@Slf4j
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
    
    /**
     * 获取可用的ONNX模型列表
     * @return ONNX模型文件列表
     */
    public List<String> getAvailableModels() {
        List<String> models = new ArrayList<>();
        try {
            // 获取resources/models目录下的所有.onnx文件
            Path modelsDir = Paths.get("src/main/resources/models");
            
            if (Files.exists(modelsDir) && Files.isDirectory(modelsDir)) {
                Files.walk(modelsDir, 1)
                    .filter(path -> Files.isRegularFile(path) && path.toString().toLowerCase().endsWith(".onnx"))
                    .forEach(path -> models.add(path.getFileName().toString()));
            } else {
                // 如果在开发环境中找不到目录，尝试从jar包中读取
                // 获取所有资源路径
                java.io.InputStream is = getClass().getClassLoader().getResourceAsStream("models/");
                if (is != null) {
                    // 这个方法在jar包中可能不工作，所以我们使用备用方案
                    JarFile jarFile = new JarFile(
                            Paths.get(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).toFile());
                    Enumeration<JarEntry> entries = jarFile.entries();
                    while (entries.hasMoreElements()) {
                        java.util.jar.JarEntry entry = entries.nextElement();
                        if (entry.getName().startsWith("models/")
                                && entry.getName().toLowerCase().endsWith(".onnx") && !entry.isDirectory()) {
                            models.add(entry.getName().substring(entry.getName().lastIndexOf('/') + 1));
                        }
                    }
                    jarFile.close();
                }
            }
        } catch (Exception e) {
            log.error("获取可用模型列表时出错: {}", e.getMessage(), e);
        }
        
        return models;
    }
    
    /**
     * 获取可用的类别文件列表
     * @return 类别文件列表
     */
    public List<String> getAvailableClassesFiles() {
        List<String> classesFiles = new ArrayList<>();
        try {
            // 获取resources/models目录下的所有.names文件
            Path modelsDir = Paths.get("src/main/resources/models");
            
            if (Files.exists(modelsDir) && Files.isDirectory(modelsDir)) {
                Files.walk(modelsDir, 1)
                    .filter(path -> Files.isRegularFile(path) && path.toString().toLowerCase().endsWith(".names"))
                    .forEach(path -> classesFiles.add(path.getFileName().toString()));
            } else {
                // 如果在开发环境中找不到目录，尝试从jar包中读取
                // 获取所有资源路径
                JarFile jarFile = new JarFile(
                        Paths.get(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).toFile());
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().startsWith("models/")
                            && entry.getName().toLowerCase().endsWith(".names") && !entry.isDirectory()) {
                        classesFiles.add(entry.getName().substring(entry.getName().lastIndexOf('/') + 1));
                    }
                }
                jarFile.close();
            }
        } catch (Exception e) {
            log.error("读取类别文件失败: {}", e.getMessage(), e);
        }
        
        return classesFiles;
    }
    
    /**
     * 切换模型
     * @param modelName 模型文件名
     * @param classesName 类别文件名
     * @return 操作结果
     * @throws Exception 文件操作异常
     */
    public String switchModel(String modelName, String classesName) throws Exception {
        // 验证模型文件和类别文件的扩展名
        if (!modelName.toLowerCase().endsWith(".onnx")) {
            return "模型文件格式错误，必须是.onnx文件";
        }
        if (!classesName.toLowerCase().endsWith(".names")) {
            return "类别文件格式错误，必须是.names文件";
        }
        
        // 确保目标models文件夹存在
        Path modelsDir = Paths.get("models");
        if (!Files.exists(modelsDir)) {
            Files.createDirectories(modelsDir);
        }
        
        // 构建目标文件路径
        Path targetModelPath = Paths.get(modelPath);
        Path targetClassesPath = Paths.get(classesPath);
        
        // 尝试从文件系统读取模型和类别文件
        Path modelFilePath = Paths.get("src/main/resources/models", modelName);
        Path classesFilePath = Paths.get("src/main/resources/models", classesName);
        boolean modelExists = Files.exists(modelFilePath) && Files.isRegularFile(modelFilePath);
        boolean classesExists = Files.exists(classesFilePath) && Files.isRegularFile(classesFilePath);
        
        // 如果文件不存在，尝试从jar包中读取
        if (!modelExists || !classesExists) {
            try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(
                    Paths.get(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).toFile())) {
                
                // 检查模型文件在jar包中是否存在
                if (!modelExists) {
                    java.util.jar.JarEntry modelEntry = jarFile.getJarEntry("models/" + modelName);
                    if (modelEntry != null) {
                        try (InputStream modelInputStream = jarFile.getInputStream(modelEntry)) {
                            Files.copy(modelInputStream, targetModelPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            modelExists = true;
                        }
                    }
                } else {
                    // 从文件系统复制模型文件
                    Files.copy(modelFilePath, targetModelPath, StandardCopyOption.REPLACE_EXISTING);
                }
                
                // 检查类别文件在jar包中是否存在
                if (!classesExists) {
                    JarEntry classesEntry = jarFile.getJarEntry("models/" + classesName);
                    if (classesEntry != null) {
                        try (InputStream classesInputStream = jarFile.getInputStream(classesEntry)) {
                            Files.copy(classesInputStream, targetClassesPath, StandardCopyOption.REPLACE_EXISTING);
                            classesExists = true;
                        }
                    }
                } else {
                    // 从文件系统复制类别文件
                    Files.copy(classesFilePath, targetClassesPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } else {
            // 从文件系统复制模型和类别文件
            Files.copy(modelFilePath, targetModelPath, StandardCopyOption.REPLACE_EXISTING);
            Files.copy(classesFilePath, targetClassesPath, StandardCopyOption.REPLACE_EXISTING);
        }
        
        // 检查文件是否成功复制
        if (!Files.exists(targetModelPath) || !Files.exists(targetClassesPath)) {
            return "模型切换失败：无法找到指定的模型或类别文件";
        }
        
        return "模型切换成功";
    }
}