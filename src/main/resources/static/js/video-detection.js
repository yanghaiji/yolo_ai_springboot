// 全局变量
let currentVideoSource = '0';
let isStreaming = false;
let videoStream = null;
let captureInterval = null;
let video = null; // 全局video元素引用
let resultCanvas = null; // 全局画布元素引用

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
    // 监听视频源选择
    document.querySelectorAll('input[name="source"]').forEach(radio => {
        radio.addEventListener('change', handleSourceChange);
    });
});

// 处理视频源选择变化
function handleSourceChange() {
    const fileInputContainer = document.getElementById('file-input-container');
    const videoFileInput = document.getElementById('videoFile');
    
    if (document.getElementById('video-file').checked) {
        fileInputContainer.style.display = 'block';
        videoFileInput.addEventListener('change', function() {
            if (this.files && this.files[0]) {
                // 视频文件处理（这里需要后端支持，暂时使用摄像头）
                alert('视频文件上传功能正在开发中，当前使用摄像头');
                document.getElementById('webcam').checked = true;
                fileInputContainer.style.display = 'none';
            }
        });
    } else {
        fileInputContainer.style.display = 'none';
        currentVideoSource = '0';
    }
}



// 显示视频帧
function displayVideoFrame(base64Frame) {
    const videoDisplay = document.getElementById('videoDisplay');
    
    // 如果画布不存在，创建它
    if (!resultCanvas) {
        resultCanvas = document.createElement('canvas');
        resultCanvas.id = 'resultCanvas';
        resultCanvas.classList.add('detection-result');
        videoDisplay.innerHTML = '';
        videoDisplay.appendChild(resultCanvas);
    }
    
    // 加载并显示处理后的图像
    const img = new Image();
    img.onload = function() {
        resultCanvas.width = img.width;
        resultCanvas.height = img.height;
        const ctx = resultCanvas.getContext('2d');
        ctx.drawImage(img, 0, 0);
    };
    img.src = `data:image/jpeg;base64,${base64Frame}`;
}

// 处理单帧图像
async function processVideoFrame(base64Image) {
    try {
        const response = await fetch('/process-video-frame', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: `frame=${encodeURIComponent(base64Image)}`
        });
        
        if (response.ok) {
            const result = await response.text();
            
            // 检查是否为错误响应
            if (result === 'error') {
                console.error('处理视频帧失败: 后端返回错误');
                // 当后端返回错误时，显示原始视频帧
                try {
                    const canvas = document.getElementById('resultCanvas');
                    const ctx = canvas.getContext('2d');
                    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
                } catch (error) {
                    console.error('显示原始视频帧失败:', error);
                }
                return;
            }
            
            displayVideoFrame(result);
        } else {
            console.error('处理视频帧失败:', response.statusText);
            // 当请求失败时，显示原始视频帧
            try {
                const canvas = document.getElementById('resultCanvas');
                const ctx = canvas.getContext('2d');
                ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
            } catch (error) {
                console.error('显示原始视频帧失败:', error);
            }
        }
    } catch (error) {
        console.error('发送请求失败:', error);
    }
}

// 开始视频流
function startStream() {
    if (isStreaming) return;
    
    isStreaming = true;
    const startBtn = document.getElementById('startBtn');
    const stopBtn = document.getElementById('stopBtn');
    const videoDisplay = document.getElementById('videoDisplay');
    
    startBtn.disabled = true;
    stopBtn.disabled = false;
    
    // 更新显示区域，显示加载状态
    videoDisplay.innerHTML = '<p class="placeholder">正在启动摄像头...</p>';
    
    // 访问用户媒体设备（摄像头）
    navigator.mediaDevices.getUserMedia({ video: true, audio: false })
        .then(function(stream) {
            videoStream = stream;
            // 创建视频元素
            video = document.createElement('video');
            video.srcObject = stream;
            video.autoplay = true;
            video.muted = true; // 静音，避免回声
            video.style.display = 'none';
            document.body.appendChild(video);
            
            // 创建画布元素用于捕获帧
            const canvas = document.createElement('canvas');
            const ctx = canvas.getContext('2d');
            
            // 设置画布大小
            video.addEventListener('loadedmetadata', function() {
                console.log('视频元数据加载完成:', video.videoWidth, 'x', video.videoHeight);
                canvas.width = video.videoWidth;
                canvas.height = video.videoHeight;
                
                // 初始化结果画布
                const videoDisplay = document.getElementById('videoDisplay');
                if (!resultCanvas) {
                    resultCanvas = document.createElement('canvas');
                    resultCanvas.id = 'resultCanvas';
                    resultCanvas.classList.add('detection-result');
                    resultCanvas.width = video.videoWidth;
                    resultCanvas.height = video.videoHeight;
                    videoDisplay.innerHTML = '';
                    videoDisplay.appendChild(resultCanvas);
                } else {
                    resultCanvas.width = video.videoWidth;
                    resultCanvas.height = video.videoHeight;
                }
                
                // 显示初始视频帧
                const ctx = resultCanvas.getContext('2d');
                ctx.drawImage(video, 0, 0, resultCanvas.width, resultCanvas.height);
            });
            
            // 当视频可以播放时开始捕获帧
            video.addEventListener('play', function() {
                function captureFrame() {
                    if (!isStreaming) {
                        return;
                    }
                    
                    try {
                        // 捕获视频帧到画布
                        ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
                        
                        // 将画布内容转换为Base64
                        const base64Image = canvas.toDataURL('image/jpeg').split(',')[1];
                        
                        // 通过HTTP请求发送帧
                        processVideoFrame(base64Image);
                    } catch (error) {
                        console.error('捕获视频帧失败:', error);
                        // 当捕获失败时，显示原始视频帧
                        try {
                            const resultCtx = resultCanvas.getContext('2d');
                            resultCtx.drawImage(video, 0, 0, resultCanvas.width, resultCanvas.height);
                        } catch (drawError) {
                            console.error('绘制原始视频帧失败:', drawError);
                        }
                    }
                    
                    // 使用requestAnimationFrame确保流畅的捕获
                    if (isStreaming) {
                        requestAnimationFrame(captureFrame);
                    }
                }
                
                // 开始捕获帧
                captureFrame();
            });
        })
        .catch(function(error) {
            console.error('访问摄像头失败: ' + error);
            alert('无法访问摄像头，请确保已授权并检查设备连接');
            isStreaming = false;
            startBtn.disabled = false;
            stopBtn.disabled = true;
            videoDisplay.innerHTML = '<p class="placeholder">点击开始检测按钮开始视频流</p>';
        });
}

// 停止视频流
function stopStream() {
    isStreaming = false;
    const startBtn = document.getElementById('startBtn');
    const stopBtn = document.getElementById('stopBtn');
    
    startBtn.disabled = false;
    stopBtn.disabled = true;
    
    // 停止视频流
    if (videoStream) {
        videoStream.getTracks().forEach(track => track.stop());
        videoStream = null;
    }
    
    // 清除捕获间隔
    if (captureInterval) {
        clearInterval(captureInterval);
        captureInterval = null;
    }
    
    // 清空视频显示
    const videoDisplay = document.getElementById('videoDisplay');
    videoDisplay.innerHTML = '<p class="placeholder">点击开始检测按钮开始视频流</p>';
}

// 页面关闭时清理资源
window.addEventListener('beforeunload', function() {
    if (isStreaming) {
        stopStream();
    }
});