package com.javayh.yolov.model;


import com.javayh.yolov.service.YoloService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ODConfig {

    public static final int lineThicknessRatio = 200;

    @Autowired
    private YoloService yoloService;

    // 随机颜色（或按类别固定）
    public double[] getOtherColor(int clsId) {
        // 使用类别 ID 生成稳定颜色
        clsId+=1;
        int r = (clsId * 37) % 255;
        int g = (clsId * 57) % 255;
        int b = (clsId * 79) % 255;
        // OpenCV 是 BGR
        return new double[]{b, g, r};
    }

    /**
     * 获取类别名称
     * @param clsId 类别id
     * @return 类别名称
     */
    public String getName(int clsId) {
        if (clsId >= 0 && clsId < yoloService.getClassesName().size()) {
            return yoloService.getClassesName().get(clsId);
        }
        return "unknown";
    }

    /**
     * 获取类别数量
     * @return 类别数量
     */
    public int getNumClasses() {
        return yoloService.getClassesName().size();
    }
}