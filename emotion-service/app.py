import base64
import json
import os
import time
from typing import Any, Dict, List, Optional, Tuple

import cv2
import numpy as np
from fastapi import Body, FastAPI, HTTPException, Request
from pydantic import BaseModel
from ultralytics import YOLO


EMOTION_LABELS = ["anger", "disgust", "happy", "neutral", "sad", "surprise"]

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(BASE_DIR, ".."))
ALGO_ROOT = os.path.join(PROJECT_ROOT, "Facial-Expression-Recognition", "Facial-Expression-Recognition")

FACE_MODEL_PATH = os.getenv(
    "EMOTION_FACE_MODEL_PATH",
    os.path.join(ALGO_ROOT, "yolov11n-face.pt"),
)
EMOTION_MODEL_PATH = os.getenv(
    "EMOTION_AFFECTNET_MODEL_PATH",
    os.path.join(ALGO_ROOT, "runs", "classify", "affectnet_optimized", "weights", "best.pt"),
)


class AnalyzeFrameRequest(BaseModel):
    sessionId: Optional[int] = None
    imageBase64: str
    source: Optional[str] = "interview"
    capturedAt: Optional[int] = None


class FaceEmotion(BaseModel):
    bbox: List[int]
    emotion: str
    confidence: float


class AnalyzeFrameResponse(BaseModel):
    dominantEmotion: Optional[str] = None
    confidence: Optional[float] = None
    faces: List[FaceEmotion]
    hasFace: bool
    timestamp: int
    capturedAt: Optional[int] = None


app = FastAPI(title="Emotion Service", version="1.0.0")

face_model: Optional[YOLO] = None
emotion_model: Optional[YOLO] = None


def _decode_image(data: str) -> np.ndarray:
    payload = data
    if "," in data and data.startswith("data:"):
        payload = data.split(",", 1)[1]

    try:
        image_bytes = base64.b64decode(payload)
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=400, detail=f"Invalid base64 image: {exc}") from exc

    np_arr = np.frombuffer(image_bytes, np.uint8)
    frame = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
    if frame is None:
        raise HTTPException(status_code=400, detail="Failed to decode image")
    return frame


def _expand_bbox(x1: int, y1: int, x2: int, y2: int, width: int, height: int, ratio: float = 0.2) -> Tuple[int, int, int, int]:
    dx = int((x2 - x1) * ratio)
    dy = int((y2 - y1) * ratio)
    return max(0, x1 - dx), max(0, y1 - dy), min(width, x2 + dx), min(height, y2 + dy)


def _analyze_frame(frame: np.ndarray) -> AnalyzeFrameResponse:
    assert face_model is not None
    assert emotion_model is not None

    detect_results = face_model(frame, conf=0.7, verbose=False)
    faces: List[FaceEmotion] = []
    h, w = frame.shape[:2]

    for result in detect_results:
        for box in result.boxes:
            x1, y1, x2, y2 = box.xyxy[0].cpu().numpy().astype(int)
            x1e, y1e, x2e, y2e = _expand_bbox(x1, y1, x2, y2, w, h)
            face_roi = frame[y1e:y2e, x1e:x2e]
            if face_roi.size == 0:
                continue

            gray = cv2.cvtColor(face_roi, cv2.COLOR_BGR2GRAY)
            gray_3ch = cv2.cvtColor(gray, cv2.COLOR_GRAY2BGR)
            emotion_results = emotion_model(gray_3ch, verbose=False)
            probs = emotion_results[0].probs.data.tolist()
            class_id = int(np.argmax(probs))
            confidence = float(probs[class_id])
            label = EMOTION_LABELS[class_id] if class_id < len(EMOTION_LABELS) else str(class_id)
            faces.append(
                FaceEmotion(
                    bbox=[int(x1e), int(y1e), int(x2e), int(y2e)],
                    emotion=label,
                    confidence=confidence,
                )
            )

    dominant_emotion = None
    dominant_confidence = None
    if faces:
        dominant = max(faces, key=lambda item: item.confidence)
        dominant_emotion = dominant.emotion
        dominant_confidence = dominant.confidence

    return AnalyzeFrameResponse(
        dominantEmotion=dominant_emotion,
        confidence=dominant_confidence,
        faces=faces,
        hasFace=len(faces) > 0,
        timestamp=int(time.time() * 1000),
        capturedAt=None,
    )


@app.on_event("startup")
def startup_event() -> None:
    global face_model, emotion_model
    if not os.path.exists(FACE_MODEL_PATH):
        raise RuntimeError(f"Face model not found: {FACE_MODEL_PATH}")
    if not os.path.exists(EMOTION_MODEL_PATH):
        raise RuntimeError(f"AffectNet model not found: {EMOTION_MODEL_PATH}")

    face_model = YOLO(FACE_MODEL_PATH)
    emotion_model = YOLO(EMOTION_MODEL_PATH)


@app.get("/health")
def health() -> Dict[str, Any]:
    return {
        "ok": True,
        "faceModelPath": FACE_MODEL_PATH,
        "emotionModelPath": EMOTION_MODEL_PATH,
    }


@app.post("/analyze/frame", response_model=AnalyzeFrameResponse)
async def analyze_frame(
    request: Request,
    payload: Optional[AnalyzeFrameRequest] = Body(default=None),
) -> AnalyzeFrameResponse:
    if payload is None:
        raw_body = await request.body()
        preview = raw_body[:200].decode("utf-8", errors="replace")
        print(
            "[EmotionService] empty parsed body, fallback to raw body parse, "
            f"content-type={request.headers.get('content-type')} "
            f"content-length={request.headers.get('content-length')} "
            f"body-bytes={len(raw_body)} preview={preview}"
        )
        if not raw_body:
            raise HTTPException(status_code=400, detail="Request body is empty")
        try:
            raw_json = json.loads(raw_body.decode("utf-8"))
            payload = AnalyzeFrameRequest(**raw_json)
        except Exception as exc:  # noqa: BLE001
            raise HTTPException(status_code=400, detail=f"Invalid JSON body: {exc}") from exc

    frame = _decode_image(payload.imageBase64)
    result = _analyze_frame(frame)
    result.capturedAt = payload.capturedAt
    return result
