// 全局变量
let selectedImage = null;
let detectionResult = null;

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
    // 监听文件选择事件
    document.getElementById('imageFile').addEventListener('change', previewImage);
});

// 预览图片
function previewImage() {
    const fileInput = document.getElementById('imageFile');
    const fileLabel = document.getElementById('fileLabel');
    const originalImage = document.getElementById('originalImage');
    const detectBtn = document.getElementById('detectBtn');
    
    if (fileInput.files && fileInput.files[0]) {
        const file = fileInput.files[0];
        selectedImage = file;
        
        // 更新文件标签显示
        fileLabel.textContent = `已选择: ${file.name}`;
        
        // 预览图片
        const reader = new FileReader();
        reader.onload = function(e) {
            originalImage.innerHTML = `<img src="${e.target.result}" alt="原始图片">`;
        };
        reader.readAsDataURL(file);
        
        // 启用检测按钮
        detectBtn.disabled = false;
        
        // 清空之前的检测结果
        document.getElementById('resultImage').innerHTML = '<p class="placeholder">检测结果将显示在这里</p>';
    }
}

// 执行图片检测
function detectImage() {
    if (!selectedImage) return;
    
    const detectBtn = document.getElementById('detectBtn');
    const resultImage = document.getElementById('resultImage');
    
    // 显示加载状态
    detectBtn.innerHTML = '检测中... <div class="loading"></div>';
    detectBtn.disabled = true;
    resultImage.innerHTML = '<p class="placeholder">正在检测，请稍候...</p>';
    
    // 创建FormData对象
    const formData = new FormData();
    formData.append('file', selectedImage);
    
    // 发送检测请求
    fetch('/detect-image', {
        method: 'POST',
        body: formData
    })
    .then(response => {
        if (!response.ok) {
            throw new Error('检测失败: ' + response.statusText);
        }
        return response.text();
    })
    .then(data => {
        // 检查是否返回了错误信息
        if (data.startsWith('error:')) {
            throw new Error(data.substring(6));
        }
        
        // 显示检测结果
        resultImage.innerHTML = `<img src="${data}" alt="检测结果">`;
        
        // 保存检测结果
        detectionResult = data;
        console.log('检测结果已保存:', detectionResult);
        
        // 启用下载按钮
        const downloadBtn = document.getElementById('downloadBtn');
        console.log('下载按钮当前状态:', downloadBtn.disabled);
        downloadBtn.disabled = false;
        console.log('下载按钮新状态:', downloadBtn.disabled);
    })
    .catch(error => {
        console.error('检测错误:', error);
        resultImage.innerHTML = `<p class="placeholder" style="color: red;">检测失败: ${error.message}</p>`;
    })
    .finally(() => {
        // 恢复按钮状态
        detectBtn.innerHTML = '开始检测';
        detectBtn.disabled = false;
    });
}

// 拖拽上传功能
let dropArea = document.getElementById('fileLabel');

['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
    dropArea.addEventListener(eventName, preventDefaults, false);
});

function preventDefaults(e) {
    e.preventDefault();
    e.stopPropagation();
}

['dragenter', 'dragover'].forEach(eventName => {
    dropArea.addEventListener(eventName, highlight, false);
});

['dragleave', 'drop'].forEach(eventName => {
    dropArea.addEventListener(eventName, unhighlight, false);
});

function highlight() {
    dropArea.style.backgroundColor = '#dee2e6';
    dropArea.style.borderColor = '#868e96';
}

function unhighlight() {
    dropArea.style.backgroundColor = '';
    dropArea.style.borderColor = '';
}

// 处理拖拽文件
function handleDrop(e) {
    const dt = e.dataTransfer;
    const files = dt.files;
    
    if (files.length > 0) {
        document.getElementById('imageFile').files = files;
        previewImage();
    }
}

dropArea.addEventListener('drop', handleDrop, false);

// 下载检测结果
function downloadResult() {
    if (!detectionResult) return;
    
    // 创建一个临时的下载链接
    const link = document.createElement('a');
    link.href = detectionResult;
    link.download = 'detection_result.jpg';
    
    // 触发下载
    document.body.appendChild(link);
    link.click();
    
    // 清理
    document.body.removeChild(link);
}