package com.javayh.yolov.controller;

import com.javayh.yolov.service.ModelService;
import com.javayh.yolov.service.YoloService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型管理控制器
 * 负责自定义模型的上传和管理
 */
@Controller
public class ModelController {
    
    @Autowired
    private ModelService modelService;
    
    @Autowired
    private YoloService yoloService;
    
    /**
     * 模型管理页面
     * @return 模型管理页面
     */
    @GetMapping("/model-management")
    public String modelManagement() {
        return "model-management";
    }
    
    /**
     * 上传自定义模型
     * @param modelFile ONNX模型文件
     * @param classesFile 类别名称文件
     * @return 操作结果
     */
    @PostMapping("/upload-model")
    @ResponseBody
    public String uploadModel(
            @RequestParam("modelFile") MultipartFile modelFile,
            @RequestParam("classesFile") MultipartFile classesFile) {
        
        try {
            String result = modelService.uploadModel(modelFile, classesFile);
            
            // 上传成功后重新加载模型和类别
            if (result.startsWith("模型上传成功")) {
                yoloService.reloadModelAndClasses();
                return "模型上传成功并已生效";
            }
            
            return result;
        } catch (Exception e) {
            return "模型上传失败: " + e.getMessage();
        }
    }
    
    /**
     * 恢复默认模型
     * @return 操作结果
     */
    @PostMapping("/reset-model")
    @ResponseBody
    public String resetModel() {
        try {
            String result = modelService.resetModel();
            
            // 重置成功后重新加载模型和类别
            if (result.startsWith("默认模型恢复成功")) {
                yoloService.reloadModelAndClasses();
                return "默认模型恢复成功并已生效";
            }
            
            return result;
        } catch (Exception e) {
            return "模型重置失败: " + e.getMessage();
        }
    }
    
    /**
     * 获取可用的模型列表
     * @return 模型列表
     */
    @GetMapping("/api/models/available-models")
    @ResponseBody
    public ResponseEntity<List<String>> getAvailableModels() {
        try {
            List<String> models = modelService.getAvailableModels();
            return ResponseEntity.ok(models);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 获取可用的类别文件列表
     * @return 类别文件列表
     */
    @GetMapping("/api/models/available-classes")
    @ResponseBody
    public ResponseEntity<List<String>> getAvailableClasses() {
        try {
            List<String> classesFiles = modelService.getAvailableClassesFiles();
            return ResponseEntity.ok(classesFiles);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 切换模型
     * @param modelName 模型文件名
     * @param classesName 类别文件名
     * @return 操作结果
     */
    @PostMapping("/api/models/switch-model")
    @ResponseBody
    public ResponseEntity<Map<String, String>> switchModel(@RequestBody Map<String, String> params) {
        String modelName = params.get("modelName");
        String classesName = params.get("classesName");
        Map<String, String> result = new HashMap<>();
        
        try {
            String switchResult = modelService.switchModel(modelName, classesName);
            
            // 切换成功后重新加载模型和类别
            if ("模型切换成功".equals(switchResult)) {
                yoloService.reloadModelAndClasses();
                result.put("status", "success");
                result.put("message", "模型切换成功并已生效");
                return ResponseEntity.ok(result);
            }
            
            result.put("status", "error");
            result.put("message", switchResult);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "模型切换失败: " + e.getMessage());
            return ResponseEntity.ok(result);
        }
    }
}