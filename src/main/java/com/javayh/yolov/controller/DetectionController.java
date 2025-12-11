package com.javayh.yolov.controller;

import com.javayh.yolov.service.DetectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;

/**
 * 检测控制器
 * 负责处理图像检测请求
 */
@Slf4j
@Controller
public class DetectionController {


    @Autowired
    private DetectionService detectionService;


    /**
     * 图像检测页面
     * @return 图像检测页面
     */
    @GetMapping("/image-detection")
    public String imageDetection() {
        return "image-detection";
    }

    /**
     * 视频检测页面
     * @return 视频检测页面
     */
    @GetMapping("/video-detection")
    public String videoDetection() {
        return "video-detection";
    }

    /**
     * 上传图像并进行检测
     * @param file 上传的图像文件
     * @return 检测结果（Base64编码的图像）
     */

    /**
     * 上传图像并进行检测
     * @param file 上传的图像文件
     * @return 检测结果（Base64编码的图像）
     */
    @PostMapping("/detect-image")
    @ResponseBody
    public String detectImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            log.error("Image file is empty");
            return "error: Image is empty";
        }

        try {
            byte[] resultImage = detectionService.detect(file.getBytes());
            String base64Image = Base64.getEncoder().encodeToString(resultImage);

            return "data:image/jpeg;base64," + base64Image;
        } catch (Exception e) {
            log.error("Detection failed: {}", e.getMessage(), e);
            return "error: Detection failed: " + e.getMessage();
        }
    }

    /**
     * 上传图像并进行检测，然后返回可下载的图像文件
     * @param file 上传的图像文件
     * @return 检测结果图像文件
     */
    @PostMapping("/detect-and-download")
    public ResponseEntity<byte[]> detectAndDownloadImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(null);
        }

        try {
            byte[] resultImage = detectionService.detect(file.getBytes());
            
            // 设置响应头，使浏览器能够下载图像
            return ResponseEntity.ok()
                    .header("Content-Type", "image/jpeg")
                    .header("Content-Disposition", "attachment; filename=detection_result.jpg")
                    .body(resultImage);
        } catch (Exception e) {
            log.error("Detection failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(null);
        }
    }
   /* @PostMapping("/detect-image")
    @ResponseBody
    public String detectImage(@RequestParam("file") MultipartFile file) {
        try {
            // 读取图像文件
            BufferedImage originalImage = ImageIO.read(file.getInputStream());
            // 执行检测
            BufferedImage resultImage = yoloService.processImage(originalImage);
            
            // 转换为Base64编码
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resultImage, "jpg", baos);
            byte[] imageBytes = baos.toByteArray();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            
            return "data:image/jpeg;base64," + base64Image;
        } catch (Exception e) {
            return "检测失败: " + e.getMessage();
        }
    }*/
}