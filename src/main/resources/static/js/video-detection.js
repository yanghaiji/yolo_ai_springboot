// 全局变量
let currentVideoSource = '0';
let isStreaming = false;
let videoStream = null;
let captureInterval = null;
let video = null; // 全局video元素引用
let resultCanvas = null; // 全局画布元素引用
let frameQueue = []; // 帧队列，只处理最新的帧
let isProcessingFrame = false; // 标记是否正在处理帧

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
    // 监听视频源选择
    document.querySelectorAll('input[name="source"]').forEach(radio => {
        radio.addEventListener('change', handleSourceChange);
    });

    // 加载可用的模型和类别文件
    loadAvailableModels();
    loadAvailableClasses();
});

// 加载可用的模型文件
function loadAvailableModels() {
    fetch('/api/models/available-models')
        .then(response => response.json())
        .then(data => {
            const modelSelect = document.getElementById('modelSelect');
            modelSelect.innerHTML = '';
            data.forEach(model => {
                const option = document.createElement('option');
                option.value = model;
                option.textContent = model;
                modelSelect.appendChild(option);
            });
        })
        .catch(error => console.error('加载可用模型失败:', error));
}

// 加载可用的类别文件
function loadAvailableClasses() {
    fetch('/api/models/available-classes')
        .then(response => response.json())
        .then(data => {
            const classesSelect = document.getElementById('classesSelect');
            classesSelect.innerHTML = '';
            data.forEach(classes => {
                const option = document.createElement('option');
                option.value = classes;
                option.textContent = classes;
                classesSelect.appendChild(option);
            });
        })
        .catch(error => console.error('加载可用类别文件失败:', error));
}

