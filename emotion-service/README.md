# Emotion Service (AffectNet)

独立的 Python 表情识别服务，用于 AI 面试页面实时识别候选人情绪。

## 1. 安装依赖

```bash
cd emotion-service
pip install -r requirements.txt
```

## 2. 启动服务

```bash
uvicorn app:app --host 0.0.0.0 --port 8091
```

## 3. 环境变量（可选）

- `EMOTION_FACE_MODEL_PATH`：人脸检测模型路径
- `EMOTION_AFFECTNET_MODEL_PATH`：AffectNet 分类模型路径

默认路径会自动指向仓库内：

- `Facial-Expression-Recognition/Facial-Expression-Recognition/yolov11n-face.pt`
- `Facial-Expression-Recognition/Facial-Expression-Recognition/runs/classify/affectnet_optimized/weights/best.pt`

## 4. 接口

- `GET /health`：健康检查
- `POST /analyze/frame`：单帧识别

请求示例：

```json
{
  "sessionId": 123,
  "imageBase64": "data:image/jpeg;base64,...",
  "source": "interview",
  "capturedAt": 1710000000000
}
```

返回示例：

```json
{
  "dominantEmotion": "happy",
  "confidence": 0.91,
  "faces": [
    {
      "bbox": [12, 34, 210, 290],
      "emotion": "happy",
      "confidence": 0.91
    }
  ],
  "hasFace": true,
  "timestamp": 1710000001000,
  "capturedAt": 1710000000000
}
```
