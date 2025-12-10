package com.javayh.yolov.controller;

import com.javayh.yolov.service.ModelService;
import com.javayh.yolov.service.YoloService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
}