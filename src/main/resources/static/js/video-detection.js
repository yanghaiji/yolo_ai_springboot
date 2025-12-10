// 全局变量
let currentVideoSource = '0';
let isStreaming = false;
let videoStream = null;
let captureInterval = null;

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
    videoDisplay.innerHTML = `<img src="data:image/jpeg;base64,${base64Frame}" alt="检测结果" class="detection-result">`;
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
            displayVideoFrame(result);
        } else {
            console.error('处理视频帧失败:', response.statusText);
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
    
    // 访问用户媒体设备（摄像头）
    navigator.mediaDevices.getUserMedia({ video: true, audio: false })
        .then(function(stream) {
            videoStream = stream;
            // 创建视频元素
            const video = document.createElement('video');
            video.srcObject = stream;
            video.autoplay = true;
            video.style.display = 'none';
            document.body.appendChild(video);
            
            // 创建画布元素用于捕获帧
            const canvas = document.createElement('canvas');
            const ctx = canvas.getContext('2d');
            
            // 设置画布大小
            video.addEventListener('loadedmetadata', function() {
                canvas.width = video.videoWidth;
                canvas.height = video.videoHeight;
            });
            
            // 定时捕获和发送帧
            captureInterval = setInterval(function() {
                if (!isStreaming) {
                    clearInterval(captureInterval);
                    return;
                }
                
                // 捕获视频帧到画布
                ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
                
                // 将画布内容转换为Base64
                const base64Image = canvas.toDataURL('image/jpeg').split(',')[1];
                
                // 通过HTTP请求发送帧
                processVideoFrame(base64Image);
            }, 100); // 每100毫秒发送一帧
        })
        .catch(function(error) {
            console.error('访问摄像头失败: ' + error);
            alert('无法访问摄像头，请确保已授权并检查设备连接');
            isStreaming = false;
            startBtn.disabled = false;
            stopBtn.disabled = true;
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