// 切换模型
function switchModel() {
    const modelSelect = document.getElementById('modelSelect');
    const classesSelect = document.getElementById('classesSelect');
    const switchBtn = document.getElementById('switchModelBtn');
    
    const modelName = modelSelect.value;
    const classesName = classesSelect.value;
    
    if (!modelName || !classesName) {
        alert('请选择模型和类别文件');
        return;
    }
    
    switchBtn.disabled = true;
    switchBtn.textContent = '切换中...';
    
    fetch('/api/models/switch-model', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ modelName, classesName })
    })
    .then(response => response.json())
    .then(data => {
        if (data.status === 'success') {
            alert('模型切换成功');
        } else {
            alert('模型切换失败: ' + data.message);
        }
    })
    .catch(error => {
        console.error('切换模型失败:', error);
        alert('模型切换失败: ' + error.message);
    })
    .finally(() => {
        switchBtn.disabled = false;
        switchBtn.textContent = '切换模型';
    });
};

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
    
    // 检查navigator.mediaDevices是否可用
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        console.error('浏览器不支持MediaDevices API');
        alert('您的浏览器不支持摄像头访问功能，请使用最新版本的Chrome、Firefox或Edge浏览器');
        isStreaming = false;
        startBtn.disabled = false;
        stopBtn.disabled = true;
        videoDisplay.innerHTML = '<p class="placeholder">点击开始检测按钮开始视频流</p>';
        return;
    }
    
    // 访问用户媒体设备（摄像头）
    // 添加浏览器兼容性处理和摄像头权限检查
    // 为旧浏览器提供兼容性支持
    if (!navigator.mediaDevices) {
        navigator.mediaDevices = {};
    }
    
    if (!navigator.mediaDevices.getUserMedia) {
        navigator.mediaDevices.getUserMedia = function(constraints) {
            const getUserMedia = navigator.getUserMedia || navigator.webkitGetUserMedia || navigator.mozGetUserMedia || navigator.msGetUserMedia;
            
            if (!getUserMedia) {
                return Promise.reject(new Error('浏览器不支持摄像头访问'));
            }
            
            return new Promise(function(resolve, reject) {
                getUserMedia.call(navigator, constraints, resolve, reject);
            });
        }
    }
    
    // 简化摄像头配置，提高兼容性
    const constraints = {
        video: {
            width: 640,
            height: 480,
            facingMode: 'user'
        },
        audio: false
    };
    
    // 直接使用getUserMedia，减少可能的兼容性问题
    navigator.mediaDevices.getUserMedia(constraints)
        .then(function(stream) {
            console.log('成功获取摄像头流:', stream);
            videoStream = stream;
            
            // 创建视频元素
            video = document.createElement('video');
            
            // 为旧浏览器提供兼容性支持
            if ('srcObject' in video) {
                video.srcObject = stream;
            } else {
                // 旧浏览器可能不支持srcObject
                video.src = window.URL.createObjectURL(stream);
            }
            
            video.autoplay = true;
            video.muted = true; // 静音，避免回声
            video.playsinline = true; // 允许内联播放，提高移动设备兼容性
            video.style.display = 'none'; // 隐藏视频元素，只用于捕获视频流
            video.style.width = '100%';
            video.style.height = '100%';
            video.style.objectFit = 'cover'; // 确保视频填充容器
            
            // 创建画布元素用于捕获帧
            const canvas = document.createElement('canvas');
            const ctx = canvas.getContext('2d');
            
            // 初始化结果画布
            const videoDisplay = document.getElementById('videoDisplay');
            resultCanvas = document.createElement('canvas');
            resultCanvas.id = 'resultCanvas';
            resultCanvas.classList.add('detection-result');
            videoDisplay.innerHTML = '';
            videoDisplay.appendChild(resultCanvas);
            
            // 视频元数据加载完成后，设置画布尺寸
            video.addEventListener('loadedmetadata', function() {
                console.log('视频元数据加载完成，尺寸:', video.videoWidth, 'x', video.videoHeight);
                
                // 设置画布尺寸与视频尺寸匹配
                canvas.width = video.videoWidth;
                canvas.height = video.videoHeight;
                resultCanvas.width = video.videoWidth;
                resultCanvas.height = video.videoHeight;
                
                // 确保画布具有正确的尺寸
                resultCanvas.style.width = '100%';
                resultCanvas.style.height = '100%';
                resultCanvas.style.objectFit = 'cover';
                
                console.log('画布尺寸已设置');
            });
            
            // 视频开始播放时的处理
            video.addEventListener('play', function() {
                console.log('视频开始播放');
            });
            
            // 视频数据加载完成后立即绘制一帧
            video.addEventListener('loadeddata', function() {
                console.log('视频数据加载完成，状态:', video.readyState);
                
                // 立即绘制第一帧
                const resultCtx = resultCanvas.getContext('2d');
                resultCtx.drawImage(video, 0, 0, resultCanvas.width, resultCanvas.height);
                console.log('初始视频帧绘制完成');
                
                // 开始捕获帧
                startCapture();
            });
            
            // 视频就绪可以播放时的处理
            video.addEventListener('canplay', function() {
                console.log('视频就绪，可以正常播放');
            });
            
            // 视频错误处理
            video.addEventListener('error', function(err) {
                console.error('视频元素错误:', err);
                console.error('视频错误详细信息:', video.error);
            });
            
            // 捕获视频帧函数
            function captureFrame() {
                if (!isStreaming || !video || video.readyState < 2) {
                    return;
                }
                
                try {
                    // 直接将视频帧绘制到结果画布
                    const resultCtx = resultCanvas.getContext('2d');
                    resultCtx.drawImage(video, 0, 0, resultCanvas.width, resultCanvas.height);
                    
                    // 同时绘制到隐藏画布用于处理
                    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
                    
                    // 将画布内容转换为Base64
                    const base64Image = canvas.toDataURL('image/jpeg', 0.7);
                    
                    // 如果当前没有在处理帧，就处理这个帧
                    if (!isProcessingFrame) {
                        // 只保留最新的帧
                        frameQueue = [base64Image];
                        processNextFrame();
                    } else {
                        // 如果已经在处理，就添加到队列末尾
                        frameQueue.push(base64Image);
                    }
                } catch (error) {
                    console.error('捕获视频帧失败:', error);
                }
            }
            
            // 处理帧队列中的下一个帧
            async function processNextFrame() {
                if (!isStreaming || frameQueue.length === 0 || isProcessingFrame) {
                    return;
                }
                
                isProcessingFrame = true;
                
                try {
                    // 获取队列中的第一个帧（最新的）
                    const base64Image = frameQueue.pop();
                    
                    // 将Base64图像发送到后端进行检测
                    // 注意：使用正确的接口地址和参数格式
                    const response = await fetch('/process-video-frame', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/x-www-form-urlencoded',
                        },
                        body: `frame=${encodeURIComponent(base64Image)}`,
                    });
                    
                    if (!response.ok) {
                        throw new Error('检测请求失败，状态码: ' + response.status);
                    }
                    
                    // 后端返回的是直接的Base64字符串，不是JSON格式
                    const base64Result = await response.text();
                    
                    // 将检测结果绘制到结果画布上
                    if (base64Result && base64Result !== 'error') {
                        // 创建一个新的图像对象
                        const img = new Image();
                        
                        // 当图像加载完成后，将其绘制到结果画布上
                        img.onload = function() {
                            const resultCtx = resultCanvas.getContext('2d');
                            resultCtx.drawImage(img, 0, 0, resultCanvas.width, resultCanvas.height);
                        };
                        
                        // 设置图像源为后端返回的Base64图像
                        img.src = 'data:image/jpeg;base64,' + base64Result;
                    } else if (base64Result === 'error') {
                        console.error('检测失败，后端返回错误');
                    }
                } catch (error) {
                    console.error('处理帧失败:', error);
                } finally {
                    isProcessingFrame = false;
                    // 处理队列中的下一个帧
                    if (frameQueue.length > 0) {
                        processNextFrame();
                    }
                }
            }
            
            // 开始捕获帧
            function startCapture() {
                if (!isStreaming) return;
                
                // 清除之前的捕获间隔
                if (captureInterval) {
                    clearInterval(captureInterval);
                }
                
                // 使用setInterval控制捕获频率
                captureInterval = setInterval(captureFrame, 150); // 150ms间隔，约6-7帧/秒

                console.log('开始捕获视频帧，间隔:', 150, 'ms');
            }
            
            // 直接调用play确保视频开始播放
            video.play().then(() => {
                console.log('视频播放已启动');
            }).catch(error => {
                console.error('视频播放失败:', error);
            });
            

        })
        .catch(function(error) {
            console.error('访问摄像头失败:', error);
            
            // 详细的错误处理
            let errorMsg = '无法访问摄像头，请确保已授权并检查设备连接';
            
            switch(error.name) {
                case 'NotAllowedError':
                    errorMsg = '摄像头访问被拒绝，请在浏览器设置中允许摄像头访问';
                    break;
                case 'NotFoundError':
                    errorMsg = '未找到摄像头设备，请检查摄像头是否已连接';
                    break;
                case 'NotReadableError':
                    errorMsg = '摄像头被其他程序占用，请关闭其他使用摄像头的应用';
                    break;
                case 'OverconstrainedError':
                    errorMsg = '无法满足摄像头的参数要求，请检查摄像头设置';
                    break;
                case 'SecurityError':
                    errorMsg = '访问摄像头时出现安全错误，请检查浏览器设置';
                    break;
            }
            
            alert(errorMsg);
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