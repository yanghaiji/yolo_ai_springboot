package com.javayh.yolov.controller;

import com.javayh.yolov.service.DetectionService;
import com.javayh.yolov.service.YoloService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.Base64;

@Controller
@RequiredArgsConstructor
public class VideoDetectionController {

    private final YoloService yoloService;
    private final DetectionService detectionService;

    /**
     * 处理视频帧的HTTP接口
     */
    @PostMapping("/process-video-frame")
    @ResponseBody
    public String processVideoFrame(@RequestParam("frame") String base64Image) {
        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64Image);

            byte[] resultImage = detectionService.detect(imageBytes);
            return Base64.getEncoder().encodeToString(resultImage);
        } catch (IOException | ai.onnxruntime.OrtException e) {
            e.printStackTrace();
            return "error";
        }
    }
   /* public String processVideoFrame(@RequestParam("frame") String base64Image) {
        try {
            // 解码Base64图像
            byte[] imageBytes = Base64.getDecoder().decode(base64Image);
            ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
            BufferedImage image = ImageIO.read(bis);

            // 使用YoloService处理图像
            BufferedImage detectedImage = yoloService.processImage(image);

            // 将处理后的图像编码为Base64
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(detectedImage, "jpg", baos);
            byte[] processedImageBytes = baos.toByteArray();
            return Base64.getEncoder().encodeToString(processedImageBytes);
        } catch (IOException | ai.onnxruntime.OrtException e) {
            e.printStackTrace();
            return "error";
        }
    }*/
}
